import cv2
import os
import time
import numpy as np
import mediapipe as mp

# ==========================================
# CONFIGURACIÓN
# ==========================================
# LISTA DE PALABRAS A GRABAR (El script irá una por una)
# Nota: 'ayuda_me' es la versión de 'ecesito ayuda' (hacia el cuerpo)
SIGN_LIST = ["hola", "_none", "agua"] 

DATA_PATH = os.path.join('dataset') 
NO_SEQUENCES = 30   # Videos por seña
SEQUENCE_LENGTH = 20 # Frames por video

# ==========================================
# MEDIAPIPE SETUP
# ==========================================
mp_holistic = mp.solutions.holistic
mp_drawing = mp.solutions.drawing_utils

def mediapipe_detection(image, model):
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB) 
    image.flags.writeable = False                  
    results = model.process(image)                 
    image.flags.writeable = True                   
    image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR) 
    return image, results

def draw_styled_landmarks(image, results):
    # Manos
    mp_drawing.draw_landmarks(image, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
    mp_drawing.draw_landmarks(image, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
    # Pose
    mp_drawing.draw_landmarks(image, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)

def extract_keypoints(results):
    pose = np.array([[res.x, res.y, res.z, res.visibility] for res in results.pose_landmarks.landmark]).flatten() if results.pose_landmarks else np.zeros(33*4)
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten() if results.left_hand_landmarks else np.zeros(21*3)
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten() if results.right_hand_landmarks else np.zeros(21*3)
    return np.concatenate([pose, lh, rh])

# ==========================================
# CÓDIGO DE GRABACIÓN
# ==========================================
def record_sign():
    # CONFIGURAR PANTALLA COMPLETA
    cv2.namedWindow('Recolector LSC', cv2.WINDOW_NORMAL)
    cv2.setWindowProperty('Recolector LSC', cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_FULLSCREEN) 
    time.sleep(0.5)

    cap = cv2.VideoCapture(0)
    
    # FORZAR RESOLUCIÓN HD (1280x720) PARA "ALEJAR" EL ZOOM
    # Muchas cámaras hacen crop (recorte) en bajas resoluciones. Al subirla, se ve más amplio.
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    
    # Check de cámara
    if not cap.isOpened():
        print("ERROR: No se pudo abrir la cámara.")
        return

    # Iniciar MediaPipe Context
    with mp_holistic.Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5) as holistic:
        
        # --- BUCLE PRINCIPAL PARA CADA PALABRA ---
        for sign_name in SIGN_LIST:
            
            # Verificar si ya existe y contar secuencias existentes
            sign_folder = os.path.join(DATA_PATH, sign_name)
            existing_sequences = 0
            
            if os.path.exists(sign_folder):
                existing_sequences = len([d for d in os.listdir(sign_folder) 
                                         if os.path.isdir(os.path.join(sign_folder, d))])
                
                if existing_sequences >= NO_SEQUENCES:
                    print(f"✅ Skipping '{sign_name}', ya tiene {existing_sequences}/{NO_SEQUENCES} secuencias.")
                    continue
                else:
                    print(f"📁 '{sign_name}' tiene {existing_sequences}/{NO_SEQUENCES} secuencias. Grabando {NO_SEQUENCES - existing_sequences} más...")
            else:
                os.makedirs(sign_folder)
                print(f"📁 Creando carpeta para '{sign_name}'")

            # --- PANTALLA DE ESPERA (Interacción Usuario) ---
            print(f"--> Preparando: '{sign_name.upper()}'")
            user_ready = False
            while not user_ready:
                ret, frame = cap.read()
                if not ret: break
                frame = cv2.flip(frame, 1) # Espejo

                # Overlay de instrucciones
                cv2.rectangle(frame, (0,0), (frame.shape[1], 80), (0,0,0), -1) # Barra negra superior
                cv2.putText(frame, f"SIGUIENTE PALABRA: {sign_name.upper()}", (50, 60), 
                           cv2.FONT_HERSHEY_SIMPLEX, 2, (255, 255, 255), 3, cv2.LINE_AA)
                
                # Mostrar progreso
                progress_text = f"Progreso: {existing_sequences}/{NO_SEQUENCES} - Grabar {NO_SEQUENCES - existing_sequences} mas"
                cv2.putText(frame, progress_text, (50, frame.shape[0]-140), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 0), 2, cv2.LINE_AA)
                
                cv2.putText(frame, "Presiona [ESPACIO] para Grabar", (50, frame.shape[0]-100), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 255, 0), 3, cv2.LINE_AA)
                cv2.putText(frame, "Presiona [ESC] para Salir", (50, frame.shape[0]-50), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)

                cv2.imshow('Recolector LSC', frame)
                
                key = cv2.waitKey(10)
                if key == 32: # Espacio (ASCII 32)
                    user_ready = True
                elif key == 27: # ESC (ASCII 27)
                    print("Saliendo...")
                    cap.release()
                    cv2.destroyAllWindows()
                    return

            # --- INICIA GRABACIÓN DE LA PALABRA ---
            # Solo grabar las secuencias que faltan, empezando desde existing_sequences
            for sequence in range(existing_sequences, NO_SEQUENCES):
                # 3. Lógica de Espera UI
                # CONTEO REGRESIVO (3, 2, 1) - OPTIMIZADO
                for countdown in range(2, 0, -1):
                    start_time = time.time()
                    while time.time() - start_time < 1.0:
                        ret_count, frame_count = cap.read()
                        if not ret_count: break
                        frame_count = cv2.flip(frame_count, 1)
                        
                        # Texto Gigante
                        text = str(countdown)
                        font_scale = 10.0
                        thickness = 20
                        font = cv2.FONT_HERSHEY_SIMPLEX
                        text_size = cv2.getTextSize(text, font, font_scale, thickness)[0]
                        text_x = (frame_count.shape[1] - text_size[0]) // 2
                        text_y = (frame_count.shape[0] + text_size[1]) // 2
                        
                        cv2.putText(frame_count, text, (text_x, text_y), 
                                   font, font_scale, (0, 255, 255), thickness, cv2.LINE_AA)
                        cv2.putText(frame_count, f"GRABANDO: {sign_name.upper()}", (50, 100), 
                                   cv2.FONT_HERSHEY_SIMPLEX, 2, (255,255,255), 3)
                                   
                        cv2.putText(frame_count, f'Video {sequence + 1}/{NO_SEQUENCES}', (15,50), 
                                   cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                        
                        cv2.imshow('Recolector LSC', frame_count)
                        cv2.waitKey(10)

                # GRABANDO FRAMES REALES
                for frame_num in range(SEQUENCE_LENGTH):
                    ret, frame = cap.read()
                    if not ret: 
                        print(f"Error frame: No se pudo leer cámara.")
                        break
                    
                    frame = cv2.flip(frame, 1)

                    # 1. Detección
                    image, results = mediapipe_detection(frame, holistic)

                    # 2. Dibujar (Visual)
                    draw_styled_landmarks(image, results)
                    
                    cv2.putText(image, '::: GRABANDO :::', (120,200), 
                               cv2.FONT_HERSHEY_SIMPLEX, 1, (0,255, 0), 4, cv2.LINE_AA)
                    cv2.putText(image, f'Palabra: {sign_name} - {sequence + 1}/{NO_SEQUENCES}', (15,30), 
                               cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                    
                    cv2.imshow('Recolector LSC', image)
                    
                    # 4. Exportar Keypoints
                    keypoints = extract_keypoints(results)
                    
                    # Crear carpetas si no existen (redundancia segura)
                    seq_path = os.path.join(sign_folder, str(sequence))
                    if not os.path.exists(seq_path): os.makedirs(seq_path)
                    
                    npy_full_path = os.path.join(seq_path, str(frame_num))
                    np.save(npy_full_path, keypoints)

                    if cv2.waitKey(1) & 0xFF == ord('q'): # Salida de emergencia
                        break
    
    cap.release()
    cv2.destroyAllWindows()
    print("¡Sesión completa! Todas las palabras grabadas.")

if __name__ == "__main__":
    record_sign()
