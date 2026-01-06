import shutil
import cv2
import os
import time
import numpy as np
import mediapipe as mp

# ==========================================
# CONFIGURACIÓN
# ==========================================
# Configuración de muestras por seña
DEFAULT_SEQUENCES = 30
SIGN_LIST = [] 

try:
    with open(os.path.join(os.path.dirname(__file__), 'target_words.txt'), 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'): continue
            
            parts = line.split(',')
            name = parts[0].strip()
            count = int(parts[1].strip()) if len(parts) > 1 else DEFAULT_SEQUENCES
            SIGN_LIST.append((name, count))
            
except FileNotFoundError:
    print("⚠️ ADVERTENCIA: No se encontró 'target_words.txt'. Usando lista por defecto.")
    SIGN_LIST = [("hola", DEFAULT_SEQUENCES), ("agua", DEFAULT_SEQUENCES), ("_none", DEFAULT_SEQUENCES)]

print(f"📋 Lista de trabajo cargada: {SIGN_LIST}") 
DATA_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dataset') # Ruta absoluta: Se-alyze/dataset
NO_SEQUENCES = 30   # Videos por seña
SEQUENCE_LENGTH = 32  # AJUSTADO: 32 frames (aprox 1.05 seg a 30 FPS)
MIN_LENGTH = 15      # Mínimo de frames para que sea válidaara mayor velocidad)

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
    # Cara (Mesh Contours) - Para que el usuario vea el mentón
    if results.face_landmarks:
        mp_drawing.draw_landmarks(
            image, 
            results.face_landmarks, 
            mp_holistic.FACEMESH_CONTOURS,
            mp_drawing.DrawingSpec(color=(80,110,10), thickness=1, circle_radius=1),
            mp_drawing.DrawingSpec(color=(80,256,121), thickness=1, circle_radius=1)
        )

    # Manos
    mp_drawing.draw_landmarks(image, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
    mp_drawing.draw_landmarks(image, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
    # Pose
    mp_drawing.draw_landmarks(image, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)

def extract_keypoints(results):
    # POSE contiene 33 puntos, incluyendo nariz (0), ojos y boca (9,10). 
    # Usamos esto como referencia del "mentón".
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
        for sign_name, target_sequences in SIGN_LIST:
            
            # Verificar si ya existe y contar secuencias existentes
            sign_folder = os.path.join(DATA_PATH, sign_name)
            # target_sequences viene de la tupla (parsed from txt)
            existing_sequences = 0
            
            if os.path.exists(sign_folder):
                existing_sequences = len([d for d in os.listdir(sign_folder) 
                                         if os.path.isdir(os.path.join(sign_folder, d))])
                
                if existing_sequences >= target_sequences:
                    print(f"✅ Skipping '{sign_name}', ya tiene {existing_sequences}/{target_sequences} secuencias.")
                    continue
                else:
                    print(f"📁 '{sign_name}' tiene {existing_sequences}/{target_sequences} secuencias. Grabando {target_sequences - existing_sequences} más...")
            else:
                os.makedirs(sign_folder)
                print(f"📁 Creando carpeta para '{sign_name}'")

            # --- PANTALLA DE ESPERA (Interacción Usuario) ---
            print(f"--> Preparando: '{sign_name.upper()}'")
            user_ready = False
            record_video_mode = False # Estado del toggle
            
            while not user_ready:
                ret, frame = cap.read()
                if not ret: break
                frame = cv2.flip(frame, 1) # Espejo

                # Overlay de instrucciones
                display_name = sign_name.replace("_", " ").upper()
                cv2.rectangle(frame, (0,0), (frame.shape[1], 80), (0,0,0), -1) # Barra negra superior
                cv2.putText(frame, f"SIGUIENTE PALABRA: {display_name}", (50, 60), 
                           cv2.FONT_HERSHEY_SIMPLEX, 2, (255, 255, 255), 3, cv2.LINE_AA)
                
                # Mostrar progreso
                progress_text = f"Progreso: {existing_sequences}/{target_sequences} - Grabar {target_sequences - existing_sequences} mas"
                cv2.putText(frame, progress_text, (50, frame.shape[0]-180), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 0), 2, cv2.LINE_AA)
                
                # Instrucción Video
                video_status = "ACTIVADO" if record_video_mode else "DESACTIVADO"
                video_color = (0, 255, 0) if record_video_mode else (0, 0, 255)
                cv2.putText(frame, f"Grabar Video (Presiona V): [{video_status}]", (50, frame.shape[0]-140), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, video_color, 2, cv2.LINE_AA)

                cv2.putText(frame, "Presiona [ESPACIO] para Grabar", (50, frame.shape[0]-100), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 255, 0), 3, cv2.LINE_AA)
                cv2.putText(frame, "Presiona [ESC] para Salir", (50, frame.shape[0]-50), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)

                cv2.imshow('Recolector LSC', frame)
                
                key = cv2.waitKey(10)
                if key == 32: # Espacio (ASCII 32)
                    user_ready = True
                elif key == 118 or key == 86: # 'v' o 'V'
                    record_video_mode = not record_video_mode
                elif key == 27: # ESC (ASCII 27)
                    print("Saliendo...")
                    cap.release()
                    cv2.destroyAllWindows()
                    return

            # --- INICIALIZAR GRABADOR DE VIDEO (Si se activó) ---
            out_video = None
            if record_video_mode:
                video_filename = os.path.join(sign_folder, f"{sign_name}_raw_video.mov")
                fourcc = cv2.VideoWriter_fourcc(*'mp4v') # Codec .mov
                fps = 30.0 # Aproximado, la webcam puede variar
                resolution = (1280, 720) # Debe coincidir con cap.set
                out_video = cv2.VideoWriter(video_filename, fourcc, fps, resolution)
                print(f"🎥 Grabando video en: {video_filename}")

            # --- INICIA GRABACIÓN DE LA PALABRA ---
            # Solo grabar las secuencias que faltan, empezando desde existing_sequences
            for sequence in range(existing_sequences, target_sequences):
                # 3. Lógica de Espera UI
                # CONTEO REGRESIVO (1) - MÁS RÁPIDO
                collected_keypoints = [] # Initialize list for this sequence
                
                for countdown in range(1, 0, -1):
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
                        cv2.putText(frame_count, f"GRABANDO: {sign_name.replace('_', ' ').upper()}", (50, 100), 
                                   cv2.FONT_HERSHEY_SIMPLEX, 2, (255,255,255), 3)
                                   
                        cv2.putText(frame_count, f'Video {sequence + 1}/{target_sequences}', (15,50), 
                                   cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                        

                        cv2.imshow('Recolector LSC', frame_count)
                        if cv2.waitKey(10) == 27: # ESC
                            print("Saliendo durante conteo...")
                            if out_video: out_video.release()
                            cap.release()
                            cv2.destroyAllWindows()
                            return
                            
                print(f"🎬 Grabando secuencia {sequence + 1}/{target_sequences} para '{sign_name}'...")
                os.system("afplay /System/Library/Sounds/Ping.aiff &") # Sonido de inicio

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
                    
                    rec_indicator = "::: GRABANDO VIDEO :::" if record_video_mode else "::: GRABANDO :::"
                    rec_color = (0, 0, 255) if record_video_mode else (0, 255, 0)

                    cv2.putText(image, rec_indicator, (120,200), 
                               cv2.FONT_HERSHEY_SIMPLEX, 1, rec_color, 4, cv2.LINE_AA)
                    cv2.putText(image, f'Palabra: {sign_name} - {sequence + 1}/{target_sequences}', (15,30), 
                                   cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)
                    
                    # ⚠️ GUARDAR VIDEO ANOTADO (Después de dibujar)
                    if out_video:
                        out_video.write(image)

                    cv2.imshow('Recolector LSC', image)
                    
                    # 4. Exportar Keypoints
                    keypoints = extract_keypoints(results)
                    collected_keypoints.append(keypoints) # Collect keypoints for the sequence
                    
                    if cv2.waitKey(1) & 0xFF == 27: # ESC (ASCII 27)
                         print("Interrupción detectada (ESC). Borrando secuencia corrupta...")
                         # No need to remove folder here, as it's handled after the loop
                         if out_video: out_video.release()
                         cap.release()
                         cv2.destroyAllWindows()
                         return
                
                # After collecting all frames for a sequence
                # After collecting all frames for a sequence
                seq_path = os.path.join(sign_folder, str(sequence))
                
                # GUARDAR SECUENCIA (Rellenar con ceros si es corta)
                if len(collected_keypoints) >= MIN_LENGTH: # Use collected_keypoints length
                     res_array = np.array(collected_keypoints) # Shape: (frames_reales, 126)
                     
                     # PADDING: Rellenar hasta llegar a 60
                     if len(collected_keypoints) < SEQUENCE_LENGTH:
                         padding_needed = SEQUENCE_LENGTH - len(collected_keypoints)
                         # Rellenamos con ceros al final
                         # Shape del padding: (padding_needed, 126)
                         padding = np.zeros((padding_needed, res_array.shape[1])) # Use actual keypoint dimension
                         res_array = np.vstack((res_array, padding))
                     
                     # Recortar si se pasó (por seguridad, aunque el break lo evita)
                     res_array = res_array[:SEQUENCE_LENGTH]
                     
                     # Create folder if it doesn't exist
                     if not os.path.exists(seq_path): os.makedirs(seq_path)
                     
                     npy_full_path = os.path.join(seq_path, "keypoints.npy") # Save as a single file for the sequence
                     np.save(npy_full_path, res_array)
                     print(f"🎬 Grabando secuencia {sequence + 1}/{target_sequences} para '{sign_name}'...")
                else:
                    print(f"⚠️ Seña muy corta ({len(collected_keypoints)} frames). Ignorada para '{sign_name}' secuencia {sequence + 1}.")
                    # If the sequence is too short and ignored, remove its folder if it was created
                    if os.path.exists(seq_path):
                        shutil.rmtree(seq_path, ignore_errors=True)
    
    if out_video: 
         out_video.release()
         print("🎥 Video guardado correctamente.")

    cap.release()
    cv2.destroyAllWindows()
    print("¡Sesión completa! Todas las palabras grabadas.")

if __name__ == "__main__":
    record_sign()
