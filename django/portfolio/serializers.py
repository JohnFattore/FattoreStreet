from rest_framework import routers, serializers, viewsets
from .models import Asset
from django.contrib.auth.models import User, Group

# serializer for Asset Model
class AssetSerializer(serializers.ModelSerializer):
    class Meta:
        model = Asset
        fields = ['id',
                  'ticker_string',
                  'shares_number',
                  'costbasis_number',
                  'buy_date',
                  'account_string',
                  'user']
        
# serializer for User Model
class UserSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True)

    # Override default create function, password must be hashed
    def create(self, validated_data):

        user = User.objects.create_user(
            username=validated_data['username'],
            password=validated_data['password'],
        )

        return user

    class Meta:
        model = User
        # Tuple of serialized model fields (see link [2])
        fields = ( "id", "username", "password" )