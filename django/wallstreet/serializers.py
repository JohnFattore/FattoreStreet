from rest_framework import serializers
from rest_framework.validators import UniqueValidator
from .models import Option, Selection
from django.contrib.auth.models import User
from django.utils import timezone
from datetime import datetime

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

# TO DO: cant delete selections of past weeks, not sure where to implement
class SelectionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Selection
        fields=['id', 'option', 'user']
    # 
    def validate(self, data):
        # users can't make the same selection twice
        userSelections = Selection.objects.filter(user=self.context['request'].user)
        for selection in userSelections:
            if data['option'].id == selection.option.id:
                raise serializers.ValidationError("Selection Must be Unique")
        # users can only make 3 selections a week
        # userCurrentSelections = userSelections.objects.filter(sunday= this sunday)
        # user cant change past selections
        return data