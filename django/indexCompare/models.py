from django.db import models

# Create your models here.
# benchmarks: SPY, VTWO, VT, Even Allocation
class Stock(models.Model):
    ticker = models.CharField(max_length=200)
    name = models.CharField(max_length=1000)
    marketCap = models.DecimalField(decimal_places=0, max_digits=100)
    countryIncorp = models.CharField(max_length=1000, default="United States")
    countryHQ = models.CharField(max_length=1000, default="United States")
    securityType = models.CharField(max_length=1000, default="Common Stock")
    def __str__(self):
        return self.ticker
    
class IndexMember(models.Model):
    ticker = models.CharField(max_length=200)
    percent = models.DecimalField(decimal_places=5, max_digits=100)
    index = models.CharField(max_length=1000)
    def __str__(self):
        return self.ticker