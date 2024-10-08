from rest_framework import serializers
from .models import Outlier, IndexMember, Stock

class OutlierSerializer(serializers.ModelSerializer):
    class Meta:
        model = Outlier
        fields = ['id', 'ticker', 'name', 'marketCap', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'countryIncorp', 'countryHQ', 'securityType', 'yearIPO', 'notes']

class StockSerializer(serializers.ModelSerializer):
    class Meta:
        model = Stock
        fields = [
                'ticker',
                'name',
                'marketCap',
                'volume',
                'volumeUSD',
                'freeFloat',
                'freeFloatMarketCap',
                'countryIncorp',
                'countryHQ',
                'securityType',
                'yearIPO',
        ]

class IndexMemberSerializer(serializers.ModelSerializer):
    # nested serialzer
    stock = StockSerializer(read_only=True) 
    class Meta:
        model = IndexMember
        fields = ['id',
                  'percent',
                  'index',
                  'outlier',
                  'notes',
                  'stock']