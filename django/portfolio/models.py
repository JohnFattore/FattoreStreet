from django.db import models
from django.contrib.auth.models import User

class Asset(models.Model):
    ticker_string = models.CharField(max_length=5)
    shares_number = models.DecimalField(decimal_places=5, max_digits=25)
    costbasis_number = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateField('date bought')
    account_string = models.CharField(max_length=100)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    #owner = models.ForeignKey('auth.User', related_name='snippets', on_delete=models.CASCADE)
    def __str__(self):
        return self.ticker_string