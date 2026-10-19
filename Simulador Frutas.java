import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import java.util.function.UnaryOperator;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Simulador de clasificación óptica de frutos por convolución. Estructuras implementadas desde cero. */
public class SimuladorFrutas extends JFrame {
    static final int N = 128;
    static final double[][] DESENFOQUE = {{1 / 16.0, 2 / 16.0, 1 / 16.0}, {2 / 16.0, 4 / 16.0, 2 / 16.0}, {1 / 16.0, 2 / 16.0, 1 / 16.0}};
    static final double[][] REALCE = {{0, -1, 0}, {-1, 5, -1}, {0, -1, 0}};
    static final double[][] SOBEL_X = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}}, SOBEL_Y = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

    // Matrices (2D) y vectores (1D)
    int[][] original, actual;
    boolean[][] mascara;
    int[] histograma = new int[256];
    double[] brixLote = new double[10];
    int brixCont, umbral = 90, fila;
    // Estructuras de datos
    Pila<int[][]> deshacer = new Pila<>();
    ListaSimple<Integer> umbrales = new ListaSimple<>();
    ListaCircular<Perfil> perfiles = new ListaCircular<>();
    ListaDoble<Calib> calibraciones = new ListaDoble<>();
    Cola<int[]> linea;
    Timer timer;
    int[][] escaneo;
    Random rnd = new Random();
    // Interfaz
    PanelImg pOrig = new PanelImg("Original"), pProc = new PanelImg("Procesada");
    JLabel lblPerfil = new JLabel(), lblCalib = new JLabel(), lblUmbral = new JLabel(), lblRes = new JLabel("<html>Sin clasificar</html>");
    JTextArea log = new JTextArea(5, 30), txtUmb = new JTextArea(2, 16);
    JSpinner spDef = new JSpinner(new SpinnerNumberModel(3, 0, 15, 1));
    JSlider slUmbral = new JSlider(0, 255, 90);
    JPanel pHist = new JPanel() {
        { setPreferredSize(new Dimension(400, 150)); setBorder(BorderFactory.createTitledBorder("Histograma de tonalidades (fruto)")); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Insets in = getInsets();
            int w = getWidth() - in.left - in.right, h = getHeight() - in.top - in.bottom, max = 1;
            for (int v : histograma) max = Math.max(max, v);
            g.setColor(new Color(60, 120, 200));
            for (int i = 0; i < 256; i++) {
                int x0 = in.left + i * w / 256, x1 = in.left + (i + 1) * w / 256, a = (int) ((double) histograma[i] / max * (h - 4));
                g.fillRect(x0, in.top + h - a, Math.max(1, x1 - x0), a);
            }
            g.setColor(Color.RED);
            int xu = in.left + umbral * w / 256;
            g.drawLine(xu, in.top, xu, in.top + h);
        }
    };

    SimuladorFrutas() {
        super("Simulador de Clasificación Óptica de Frutos – Convolución");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        perfiles.agregar(new Perfil("Manzana", 150, 11, 15, 0.03));
        perfiles.agregar(new Perfil("Mango", 170, 14, 20, 0.05));
        perfiles.agregar(new Perfil("Naranja", 160, 9, 13, 0.04));
        perfiles.agregar(new Perfil("Banano", 185, 18, 22, 0.06));
        calibraciones.agregar(new Calib("Cámara A estándar", 1.0, 0));
        calibraciones.agregar(new Calib("Cámara B baja luz", 1.2, 15));
        calibraciones.agregar(new Calib("Cámara C sobreexpuesta", 0.85, -10));
        umbrales.agregar(umbral);
        construirUI();
        generarFruta();
        pack();
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- Interfaz
    JPanel seccion(JPanel padre, String titulo) {
        JPanel p = new JPanel(new GridLayout(0, 1, 3, 3));
        p.setBorder(BorderFactory.createTitledBorder(titulo));
        padre.add(p);
        return p;
    }

    void btn(JPanel p, String txt, Runnable r) {
        JButton b = new JButton(txt);
        b.addActionListener(e -> r.run());
        p.add(b);
    }

    void construirUI() {
        JPanel izq = new JPanel();
        izq.setLayout(new BoxLayout(izq, BoxLayout.Y_AXIS));
        izq.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel pf = seccion(izq, "Fruta (lista circular)");
        pf.add(lblPerfil);
        btn(pf, "Perfil siguiente →", () -> { perfiles.siguiente(); etiquetas(); });
        JPanel fd = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fd.add(new JLabel("Defectos:"));
        fd.add(spDef);
        pf.add(fd);
        btn(pf, "Generar fruta", this::generarFruta);
        btn(pf, "Cargar imagen...", this::cargarImagen);

        JPanel pfi = seccion(izq, "Filtros (pila de deshacer)");
        btn(pfi, "Desenfoque gaussiano", () -> filtro("Desenfoque", m -> convolucion(m, DESENFOQUE)));
        btn(pfi, "Realce (sharpen)", () -> filtro("Realce", m -> convolucion(m, REALCE)));
        btn(pfi, "Bordes Sobel", () -> filtro("Sobel", SimuladorFrutas::sobel));
        btn(pfi, "Deshacer", this::deshacerFiltro);
        btn(pfi, "Restaurar original", () -> filtro("Restaurar original", m -> copiar(original)));

        JPanel pu = seccion(izq, "Umbral de defecto (lista simple)");
        pu.add(lblUmbral);
        slUmbral.addChangeListener(e -> lblUmbral.setText("Umbral: " + slUmbral.getValue()));
        pu.add(slUmbral);
        btn(pu, "Fijar umbral", () -> {
            umbral = slUmbral.getValue();
            umbrales.agregar(umbral);
            log("Umbral fijado en " + umbral);
            refrescar();
        });
        txtUmb.setEditable(false);
        txtUmb.setLineWrap(true);
        pu.add(new JScrollPane(txtUmb));

        JPanel pc = seccion(izq, "Calibración de cámara (lista doble)");
        pc.add(lblCalib);
        JPanel nav = new JPanel(new GridLayout(0, 2, 4, 4));
        btn(nav, "← Anterior", () -> { calibraciones.anterior(); etiquetas(); });
        btn(nav, "Siguiente →", () -> { calibraciones.siguiente(); etiquetas(); });
        btn(nav, "Aplicar", () -> { Calib c = calibraciones.actual(); filtro("Calibración " + c.nombre, m -> calibrar(m, c)); });
        btn(nav, "Nueva...", this::nuevaCalib);
        btn(nav, "Eliminar", () -> { calibraciones.eliminarActual(); etiquetas(); });
        pc.add(nav);

        JPanel pl = seccion(izq, "Línea de visión (cola)");
        btn(pl, "Escanear en línea", this::escanear);
        btn(pl, "Clasificar", () -> { if (!escaneando()) clasificar(); });
        pl.add(lblRes);

        JPanel centro = new JPanel(new GridLayout(1, 2, 8, 8));
        centro.add(pOrig);
        centro.add(pProc);
        log.setEditable(false);
        JScrollPane sp = new JScrollPane(log);
        sp.setBorder(BorderFactory.createTitledBorder("Registro"));
        JPanel sur = new JPanel(new GridLayout(1, 2, 8, 8));
        sur.add(pHist);
        sur.add(sp);
        sur.setPreferredSize(new Dimension(800, 160));

        setLayout(new BorderLayout(6, 6));
        add(new JScrollPane(izq, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.WEST);
        add(centro, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------- Lógica
    boolean escaneando() { return timer != null && timer.isRunning(); }

    void generarFruta() {
        if (escaneando()) return;
        Perfil p = perfiles.actual();
        int def = (Integer) spDef.getValue();
        double c = N / 2.0, rad = N * 0.38;
        double[][] d = new double[def][3];
        for (double[] x : d) {
            double ang = rnd.nextDouble() * 2 * Math.PI, dist = rnd.nextDouble() * rad * 0.7;
            x[0] = c + Math.cos(ang) * dist;
            x[1] = c + Math.sin(ang) * dist;
            x[2] = 4 + rnd.nextInt(6);
        }
        int[][] m = new int[N][N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double dist = Math.hypot(x - c, y - c);
                if (dist <= rad) {
                    double v = p.tono * (0.75 + 0.25 * (1 - dist / rad));
                    for (double[] k : d) if (Math.hypot(x - k[0], y - k[1]) <= k[2]) v = p.tono * 0.4;
                    m[y][x] = clamp(v + rnd.nextGaussian() * 4);
                } else m[y][x] = clamp(235 + rnd.nextGaussian() * 3);
            }
        }
        iniciar(m, "Fruta generada: " + p.nombre);
    }

    void cargarImagen() {
        JFileChooser fc = new JFileChooser();
        if (escaneando() || fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage e = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = e.createGraphics();
            g.drawImage(ImageIO.read(fc.getSelectedFile()), 0, 0, N, N, null);
            g.dispose();
            int[][] m = new int[N][N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    int c = e.getRGB(x, y);
                    m[y][x] = clamp(0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255));
                }
            iniciar(m, "Imagen cargada: " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar la imagen");
        }
    }

    /** Fija la imagen base, calcula la máscara del fruto (distinto del fondo) y reinicia la pila. */
    void iniciar(int[][] m, String msg) {
        original = m;
        actual = copiar(m);
        double fondo = 0;
        for (int i = 0; i < N; i++) fondo += m[0][i] + m[N - 1][i] + m[i][0] + m[i][N - 1];
        fondo /= 4 * N;
        mascara = new boolean[N][N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) mascara[y][x] = Math.abs(m[y][x] - fondo) > 25;
        deshacer = new Pila<>();
        log(msg);
        refrescar();
    }

    void filtro(String nombre, UnaryOperator<int[][]> f) {
        if (escaneando()) return;
        deshacer.push(copiar(actual));
        actual = f.apply(actual);
        log("Filtro: " + nombre + " (pila: " + deshacer.tamano() + ")");
        refrescar();
    }

    void deshacerFiltro() {
        if (escaneando()) return;
        if (deshacer.vacia()) { log("Nada que deshacer"); return; }
        actual = deshacer.pop();
        log("Deshacer (pila: " + deshacer.tamano() + ")");
        refrescar();
    }

    void nuevaCalib() {
        String s = JOptionPane.showInputDialog(this, "Formato: nombre,ganancia,desplazamiento", "Cámara D,1.1,5");
        if (s == null) return;
        try {
            String[] p = s.split(",");
            calibraciones.agregar(new Calib(p[0].trim(), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim())));
            calibraciones.irAlFinal();
            etiquetas();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Formato inválido");
        }
    }

    /** Simula la banda: encola las filas y las procesa una por una. */
    void escanear() {
        if (escaneando()) return;
        linea = new Cola<>();
        for (int[] f : actual) linea.encolar(f.clone());
        escaneo = new int[N][N];
        for (int[] f : escaneo) Arrays.fill(f, 30);
        Arrays.fill(histograma, 0);
        fila = 0;
        log("Escaneando " + linea.tamano() + " filas...");
        timer = new Timer(15, e -> {
            if (linea.vacia()) { timer.stop(); log("Escaneo terminado"); clasificar(); return; }
            int[] f = linea.desencolar();
            escaneo[fila] = f;
            for (int x = 0; x < N; x++) if (mascara[fila][x]) histograma[f[x]]++;
            fila++;
            pProc.set(aImagen(escaneo));
            pHist.repaint();
        });
        timer.start();
    }

    void clasificar() {
        Perfil p = perfiles.actual();
        int total = 0, def = 0, sano = 0;
        double suma = 0;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (!mascara[y][x]) continue;
                total++;
                if (actual[y][x] < umbral) def++;
                else { sano++; suma += actual[y][x]; }
            }
        double ratio = total == 0 ? 0 : (double) def / total, media = sano == 0 ? 0 : suma / sano;
        double t = Math.max(0, Math.min(1, (media - 0.6 * p.tono) / (0.4 * p.tono)));
        double brix = Math.max(0, p.bMin + (p.bMax - p.bMin) * t - 5 * ratio); // modelo de Brix simulado
        String cat = ratio > 2 * p.tol ? "DESCARTE (defectos)" : brix < p.bMin ? "SEGUNDA (bajo Brix)" : ratio <= p.tol ? "EXTRA" : "PRIMERA";
        brixLote[brixCont++ % brixLote.length] = brix;
        int n = Math.min(brixCont, brixLote.length);
        double prom = 0;
        for (int i = 0; i < n; i++) prom += brixLote[i];
        lblRes.setText(String.format("<html>Defectos: %.1f%%<br>Brix: %.1f (lote: %.1f)<br><b>%s</b></html>", ratio * 100, brix, prom / n, cat));
        log(String.format("Clasificación %s: defectos %.1f%%, Brix %.1f -> %s", p.nombre, ratio * 100, brix, cat));
        if (ratio > 0.9) log("Aviso: casi todo es 'defecto'. Si aplicaste Sobel, usa Deshacer antes de clasificar.");
    }

    void refrescar() {
        pOrig.set(aImagen(original));
        pProc.set(aImagen(actual));
        Arrays.fill(histograma, 0);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) if (mascara[y][x]) histograma[actual[y][x]]++;
        pHist.repaint();
        etiquetas();
    }

    void etiquetas() {
        lblPerfil.setText("Perfil: " + perfiles.actual());
        lblCalib.setText(String.format("(%d/%d) %s", calibraciones.indice() + 1, calibraciones.tamano(), calibraciones.actual()));
        lblUmbral.setText("Umbral: " + slUmbral.getValue());
        txtUmb.setText(umbrales.toString());
    }

    BufferedImage aImagen(int[][] m) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) img.setRGB(x, y, (m[y][x] << 16) | (m[y][x] << 8) | m[y][x]);
        return img;
    }

    void log(String s) {
        log.append(s + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ---------------------------------------------------------------- Procesamiento de imagen
    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static int[][] copiar(int[][] m) {
        int[][] c = new int[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    /** Suma ponderada del vecindario 3x3 de (f, c); los bordes se replican. */
    static double suma(int[][] m, int f, int c, double[][] k) {
        double s = 0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                s += m[Math.max(0, Math.min(N - 1, f + i - 1))][Math.max(0, Math.min(N - 1, c + j - 1))] * k[i][j];
        return s;
    }

    static int[][] convolucion(int[][] m, double[][] k) {
        int[][] o = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) o[y][x] = clamp(suma(m, y, x, k));
        return o;
    }

    static int[][] sobel(int[][] m) {
        int[][] o = new int[N][N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double gx = suma(m, y, x, SOBEL_X), gy = suma(m, y, x, SOBEL_Y);
                o[y][x] = clamp(Math.sqrt(gx * gx + gy * gy));
            }
        return o;
    }

    static int[][] calibrar(int[][] m, Calib c) {
        int[][] o = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) o[y][x] = clamp(c.g * m[y][x] + c.d);
        return o;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new SimuladorFrutas().setVisible(true));
    }
}

/* ------------------------------------------------------------ Modelo y paneles */
class Perfil {
    String nombre; int tono; double bMin, bMax, tol;
    Perfil(String n, int t, double a, double b, double tol) { nombre = n; tono = t; bMin = a; bMax = b; this.tol = tol; }
    public String toString() { return String.format("%s (Brix %.0f-%.0f, tol. %.0f%%)", nombre, bMin, bMax, tol * 100); }
}

class Calib {
    String nombre; double g, d;
    Calib(String n, double g, double d) { nombre = n; this.g = g; this.d = d; }
    public String toString() { return String.format("%s (x%.2f, %+.0f)", nombre, g, d); }
}

class PanelImg extends JPanel {
    BufferedImage img;
    PanelImg(String t) { setPreferredSize(new Dimension(320, 320)); setBorder(BorderFactory.createTitledBorder(t)); }
    void set(BufferedImage i) { img = i; repaint(); }
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img == null) return;
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right, h = getHeight() - in.top - in.bottom, s = Math.min(w, h);
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, in.left + (w - s) / 2, in.top + (h - s) / 2, s, s, null);
    }
}

/* ------------------------------------------------------------ Estructuras de datos (desde cero) */
/** Lista simple: historial de umbrales. */
class ListaSimple<T> {
    private class Nodo { T d; Nodo sig; Nodo(T d) { this.d = d; } }
    private Nodo cab, fin;
    void agregar(T d) { Nodo n = new Nodo(d); if (cab == null) cab = fin = n; else { fin.sig = n; fin = n; } }
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (Nodo a = cab; a != null; a = a.sig) s.append(a.d).append(a.sig != null ? " -> " : "");
        return s.toString();
    }
}

/** Lista circular: perfiles de frutas (el último nodo apunta al primero). */
class ListaCircular<T> {
    private class Nodo { T d; Nodo sig; Nodo(T d) { this.d = d; } }
    private Nodo ult, act;
    void agregar(T d) {
        Nodo n = new Nodo(d);
        if (ult == null) { n.sig = n; act = n; } else { n.sig = ult.sig; ult.sig = n; }
        ult = n;
    }
    T actual() { return act.d; }
    T siguiente() { act = act.sig; return act.d; }
}

/** Lista doble: calibración de cámaras, con cursor en ambos sentidos. */
class ListaDoble<T> {
    private class Nodo { T d; Nodo ant, sig; Nodo(T d) { this.d = d; } }
    private Nodo cab, fin, act;
    private int tam;
    void agregar(T d) {
        Nodo n = new Nodo(d);
        if (cab == null) cab = fin = act = n; else { fin.sig = n; n.ant = fin; fin = n; }
        tam++;
    }
    T actual() { return act.d; }
    void siguiente() { if (act.sig != null) act = act.sig; }
    void anterior() { if (act.ant != null) act = act.ant; }
    void irAlFinal() { act = fin; }
    void eliminarActual() {
        if (tam <= 1) return;
        Nodo a = act.ant, s = act.sig;
        if (a != null) a.sig = s; else cab = s;
        if (s != null) s.ant = a; else fin = a;
        act = s != null ? s : a;
        tam--;
    }
    int indice() { int i = 0; for (Nodo a = cab; a != act; a = a.sig) i++; return i; }
    int tamano() { return tam; }
}

/** Pila: deshacer de filtros. */
class Pila<T> {
    private class Nodo { T d; Nodo abajo; Nodo(T d, Nodo a) { this.d = d; abajo = a; } }
    private Nodo tope;
    private int tam;
    void push(T d) { tope = new Nodo(d, tope); tam++; }
    T pop() { T d = tope.d; tope = tope.abajo; tam--; return d; }
    boolean vacia() { return tope == null; }
    int tamano() { return tam; }
}

/** Cola: filas de píxeles en la línea de visión. */
class Cola<T> {
    private class Nodo { T d; Nodo sig; Nodo(T d) { this.d = d; } }
    private Nodo frente, fin;
    private int tam;
    void encolar(T d) { Nodo n = new Nodo(d); if (fin == null) frente = fin = n; else { fin.sig = n; fin = n; } tam++; }
    T desencolar() { T d = frente.d; frente = frente.sig; if (frente == null) fin = null; tam--; return d; }
    boolean vacia() { return frente == null; }
    int tamano() { return tam; }
}
