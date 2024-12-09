from django.db import models
from simple_history.models import HistoricalRecords

class Stock(models.Model):
    ticker = models.CharField(max_length=200)
    name = models.CharField(max_length=1000)
    marketCap = models.DecimalField(decimal_places=0, max_digits=100)
    volume = models.DecimalField(decimal_places=0, max_digits=100, default=0)
    volumeUSD = models.DecimalField(decimal_places=0, max_digits=100, default=0)
    freeFloat = models.DecimalField(decimal_places=5, max_digits=100, default=100)
    freeFloatMarketCap = models.DecimalField(decimal_places=0, max_digits=100, default=0)
    countryIncorp = models.CharField(max_length=1000, default="United States")
    countryHQ = models.CharField(max_length=1000, default="United States")
    securityType = models.CharField(max_length=1000, default="Common Stock")
    yearIPO = models.IntegerField(default=0000)
    history = HistoricalRecords()
    def __str__(self):
        return self.ticker

# One to Many Relationship with Stocks, multiple index members can associated with one stock
class IndexMember(models.Model):
    stock = models.ForeignKey(Stock, on_delete=models.CASCADE)
    percent = models.DecimalField(decimal_places=5, max_digits=100)
    index = models.CharField(max_length=1000)
    outlier = models.BooleanField(default=False)
    notes = models.CharField(max_length=100000, default='')
    history = HistoricalRecords()
    def __str__(self):
        return self.ticker