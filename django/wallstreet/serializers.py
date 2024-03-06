from rest_framework import serializers
from rest_framework.validators import UniqueValidator
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
        fields=['id', 'option', 'user']
    def validate(self, data):
        queryset = Selection.objects.filter(user=data.option)
        if data.option in queryset:
            raise serializers.ValidationError("Selection Must be Unique")
        return data