from transformers import AutoModelForCausalLM, AutoTokenizer

# Replace with the path or Hugging Face model name where you downloaded the model
model_path = "meta-llama/Llama-3.2-1B"

# Load tokenizer
tokenizer = AutoTokenizer.from_pretrained(model_path)

# Load model (this will detect if you have CUDA and map it to the GPU)
model = AutoModelForCausalLM.from_pretrained(
    model_path,
    device_map="auto",  # Automatically selects CPU or GPU
    load_in_4bit=True,
    torch_dtype="auto"  # Uses appropriate precision for your GPU
)

print("Model and tokenizer loaded successfully!")

# Input text for the model
input_text = "whats the difference between sourwood and regular honey?"

# Tokenize input
input_ids = tokenizer(input_text, return_tensors="pt").input_ids.to(model.device)

# Generate text
output = model.generate(input_ids, max_length=200, temperature=0.7)

# Decode and print the output
print(tokenizer.decode(output[0], skip_special_tokens=True))