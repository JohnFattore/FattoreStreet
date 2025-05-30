import os
import google.generativeai as genai
from dotenv import load_dotenv
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent  # Adjust if needed
env_path = BASE_DIR / "mysite" / ".env"  # Example: .env is inside a "config" folder

load_dotenv(env_path)  # Load .env file
api_key = os.getenv("GOOGLE_API_KEY")

genai.configure(api_key=api_key)

# Create the model
generation_config = {
  "temperature": 0.5,
  "top_p": 0.95,
  "top_k": 40,
  "max_output_tokens": 1024,
  "response_mime_type": "text/plain",
}

model = genai.GenerativeModel(
  model_name="gemini-2.0-flash",
  generation_config=generation_config,
)

chat_session = model.start_chat(
  history=[
        {"role": "user", "parts": ["Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds."]}
      ]
)

response = chat_session.send_message("What should I invest in?")

print(response.text)