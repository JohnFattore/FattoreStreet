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
    snp500_buy_date = SnP500PriceSerializer(read_only=True) 

    class Meta:
        model = Asset
        fields = ['id', 
                  'ticker', 
                  'shares', 
                  'cost_basis', 
                  'buy_date', 
                  'user',
                  'snp500_buy_date']
        
    def validate_shares(self, value):
        if value < 0:
            raise serializers.ValidationError("The number of shares must be greater than 0.")
        return value
    
    def validate_cost_basis(self, value):
        if value < 0:
            raise serializers.ValidationError("The cost basis must be greater than 0.")
        return value
    
    def validate_buy_date(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The buy date can't be in the future.")
        return value