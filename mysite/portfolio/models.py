from django.db import models
from django.contrib.auth.models import User

class Asset(models.Model):
    ticker_text = models.CharField(max_length=5)
    shares_integer = models.DecimalField(decimal_places=5, max_digits=10)
    costbasis_price = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateField('date bought')
    account = models.CharField(max_length=100)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.ticker_text

class Allocation(models.Model):
    ticker_text = models.CharField(max_length=5)
    shares_integer = models.DecimalField(decimal_places=5, max_digits=10)
    currentPrice = models.DecimalField(decimal_places=2, max_digits=10)
    percent_allocated = models.DecimalField(decimal_places=2, max_digits=5)
    def __str__(self):
        return self.ticker_text