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
    restaurant = serializers.PrimaryKeyRelatedField(queryset=Restaurant.objects.all())
    restaurant_detail = RestaurantSerializer(source='restaurant', read_only=True)
    rating = serializers.DecimalField(max_digits=2, decimal_places=1)

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

    def validate(self, data):
        user = self.context['request'].user
        restaurant = data['restaurant']

        if Review.objects.filter(user=user, restaurant=restaurant).exists():
            raise serializers.ValidationError("You have already reviewed this restaurant.")

        return data

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