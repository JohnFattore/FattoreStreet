from rest_framework import serializers
from .models import Asset, SnP500Price
from django.utils import timezone

# serializer for SnP500Price Model
class SnP500PriceSerializer(serializers.ModelSerializer):
    class Meta:
        model = SnP500Price
        fields = ['id', 
                  'date', 
                  'price',
                  ]

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    SnP500Price = SnP500PriceSerializer(read_only=True) 

    class Meta:
        model = Asset
        fields = ['id', 
                  'ticker', 
                  'shares', 
                  'costbasis', 
                  'buyDate', 
                  'user',
                  'SnP500Price']
        
    def validate_shares(self, value):
        if value < 0:
            raise serializers.ValidationError("The number of shares must be greater than 0.")
        return value
    
    def validate_costbasis(self, value):
        if value < 0:
            raise serializers.ValidationError("The cost basis must be greater than 0.")
        return value
    
    def validate_buyDate(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The buy date can't be in the future.")
        return value