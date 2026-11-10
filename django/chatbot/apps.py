from django.apps import AppConfig


class ChatbotConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'chatbot'

    def ready(self):
        from .prompt import reference_text

        # Fail at boot, not on the first question, if the image shipped without chatbot/context/
        reference_text()
