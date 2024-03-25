from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone
import datetime

class Option(models.Model):
    ticker = models.CharField(max_length=200)
    sunday = models.DateField()
    startPrice = models.DecimalField(decimal_places=2, max_digits=10, default = 1)
    endPrice = models.DecimalField(decimal_places=2, max_digits=10, default = 2)
    percentChange = models.DecimalField(decimal_places=2, max_digits=5)
    rank = models.IntegerField(default=0)

    def save(self, *args, **kwargs):
        self.percentChange = ((self.endPrice - self.startPrice) / self.startPrice) * 100
        super(Option, self).save(*args, **kwargs) # Call the "real" save() method.
    
    def __str__(self):
        return self.ticker

class Selection(models.Model):
    option = models.ForeignKey(Option, on_delete=models.CASCADE)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.option.ticker