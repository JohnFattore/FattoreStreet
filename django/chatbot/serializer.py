from rest_framework import serializers
from .models import Conversation, Interaction

class ChatMessageSerializer(serializers.Serializer):
    message = serializers.CharField(max_length=500)  # Adjust max_length as needed

class InteractionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Interaction
        fields = ['input_text', 'output_text', 'timestamp']

class ConversationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Conversation
        fields = ['id', 'title', 'created_at', 'last_message_at']
        read_only_fields = ['id', 'created_at', 'last_message_at']

class ConversationDetailSerializer(ConversationSerializer):
    interactions = InteractionSerializer(many=True, read_only=True)

    class Meta(ConversationSerializer.Meta):
        fields = ConversationSerializer.Meta.fields + ['interactions']
