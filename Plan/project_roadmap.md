# Hoja de Ruta del Proyecto: Se-alyze (Reconocimiento de Lenguaje de Señas)

Este documento detalla el plan de desarrollo paso a paso para construir el prototipo funcional en **Android**.

## Fase 1: Fundamentos y Datos (Semanas 1-2)
**Objetivo:** Definir el alcance y preparar los datos necesarios para el entrenamiento.

### 1.1 Selección del Lenguaje
- [ ] Decidir el lenguaje objetivo (LSE, LSM, ASL, etc.).
- [ ] Definir el vocabulario inicial (MVP): Empezar con 10-20 señas básicas (ej. "Hola", "Gracias", "Ayuda", "Casa").

### 1.2 Adquisición de Datos
- [ ] **Investigación:** Buscar datasets existentes (WLASL, LSE_Lex, AUTSL).
- [ ] **Recolección Propia (Si es necesario):** Grabar videos propios para completar el vocabulario.
- [ ] **Limpieza:** Filtrar videos de baja calidad o encuadres incorrectos.

## Fase 2: Motor de Inteligencia Artificial (Semanas 3-5)
**Objetivo:** Crear y entrenar el modelo capaz de clasificar gestos a partir de coordenadas.

### 2.1 Pipeline de Procesamiento (Python)
- [ ] Implementar script con **MediaPipe Holistic/Hands** para extraer landmarks (puntos clave) de los videos.
- [ ] Normalizar coordenadas (hacerlas relativas al centro del cuerpo/mano para que la distancia a la cámara no afecte).
- [ ] Generar dataset de secuencias: Archivos `.npy` o `.csv` conteniendo las trayectorias de los puntos.

### 2.2 Entrenamiento del Modelo
- [ ] Diseñar arquitectura LSTM (Long Short-Term Memory) o GRU en TensorFlow/Keras.
- [ ] Entrenar el modelo con los datos procesados.
- [ ] Evaluar precisión (Accuracy) y Matriz de Confusión.

### 2.3 Conversión y Optimización
- [ ] Convertir el modelo entrenado a formato **TensorFlow Lite (.tflite)**.
- [ ] Aplicar cuantización (Quantization) si es necesario para reducir tamaño y latencia.

## Fase 3: Desarrollo Android (Semanas 6-9)
**Objetivo:** Integrar el modelo en una aplicación móvil nativa o Flutter enfocada en Android.

### 3.1 Estructura del Proyecto
- [ ] Inicializar proyecto (Android Nativo Kotlin o Flutter).
- [ ] Configurar permisos (Cámara, Micrófono/Audio).

### 3.2 Visión por Computadora en el Móvil
- [ ] Integrar **CameraX** (Android) o plugin de cámara.
- [ ] Integrar **MediaPipe Solutions** para recibir el stream de video y extraer landmarks en tiempo real en el dispositivo.
- [ ] Visualizar el "esqueleto" sobre el video (debug mode) para asegurar que el tracking funciona.

### 3.3 El Cerebro (Inferencia)
- [ ] Implementar el **Intérprete de TFLite**.
- [ ] Crear un buffer (ventana deslizante) que acumule los últimos 30 frames de coordenadas.
- [ ] Pasar el buffer al modelo y obtener la predicción.

### 3.4 Interfaz y Salida (UI/UX)
- [ ] Mostrar la palabra predicha en pantalla con alta legibilidad.
- [ ] Implementar **Text-to-Speech (TTS)** nativo de Android para vocalizar la palabra.
- [ ] Añadir botón de "Borrar" o "Frase Nueva".

## Fase 4: Pruebas y Refinamiento (Semanas 10+)
**Objetivo:** Pulir la experiencia de usuario y el rendimiento.

### 4.1 Optimización
- [ ] Medir FPS (Frames por Segundo). Asegurar al menos 20-30 FPS.
- [ ] Gestionar el calentamiento del dispositivo y uso de batería.

### 4.2 Pruebas de Campo
- [ ] Probar con diferentes condiciones de luz.
- [ ] Probar con diferentes fondos y ropa.
- [ ] Validar con usuarios reales o conocedores del lenguaje de señas.
