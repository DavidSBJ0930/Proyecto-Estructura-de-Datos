# Proyecto-Estructura-de-Datos
# 🍏 Simulador de Clasificación Óptica de Frutos por Filtros Matriciales (Convolución)

<p align="center">
  <b>Sistema de visión artificial y procesamiento digital de imágenes en Java para el control de calidad automatizado en líneas de producción agrícola.</b>
</p>

---

## 🚀 ¿De qué trata el proyecto?
Este proyecto consiste en un simulador orientado al sector agroindustrial que automatiza el análisis digital de imágenes de frutos en una banda transportadora. Mediante el uso de **filtros matriciales (convolución)**, el sistema es capaz de escanear los píxeles de los frutos en tiempo real para detectar defectos superficiales (manchas o imperfecciones en la cáscara), analizar distribuciones de color mediante histogramas y estimar parámetros de calidad como los grados Brix (°Bx). Todo el núcleo lógico y de procesamiento opera utilizando estructuras de datos implementadas completamente desde cero, sin depender de librerías externas de colecciones.

---

## 🛠️ Estructuras de Datos Implementadas
Para la gestión eficiente de los datos del proceso, se diseñaron e implementaron las siguientes estructuras desde cero:
*   **Matriz (2D):** Modela la imagen digital de píxeles del fruto y permite aplicar kernels de convolución (Desenfoque, Realce, Filtro Sobel) para la detección de contornos y defectos.
*   **Vector (1D):** Almacena el histograma de tonalidades de la fruta y las lecturas secuenciales de contenido de azúcar (°Brix).
*   **Lista Simple:** Mantiene el registro histórico de los umbrales (*thresholds*) configurados durante las corridas de producción.
*   **Lista Circular:** Gestiona la rotación continua entre los diferentes perfiles de frutas (manzanas, mangos, naranjas, bananos) configurados en la banda transportadora.
*   **Lista Doblemente Enlazada:** Permite la navegación bidireccional entre los distintos perfiles de calibración de las cámaras ópticas.
*   **Pila (LIFO):** Implementa la función de *Undo* (Deshacer) para revertir de forma inmediata los filtros visuales aplicados sobre las imágenes.
*   **Cola (FIFO):** Simula el buffer de entrada para el escaneo de filas de píxeles en tiempo real por parte de la línea de visión artificial.

---

## 🎨 Características Principales
*   **Interfaz Gráfica (Java Swing):** Panel dual para la comparación visual directa entre la imagen original y la procesada.
*   **Generador y Cargador de Imágenes:** Permite generar frutos sintéticos con cantidad de defectos configurable o importar imágenes personalizadas.
*   **Histograma Interactivo:** Gráfica dinámica en tiempo real de la distribución de tonos y líneas de umbral de corte.
*   **Clasificación Automática:** Evaluación porcentual de defectos para categorizar el fruto (Extra, Primera, Segunda o Descarte).

---

## 💻 Requisitos y Ejecución
1. Tener instalado **Java Development Kit (JDK 8 o superior)**.
2. Compilar y ejecutar el archivo principal:
   ```bash
   javac SimuladorFrutas.java
   java SimuladorFrutas
