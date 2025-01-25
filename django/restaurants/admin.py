from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Restaurant, Review, MenuItem, Favorite  

@admin.register(Restaurant)
class RestaurantAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'name', 'address', 'state', 'city', 'latitude', 'longitude', 'categories', 'stars', 'review_count', 'yelp_id')
    history_list_display = ('id', 'name', 'address', 'state', 'city', 'latitude', 'longitude', 'categories', 'stars', 'review_count', 'yelp_id')

@admin.register(Review)
class ReviewAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'restaurant', 'user', 'rating', 'comment')
    history_list_display = ('id', 'restaurant', 'user', 'rating', 'comment') 

@admin.register(MenuItem)
class MenuItemAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'restaurant', 'name', 'description', 'price')
    history_list_display = ('id', 'restaurant', 'name', 'description', 'price')

@admin.register(Favorite)
class FavoriteAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'menu_item', 'user', 'comment')
    history_list_display = ('id', 'menu_item', 'user', 'comment')