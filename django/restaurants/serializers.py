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

class MenuItemSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuItem
        fields = [
                'restaurant',
                'name',
                'description',
                'price',
        ]

class FavoriteSerializer(serializers.ModelSerializer):
    class Meta:
        model = Favorite
        fields = [
                'menu_item',
                'user',
        ]