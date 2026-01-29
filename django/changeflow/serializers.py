from rest_framework import serializers
from .models import ChangeRequest

class ChangeRequestSerializer(serializers.ModelSerializer):
    class Meta:
        model = ChangeRequest
        fields = '__all__'
