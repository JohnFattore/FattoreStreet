import os
import google.generativeai as genai
import environ

from dotenv import load_dotenv
import os

load_dotenv()  # Load .env file
api_key = os.getenv("GOOGLE_API_KEY")

genai.configure(api_key=api_key)

# Create the model
generation_config = {
  "temperature": 1,
  "top_p": 0.95,
  "top_k": 40,
  "max_output_tokens": 8192,
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