from rest_framework import serializers
from django.contrib.auth.models import User
    
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