# Requerimientos de Backend y Arquitectura

Aunque el procesamiento principal (Inferencia IA) ocurre en el dispositivo (**Edge Computing**) para garantizar velocidad y privacidad, el proyecto requiere servicios de backend para gestión, actualizaciones y mejora continua.

## 1. Arquitectura General
*   **Tipo:** Serverless (Recomendado para inicio) o Microservicios.
*   **Proveedor Sugerido:** Firebase (Google) es ideal por su integración nativa con Android y Flutter, además de su capa gratuita generosa.

## 2. Componentes del Backend

### 2.1 Distribución de Modelos (Model Serving)
*   **Problema:** Los modelos de IA mejoran con el tiempo. No queremos actualizar toda la app en la Play Store solo para actualizar el "cerebro".
*   **Solución:** Descarga dinámica de modelos `.tflite`.
*   **Tecnología:** **Firebase ML Model Downloader** o un bucket S3/Google Cloud Storage simple con control de versiones.

### 2.2 Autenticación y Perfiles (Opcional en MVP)
*   **Función:** Guardar preferencias del usuario, historial, o progreso si se añade un módulo de aprendizaje.
*   **Tecnología:** Firebase Authentication (Google, Email, Anónimo).

### 2.3 Analytics y Monitoreo
*   **Función:** Entender cómo se usa la app y dónde falla.
*   **Métricas Clave:**
    *   Tasa de detecciones exitosas vs. fallidas (si el usuario reporta error).
    *   Latencia promedio de inferencia en diferentes dispositivos.
    *   Tiempo de uso de la cámara.
*   **Tecnología:** Google Analytics for Firebase / Crashlytics.

### 2.4 Data Loop (Mejora Continua - Avanzado)
*   **Función:** Permitir a los usuarios reportar una traducción incorrecta y opcionalmente subir ese clip de video (anonymizado) para re-entrenar el modelo.
*   **Almacenamiento:** Cloud Storage (para los videos cortos).
*   **Base de Datos:** Firestore (NoSQL) para metadatos del reporte (Timestamp, Seña esperada vs. Seña detectada).

## 3. APIs Necesarias (Contrato Inicial)

No se requiere una API REST compleja para el funcionamiento core, pero sí para los servicios auxiliares:

1.  `GET /api/v1/models/latest`: Comprobar si hay una nueva versión del modelo IA.
2.  `POST /api/v1/feedback`: Enviar reporte de error (JSON + Video opcional).
3.  `GET /api/v1/dictionary`: Obtener la lista de palabras soportadas y sus metadatos (ej: URLs de imágenes de referencia).

## 4. Resumen de Stack Recomendado para Backend
*   **Base de Datos:** Firebase Firestore.
*   **Almacenamiento Archivos:** Firebase Storage.
*   **Auth:** Firebase Auth.
*   **Functions:** Cloud Functions (para lógica de servidor ligera, ej: validar reportes).
