from rest_framework import serializers
from .models import Option, Selection, Result
from django.contrib.auth.models import User
from datetime import date, timedelta
import environ

env = environ.Env()
environ.Env.read_env()

# serializer for Option Model
class OptionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Option
        fields = ['id', 'ticker', 'name', 'sunday', 'startPrice', 'endPrice', 'percentChange', 'rank', 'benchmark']
    def validate_sunday(self, value):
        # must be a sunday
        if value.weekday() != 6:
            raise serializers.ValidationError("Sunday field must be a sunday")
        return value

class SelectionSerializer(serializers.ModelSerializer):
    ticker = serializers.SerializerMethodField()
    name = serializers.SerializerMethodField()
    sunday = serializers.SerializerMethodField()

    class Meta:
        model = Selection
        fields = ['id', 'ticker', 'name', 'sunday']

    def get_ticker(self, obj):
        selected_option = Option.objects.get(id=obj.option.id)
        return OptionSerializer(selected_option).data["ticker"]
    def get_name(self, obj):
        selected_option = Option.objects.get(id=obj.option.id)
        return OptionSerializer(selected_option).data["name"]
    def get_sunday(self, obj):
        selected_option = Option.objects.get(id=obj.option.id)
        return OptionSerializer(selected_option).data["sunday"]
    
    def validate(self, data):
        # users can't make the same selection twice
        userSelections = Selection.objects.filter(user=self.context['request'].user)
        for selection in userSelections:
            if data['option'].id == selection.option.id:
                raise serializers.ValidationError("Selection Must be Unique")
        # get userSelections for sunday of option
        option = Option.objects.get(id=data['option'].id)
        currentOptions = Option.objects.filter(sunday=option.sunday)
        userCurrentSelections = userSelections.filter(option_id__in=currentOptions)
        today = date.today()
        if (userCurrentSelections.count() >= 3):
            raise serializers.ValidationError("Only 3 Selections per Week")
        # I think this can be deleted
        env("CUTOVER_WEEKDAY")
        if (((option.sunday) + timedelta(days=int(env("CUTOVER_ISOWEEKDAY")), hours=int(env("CUTOVER_HOUR")))) < today):
            raise serializers.ValidationError("Cant add selections for past weeks")
        if (option.benchmark == True):
            raise serializers.ValidationError("Cant select benchmarks")
        return data
    
class ResultSerializer(serializers.ModelSerializer):
    class Meta:
        model = Result
        fields = ['id', 'portfolioPercentChange', 'sunday']