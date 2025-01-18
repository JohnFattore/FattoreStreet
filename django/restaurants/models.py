from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords

class Restaurant(models.Model):
    RATING_CHOICES = [
    (1, '1 - Poor'),
    (1.5, '1.5 - Subpar'),
    (2, '2 - Fair'),
    (2.5, '2.5 - Almost Good'),
    (3, '3 - Good'),
    (3.5, '3.5 - Decent'),
    (4, '4 - Very Good'),
    (4.5, '4.5 - Excellent-ish'),
    (5, '5 - Excellent'),
    ]
    yelp_id= models.CharField(max_length=250, default=1)
    name = models.CharField(max_length=250)
    address = models.TextField()
    state = models.TextField(default="Nash")
    city = models.TextField(default="NA")
    latitude = models.DecimalField(max_digits=25, decimal_places=8, default=1)
    longitude = models.DecimalField(max_digits=25, decimal_places=8, default=1)
    categories = models.TextField(default="eat")
    stars = models.DecimalField(max_digits=2, decimal_places=1, choices=RATING_CHOICES, default=1)
    review_count = models.IntegerField(default=0)
    history = HistoricalRecords()
    def __str__(self):
        return self.name
    
class Review(models.Model):
    RATING_CHOICES = [
    (1, '1 - Poor'),
    (1.5, '1.5 - Subpar'),
    (2, '2 - Fair'),
    (2.5, '2.5 - Almost Good'),
    (3, '3 - Good'),
    (3.5, '3.5 - Decent'),
    (4, '4 - Very Good'),
    (4.5, '4.5 - Excellent-ish'),
    (5, '5 - Excellent'),
    ]
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    rating = models.PositiveSmallIntegerField(choices=RATING_CHOICES) 
    comment = models.TextField(blank=True, null=True) 
    history = HistoricalRecords()
    def __str__(self):
        return self.restaurant.name
    
class MenuItem(models.Model):
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE)
    name = models.CharField(max_length=100)
    description = models.TextField(blank=True, null=True)
    price = models.DecimalField(max_digits=10, decimal_places=2)
    history = HistoricalRecords()
    def __str__(self):
        return self.name

class Favorite(models.Model):
    menu_item = models.ForeignKey(MenuItem, on_delete=models.CASCADE)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    comment = models.TextField(blank=True, null=True)
    history = HistoricalRecords()
    def __str__(self):
        return self.user.username