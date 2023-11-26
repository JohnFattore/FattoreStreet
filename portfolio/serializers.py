from rest_framework import routers, serializers, viewsets
from .models import Asset
from django.contrib.auth.models import User, Group

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    class Meta:
        model = Asset
        fields = ['ticker_string',
                  'shares_number',
                  'costbasis_number',
                  'buy_date',
                  'account_string',
                  'user_key']
        
# serializer for User Model
class UserSerializer(serializers.HyperlinkedModelSerializer):
    class Meta:
        model = User
        fields = ['username', 
                  'email', 
                  'groups']