from rest_framework import serializers
from .models import Outlier

class OutlierSerializer(serializers.ModelSerializer):
    class Meta:
        model = Outlier
        fields = ['id', 'ticker', 'name', 'marketCap', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'countryIncorp', 'countryHQ', 'securityType', 'yearIPO', 'notes']