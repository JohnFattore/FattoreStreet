from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords

# one to many with assets, asset model could contain this model as foriegn key
class SnP500Price(models.Model):
    date = models.DateField()
    price = models.DecimalField(decimal_places=2, max_digits=100, default=0)
    dividends = models.DecimalField(decimal_places=5, max_digits=100, default=0)
    reinvestShares = models.DecimalField(decimal_places=5, max_digits=25)
    history = HistoricalRecords()

class Asset(models.Model):
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    costbasis = models.DecimalField(decimal_places=2, max_digits=10)
    buyDate = models.DateField('date bought')
    dividends = models.DecimalField(decimal_places=5, max_digits=100, default=0)
    reinvestShares = models.DecimalField(decimal_places=5, max_digits=25)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    SnP500Price = models.ForeignKey(SnP500Price, on_delete=models.CASCADE)
    history = HistoricalRecords()
    def __str__(self):
        return self.ticker