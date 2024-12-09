from rest_framework import serializers
from .models import Restaurant, Review, MenuItem, Favorite

class RestaurantSerializer(serializers.ModelSerializer):
    class Meta:
        model = Restaurant
        fields = [
                'name',
                'address',
                'phone_number',
                'website',
        ]

class ReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = Review
        fields = [
                'restaurant',
                'user',
                'rating',
                'comment',
        ]

class ReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = Review
        fields = [
                'restaurant',
                'name',
                'description',
                'price',
        ]

class ReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = Review
        fields = [
                'menu_item',
                'user',
        ]