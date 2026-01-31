from rest_framework import serializers
from .models import Interaction

class ChatMessageSerializer(serializers.Serializer):
    message = serializers.CharField(max_length=500)  # Adjust max_length as needed

class InteractionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Interaction
        fields = ['input_text', 'output_text', 'timestamp']