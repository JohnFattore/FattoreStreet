from django.db import models


class Stock(models.Model):
    ticker_text = models.CharField(max_length=4)
    costbasis_price = models.DecimalField()
    buy_date = models.DateTimeField('date published')

class ETF(models.Model):
    ticker_text = models.CharField(max_length=4)
    buy_date = models.DateTimeField('date published')