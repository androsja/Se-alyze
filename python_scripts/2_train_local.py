import numpy as np
import os
from sklearn.model_selection import train_test_split
from tensorflow.keras.utils import to_categorical
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense
import tensorflow as tf

# ==========================================
# CONFIGURACIÓN
# ==========================================# Configuración
DATA_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dataset') 
SEQUENCE_LENGTH = 35 # Ajustado a 35 frames
EPOCHS = 120         
BATCH_SIZE = 32

def train_local():
    # 1. Cargar Datos
    actions = np.array(sorted([folder for folder in os.listdir(DATA_PATH) if os.path.isdir(os.path.join(DATA_PATH, folder))]))
    print(f"Señas encontradas: {actions}")
    
    label_map = {label:num for num, label in enumerate(actions)}
    
    sequences, labels = [], []
    
    for action in actions:
        action_path = os.path.join(DATA_PATH, action)
        # Listamos carpetas que sean números
        video_dirs = [d for d in os.listdir(action_path) if d.isdigit()]
        
        # Cargar TODAS las secuencias disponibles
        for sequence in video_dirs:
            try:
                # El archivo ya contiene la secuencia completa con padding
                keypoints_file = os.path.join(action_path, str(sequence), "keypoints.npy")
                window = np.load(keypoints_file)  # Shape: (35, 126)
                sequences.append(window)
                labels.append(label_map[action])
            except Exception as e:
                print(f"Error cargando secuencia {sequence} de {action}: {e}")

    X = np.array(sequences)
    
    # --- CRITICAL FIX FOR ANDROID ---
    # The Android App uses HandLandmarker (No Pose).
    # The Dataset (Holistic) has 258 features: [Pose(132) + LH(63) + RH(63)].
    # We must slice X to keep only the last 126 features (LH + RH) to match Android.
    print(f"Original Shape: {X.shape}")
    X = X[:, :, 132:] # Slice: Skip first 132 (Pose)
    print(f"New Shape (Hands Only): {X.shape}") # Should be (N, 30, 126)
    
    y = to_categorical(labels, num_classes=actions.shape[0]).astype(int)

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.05)

    # 2. Crear Modelo
    model = Sequential()
    # Capas LSTM con Dropout
    model.add(LSTM(64, return_sequences=True, activation='tanh', recurrent_activation='sigmoid', input_shape=(SEQUENCE_LENGTH, 126))) # 126 = Keypoints LH+RH
    model.add(tf.keras.layers.Dropout(0.3))
    model.add(LSTM(128, return_sequences=True, activation='tanh', recurrent_activation='sigmoid'))
    model.add(tf.keras.layers.Dropout(0.3))
    model.add(LSTM(64, return_sequences=False, activation='tanh', recurrent_activation='sigmoid'))
    model.add(tf.keras.layers.Dropout(0.3))
    # Capas de Clasificación con Dropout
    model.add(Dense(64, activation='relu'))
    model.add(tf.keras.layers.Dropout(0.4))
    model.add(Dense(32, activation='relu'))
    model.add(tf.keras.layers.Dropout(0.4))
    model.add(Dense(actions.shape[0], activation='softmax')) # Salida = número de señas

    model.compile(optimizer='Adam', loss='categorical_crossentropy', metrics=['categorical_accuracy'])

    # 3. Entrenar
    print("Iniciando entrenamiento local...")
    
    # CALLBACKS: EarlyStopping para evitar sobreentrenamiento y que el modelo "empeore"
    early_stopping = tf.keras.callbacks.EarlyStopping(monitor='categorical_accuracy', patience=20, restore_best_weights=True)
    
    model.fit(X_train, y_train, epochs=500, callbacks=[early_stopping])

    # 4. Guardar
    # Ruta relativa desde la raíz del proyecto (donde se ejecuta el script)
    assets_path = 'Se-alyze-Android/app/src/main/assets'
    
    if not os.path.exists(assets_path):
         print(f"ADVERTENCIA: No encuentro la carpeta assets de Android en: {assets_path}")
         # Intentar crearla
         try:
             os.makedirs(assets_path)
             print("--> Se creó la carpeta assets.")
         except:
             pass
         model.save('lsc_model_v1.h5')
    
    if os.path.exists(assets_path) or os.path.exists('lsc_model_v1.h5'):
        # Convertir a TFLite usando SavedModel (compatible con Keras 3.x)
        temp_saved_model_dir = 'temp_saved_model'
        model.export(temp_saved_model_dir)
        
        converter = tf.lite.TFLiteConverter.from_saved_model(temp_saved_model_dir)
        # Optimizaciones para móvil
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS, # Enable TensorFlow Lite ops.
            tf.lite.OpsSet.SELECT_TF_OPS # Enable TensorFlow ops.
        ]
        tflite_model = converter.convert()
        
        # Limpiar directorio temporal
        import shutil
        if os.path.exists(temp_saved_model_dir):
            shutil.rmtree(temp_saved_model_dir)
        
        output_path = os.path.join(assets_path, 'lsc_model.tflite')
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        
        print(f"¡ÉXITO! Modelo guardado en: {output_path}")
        
        # Guardar etiquetas también
        labels_path = os.path.join(assets_path, 'labels.txt')
        with open(labels_path, 'w') as f:
            for label in actions:
                f.write(label + '\n')
        print(f"Etiquetas guardadas en: {labels_path}")

if __name__ == "__main__":
    train_local()
