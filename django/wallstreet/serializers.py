from rest_framework import serializers
from .models import Option, Selection
from django.contrib.auth.models import User
from django.utils import timezone

# serializer for Option Model
class OptionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Option
        fields = ['id', 'ticker', 'sunday']
    def validate_sunday(self, value):
        # must be a sunday
        if value.weekday() != 6:
            raise serializers.ValidationError("Sunday field must be a sunday")
        return value
    
class SelectionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Selection
        fields = ['id', 'option', 'sunday', 'user']
    def validate_sunday(self, value):
        # must be a sunday
        if value.weekday() != 6:
            raise serializers.ValidationError("Sunday field must be a sunday")
        return value