from django.db import models
from django.contrib.auth.models import User

# benchmarks: SPY, VTWO, VT, 
class Option(models.Model):
    ticker = models.CharField(max_length=200)
    sunday = models.DateField()
    startPrice = models.DecimalField(decimal_places=2, max_digits=10, default = 1)
    endPrice = models.DecimalField(decimal_places=2, max_digits=10, default = 2)
    percentChange = models.DecimalField(decimal_places=2, max_digits=5, default=0)
    rank = models.IntegerField(default=0)
    benchmark = models.BooleanField(default=False)
    def __str__(self):
        return self.ticker

class Selection(models.Model):
    option = models.ForeignKey(Option, on_delete=models.CASCADE)
    allocation = models.DecimalField(decimal_places=2, max_digits=5, default=0)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.option.ticker

class Result(models.Model):
    portfolioPercentChange = models.DecimalField(decimal_places=2, max_digits=5, default=0)
    sunday = models.DateField()
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.sunday
    
class AltBenchmark(models.Model):
    benchmark = models.CharField(max_length=256)
    percentChange = models.DecimalField(decimal_places=2, max_digits=10, default = 2)
    sunday = models.DateField()
    def __str__(self):
        return self.benchmark