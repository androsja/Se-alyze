# Documentación de Funcionalidades: Se-alyze

Este documento describe las funcionalidades clave que tendrá la aplicación en su versión MVP y futuras iteraciones.

## 1. Funcionalidades Principales (Core)

### 1.1 Traducción en Tiempo Real
*   **Descripción:** La función principal. La cámara captura los gestos del usuario y los traduce a texto y audio instantáneamente.
*   **Detalles:**
    *   La traducción opera "seña por seña".
    *   Umbral de confianza: Solo mostrar palabras si el modelo tiene >80% de certeza.

### 1.2 Text-to-Speech (Voz)
*   **Descripción:** Sintetizar la palabra traducida en audio.
*   **Detalles:**
    *   Uso del motor TTS nativo de Android.
    *   Opción para activar/desactivar el audio (botón de "Mute").

### 1.3 Modo Visualización (Debug/Esqueleto)
*   **Descripción:** Permite ver lo que la IA "ve".
*   **Detalles:**
    *   Superposición (overlay) de los puntos (landmarks) de las manos y cuerpo sobre la imagen de la cámara.
    *   Útil para que el usuario sepa si está bien encuadrado.

## 2. Gestión de Usuario y Configuración

### 2.1 Selección de Diccionario
*   **Descripción:** Permitir al usuario descargar o seleccionar diferentes paquetes de idioma (ej: Básico, Médico, Viajes).
*   **Detalles:**
    *   Inicialmente un solo paquete integrado.

### 2.2 Historial de Traducción
*   **Descripción:** Registro temporal de las últimas señas detectadas para formar frases simples.
*   **Detalles:**
    *   Barra inferior mostrando: "YO" + "IR" + "CASA".
    *   Botón para borrar historial.

## 3. Funcionalidades Técnicas (No visibles pero críticas)

### 3.1 Procesamiento Offline
*   **Descripción:** La app debe funcionar sin conexión a internet.
*   **Detalles:** El modelo TFLite y la librería MediaPipe están embebidos en la app.

### 3.2 Calibración de Luz (Automática)
*   **Descripción:** Aviso al usuario si la iluminación es insuficiente para una detección precisa.

## 4. Funcionalidades Futuras (Roadmap V2)

*   **Modo Conversación:** Traducción inversa (Voz a Texto para que la persona sorda lea).
*   **Traducción de Frases (NLP):** Usar un modelo de lenguaje para convertir "YO IR CASA" en "Voy a casa".
*   **Gamificación:** Módulos para aprender señas ("Haz la seña de 'Gato'").
