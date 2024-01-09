from django.db import models
from django.contrib.auth.models import User

class Asset(models.Model):
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    costbasis = models.DecimalField(decimal_places=2, max_digits=10)
    buy = models.DateField('date bought')
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.ticker_string