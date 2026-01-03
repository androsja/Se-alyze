# Arquitectura Detallada: Se-alyze (MVI + Clean Architecture)

Este documento especifica la arquitectura técnica profunda para el sistema de reconocimiento de Lengua de Señas Colombiana (LSC).

## 1. Concepto de Arquitectura "Ortogonal"
El sistema está diseñado para ser **agnóstico del idioma y del modelo**.
- **Ortogonalidad:** Significa que cambiar el idioma (de LSC a ASL) es tan simple como cambiar un archivo de configuración (`config.json`) y el archivo del modelo (`.tflite`). El código base (Java/Kotlin) **NO cambia**.
- **Configuración Abierta:**
    - `assets/models/lsc_v1.tflite`: El cerebro entrenado.
    - `assets/config/lsc_taxonomy.json`: Mapeo de índices a palabras (ej: `0: "Hola"`, `1: "Gracias"`).

---

## 2. Pipeline de Datos: De Cámara a Traducción

Este es el viaje exacto de la información a través de las capas.

### Paso 1: Captura (Data Layer - Hardware)
*   **Fuente:** `CameraX` (Librería de Android).
*   **Formato:** `ImageProxy` (Buffer YUV_420_888).
*   **Frecuencia:** 30 FPS (Frames por segundo).
*   **Acción:** Se convierte el `ImageProxy` a un objeto `Bitmap` o `MPImage` (MediaPipe Image) optimizado.

### Paso 2: Extracción de Características (Data Layer - Vision)
*   **Motor:** `MediaPipeHands` / `MediaPipeHolistic`.
*   **Entrada:** `MPImage`.
*   **Proceso:** La red neuronal de MediaPipe escanea la imagen buscando manos y postura.
*   **Salida (Mensaje):** `LandmarkResult`.
    *   Contiene 21 puntos (x, y, z) por mano.
    *   Contiene 33 puntos de pose corporal.
    *   *Nota:* Si no se detectan manos, el flujo se detiene aquí para ahorrar batería.

### Paso 3: Normalización y Secuenciación (Domain Layer - Logic)
Aquí ocurre la "magia" matemática para hacer el sistema robusto.
*   **Normalización:** Las coordenadas crudas dependen de qué tan lejos esté la persona de la cámara.
    *   *Algoritmo:* Restamos la coordenada del "Punto Central" (muñeca o nariz) a todos los demás puntos. Así, el movimiento es relativo al cuerpo, no a la pantalla.
*   **Buffer Temporal (Ventana Deslizante):** Una sola foto no dice nada (una foto de "Hola" se parece a una de "Adiós"). Necesitamos el movimiento.
    *   Se acumulan los últimos **30 frames** en una lista: `List<FrameLandmarks>`.

### Paso 4: Inferencia / Traducción (Data Layer - ML)
*   **Motor:** `TensorFlow Lite Interpreter`.
*   **Entrada:** Tensor de forma `[1, 30, 126]` (1 secuencia, 30 frames, 126 coordenadas x/y de ambas manos y pose).
*   **Proceso:** El modelo LSTM procesa la secuencia temporal.
*   **Salida:** `float[] probabilities`. Un array con porcentajes para cada palabra posible.
    *   Ej: `[0.02, 0.95, 0.03]` (95% probabilidad de ser la palabra en índice 1).

### Paso 5: Decodificación y UI (Presentation Layer)
*   **Umbral:** Si la probabilidad > 85%, se considera válida.
*   **Anti-Rebote (Debounce):** Si el modelo dice "Hola" 5 veces seguidas en 0.2 segundos, solo mostramos una vez "Hola".
*   **Estado UI:** Se actualiza `TranslationUiState` con la nueva palabra.

---

## 3. Detalle de Capas y Mensajes (Código)

Diseño de clases siguiendo Clean Architecture.

### A. Capa de Dominio (Domain)
*Pura lógica. Sin Android ni librerías UI.*

**Modelos:**
```kotlin
data class SignFrame(val leftHand: Hand?, val rightHand: Hand?, val pose: Pose?)
data class TranslationResult(val word: String, val confidence: Float, val iconUrl: String?)
```

**Casos de Uso (Interactors):**
1.  `AnalyzeFrameUseCase`: Recibe imagen -> Devuelve Landmarks.
2.  `RecognizeSignUseCase`: Recibe lista de Landmarks -> Devuelve `TranslationResult`.

### B. Capa de Datos (Data)
*Implementación técnica.*

**Repositorios:**
```kotlin
class SignRepositoryImpl(
    private val mediaPipeDataSource: MediaPipeDataSource,
    private val tfliteDataSource: TfliteDataSource
) : SignRepository {
    // Orquesta la llamada a MediaPipe y luego a TFLite
}
```

### C. Capa de Presentación (UI)
*Jetpack Compose + MVI.*

**ViewModel (`CameraViewModel`):**
Mantiene el **Estado Único de Verdad**:
```kotlin
data class CameraUiState(
    val isCameraPermissionGranted: Boolean = false,
    val currentSign: String? = null, // La traducción actual
    val lastPhrases: List<String> = emptyList(), // Historial "Hola", "Amigo"
    val debugOverlay: List<Point> = emptyList() // Puntos para dibujar el esqueleto
)
```

**Interfaz de Usuario (Pantallas):**
1.  **`CameraOverlay`**: Dibuja los puntos sobre el video (Canvas). Verde si la confianza es alta, rojo si es baja.
2.  **`TranslationBox`**: Caja flotante inferior con texto grande y alto contraste. Muestra la palabra actual.
3.  **`PhraseBar`**: Barra superior acumulando las palabras: "YO" -> "QUERER" -> "AGUA".

---

## 4. Lengua de Señas Colombiana (LSC) - Especificaciones

Para este prototipo, nos centraremos en **LSC**.

### Diferencias Clave a considerar:
1.  **Dactilología (Deletreo):** LSC usa mucho el deletreo manual.
2.  **Expresión Facial:** Es gramatical en LSC (ej: levantar cejas para preguntas).
    *   *Implementación:* Nuestro modelo incluirá puntos de la cara (cejas y boca) en los datos de entrada para captar estas sutilezas.

### Vocabulario Inicial (MVP):
El modelo será entrenado inicialmente para detectar estas clases (Clasificador de 10 clases):
1.  Hola
2.  Nombre
3.  Sordo
4.  Oyente
5.  Ayuda
6.  Gracias
7.  Baño
8.  Comer
9.  Si
10. No
+ Clase "Ruido" (cuando no se está haciendo ninguna seña).

## 5. Escalabilidad Futura (Open Design)
Para "dejarlo abierto a nuevas señas":
1.  **Entrenamiento en la Nube:** Crearemos un script en Python que permita al admin subir una carpeta con nuevos videos de una nueva seña.
2.  **Re-entrenamiento Automático:** El script generará un nuevo `.tflite`.
3.  **Actualización OTA:** La app consultará al iniciar si hay una versión `v2.tflite` y la descargará.
