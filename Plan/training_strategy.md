# Estrategia de Entrenamiento y Análisis de Peso

Este documento responde a las dudas críticas sobre el peso del modelo y el plan de ejecución para entrenar la IA de LSC (Lengua de Señas Colombiana).

## 1. Análisis de Peso y Arquitectura (¿Server o App?)

**La Preocupación:** "¿El modelo pesará demasiado para el celular (`.tflite`)?"

**La Realidad:** NO. Será extremadamente ligero.

### ¿Por qué?
No estamos enviando **VIDEO** a la red neuronal. Estamos enviando **COORDENADAS**.
1.  **MediaPipe (Ya integrado):** Hace el trabajo pesado. Convierte la imagen "pesada" (Megapixeles) en un esqueleto de puntos (aprox. 100 números float por frame).
2.  **Nuestro Modelo LSC:** Solo analiza esos números. "Si el punto 8 (índice) sube y el punto 4 (pulgar) se acerca... es 'Hola'".

### Estimación de Peso
*   **Tipo de Modelo:** LSTM (Long Short-Term Memory) o Transformer ligero.
*   **Entrada:** 30 frames x 150 coordenadas.
*   **Peso Estimado:** **Entre 200 KB y 3 MB.**
*   **Comparación:** Una sola foto de alta resolución pesa más que todo tu cerebro de IA.

### Veredicto: **ON-DEVICE (En el Celular)**
| Característica | En el Celular (Recomendado) | En Servidor (Cloud) |
| :--- | :--- | :--- |
| **Peso App** | +2 MB (Insignificante) | Ligero |
| **Latencia** | **Tiempo Real (Instantáneo)** | Lag de 1-2 segundos (Mata la fluidez) |
| **Internet** | **Funciona Offline** | Requiere 4G/5G constante |
| **Privacidad** | Total (Video no sale del tel) | Riesgosa (Video viaja a la nube) |
| **Costos** | **$0** | Costoso (GPU Servers) |

---

## 2. Plan de Entrenamiento (Paso a Paso)

Este es tu "Gimnasio" para la IA.

### Fase A: Recolección de Datos (La parte manual)
Necesitas enseñarle con ejemplos.
*   **Objetivo:** 20 Palabras Iniciales.
*   **Volumen:** 50 repeticiones por palabra. (1000 videos cortos total).
*   **Herramienta:** Usa la misma cámara del celular o webcam.
*   **Reglas de Grabación:**
    1.  **Variedad:** No te quedes quieto. Muévete un poco a la izquierda/derecha.
    2.  **Distancia:** Graba algunos cerca (primer plano) y otros más lejos (torso completo).
    3.  **Luz:** Graba de día y con luz artificial.
    4.  **Usuarios:** Si puedes, pide a 2 o 3 amigos que hagan las señas también (para que no solo reconozca TUS manos).

### Fase B: Pre-Procesamiento (Script Python 1)
No entrenamos con los videos `.mp4`, sino con los datos geométricos.
1.  Crearemos un script `extract_landmarks.py`.
2.  Lee cada video -> Pasa por MediaPipe -> Guarda un archivo `.npy` (numpy) o `.csv`.
3.  **Resultado:** Una carpeta de archivos de texto/números, muy liviana.

### Fase C: Entrenamiento (Script Python 2 - Google Colab)
1.  Usaremos **Google Colab** (Gratis y rápido).
2.  Cargamos los `.npy`.
3.  Definimos el modelo (Keras/TensorFlow):
    ```python
    model = Sequential()
    model.add(LSTM(64, return_sequences=True, activation='relu', input_shape=(30, 258)))
    model.add(LSTM(128, return_sequences=True, activation='relu'))
    model.add(LSTM(64, return_sequences=False, activation='relu'))
    model.add(Dense(64, activation='relu'))
    model.add(Dense(32, activation='relu'))
    model.add(Dense(actions.shape[0], activation='softmax')) // Salida: Probabilidades
    ```
4.  Ejecutamos `model.fit()`. (Tardará unos 15-30 minutos).

### Fase D: Exportación
1.  Convertir a TFLite:
    ```python
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    ```
2.  Descargar `lsc_model.tflite`.
3.  Copiar a la carpeta `app/src/main/assets/` de tu proyecto Android.

---

## 3. Integración con LLM (El "Rectificador") - Enfoque Directo SDK

El modelo `tflite` es rápido pero "tonto" gramaticalmente.
*   **TFLite dice:** "YO ... COMER ... MAÑANA".
*   **Solución (Direct SDK):**
    *   No montaremos un servidor propio intermedio.
    *   La App Android llamará **directamente** a la API de **Gemini** (usando Google AI Client SDK for Android) o OpenAI.
    *   **Flujo:** `App -> Gemini API -> App`.
    *   **Ventaja:** Implementación "Breve" y sencilla. Sin mantenimiento de backend.
    *   **Costo:** Pago por uso (tokens), pero muy barato para texto simple.

