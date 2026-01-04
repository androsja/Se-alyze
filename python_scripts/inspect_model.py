import tensorflow as tf
import os

model_path = os.path.join("Se-alyze-Android", "app", "src", "main", "assets", "lsc_model.tflite")

try:
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("== Input Details ==")
    for i, detail in enumerate(input_details):
        print(f"Input {i}: {detail}")

    print("\n== Output Details ==")
    for i, detail in enumerate(output_details):
        print(f"Output {i}: {detail}")

    print("\n== All Tensor Details (First 10) ==")
    tensor_details = interpreter.get_tensor_details()
    for i, detail in enumerate(tensor_details[:10]):
        print(f"Tensor {i}: {detail['name']} shape={detail['shape']}")

except Exception as e:
    print(f"Error inspecting model: {e}")
