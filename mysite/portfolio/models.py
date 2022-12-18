from django.db import models


class Stock(models.Model):
    ticker_text = models.CharField(max_length=4)
    shares_integer = models.IntegerField()
    costbasis_price = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateTimeField('date bought')

class ETF(models.Model):
    ticker_text = models.CharField(max_length=4)
    shares_integer = models.IntegerField()
    costbasis_price = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateTimeField('date bought')