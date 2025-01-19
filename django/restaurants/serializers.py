from rest_framework import serializers
from .models import Restaurant, Review, MenuItem, Favorite

class RestaurantSerializer(serializers.ModelSerializer):
    class Meta:
        model = Restaurant
        fields = [
                'yelp_id',
                'name',
                'address',
                'state',
                'city',
                'latitude',
                'longitude',
                'categories',
                'stars',
                'review_count',
                'id'
        ]

class ReviewSerializer(serializers.ModelSerializer):
    restaurant = serializers.PrimaryKeyRelatedField(queryset=Restaurant.objects.all())  # Accepts ID for input
    restaurant_detail = RestaurantSerializer(source='restaurant', read_only=True)  # Nested representation for output

    class Meta:
        model = Review
        fields = [
                'user',
                'rating',
                'comment',
                'id',
                'restaurant',
                'restaurant_detail'
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