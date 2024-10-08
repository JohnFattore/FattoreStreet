from rest_framework import serializers
from .models import Asset, SnP500Price
from django.contrib.auth.models import User
from django.utils import timezone

    
# serializer for User Model
class UserSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True)

    # Override default create function, password must be hashed
    def create(self, validated_data):

        user = User.objects.create_user(
            username=validated_data['username'],
            password=validated_data['password'],
            email=validated_data['email'],
        )

        return user

    class Meta:
        model = User
        # Tuple of serialized model fields (see link [2])
        fields = ( "id", "username", "password", "email" )

# serializer for SnP500Price Model
class SnP500PriceSerializer(serializers.ModelSerializer):
    class Meta:
        model = SnP500Price
        fields = ['id', 
                  'date', 
                  'price']

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    SnP500Price = SnP500PriceSerializer(read_only=True) 

    class Meta:
        model = Asset
        fields = ['id', 
                  'ticker', 
                  'shares', 
                  'costbasis', 
                  'buy', 
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
    
    def validate_buy(self, value):
        if value > timezone.now().date():
            raise serializers.ValidationError("The buy date can't be in the future.")
        return value