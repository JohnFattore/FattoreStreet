from rest_framework import serializers
from .models import Asset, SnP500Price, AssetInfo
from django.utils import timezone

# serializer for SnP500Price Model
class SnP500PriceSerializer(serializers.ModelSerializer):
    class Meta:
        model = SnP500Price
        fields = ['id', 
                  'date', 
                  'price']

class AssetInfoSerializer(serializers.ModelSerializer):
    class Meta:
        model = AssetInfo
        fields = ['id', 
                  'ticker', 
                  'short_name',
                  'long_name',
                  'type',
                  'exchange',
                  'market']

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    asset_info = AssetInfoSerializer(read_only=True)
    snp500_buy_date = SnP500PriceSerializer(read_only=True) 
    snp500_sell_date = SnP500PriceSerializer(read_only=True)
    user = serializers.PrimaryKeyRelatedField(read_only=True)
    cost_basis = serializers.FloatField(read_only=True)
    
    class Meta:
        model = Asset
        fields = ['id', 
                  'shares', 
                  'cost_basis', 
                  'sell_price',
                  'buy_date', 
                  'sell_date',
                  'user',
                  'asset_info',
                  'snp500_buy_date',
                  'snp500_sell_date']
        
    def validate_shares(self, value):
        if value < 0:
            raise serializers.ValidationError("The number of shares must be greater than 0.")
        return value
    
    def validate_buy_date(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The buy date can't be in the future.")
        return value
    
    def validate_sell_date(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The sell date can't be in the future.")
        return value
        
    def validate(self, data):
        buy_date = data.get('buy_date', getattr(self.instance, 'buy_date', None))
        sell_date = data.get('sell_date', getattr(self.instance, 'sell_date', None))

        if buy_date and sell_date and sell_date < buy_date:
            raise serializers.ValidationError("The sell date can't be before the buy date.")
        
        return data