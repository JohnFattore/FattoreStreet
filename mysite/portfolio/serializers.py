from rest_framework import serializers
from .models import Asset

class AssetSerializer(serializers.ModelSerializer):
    class Meta:
        model = Asset
        fields = ('ticker_text', 'shares_integer', 'costbasis_price', 'buy_date', 'account', 'user')