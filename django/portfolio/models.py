from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords

# one to many with assets, asset model could contain this model as foriegn key
class SnP500Price(models.Model):
    date = models.DateField()
    price = models.DecimalField(decimal_places=2, max_digits=100, default=0)
    history = HistoricalRecords()

class Asset(models.Model):
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    cost_basis = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateField('date bought')
    # sell_date = models.DateField('date sold')
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    snp500_buy_date = models.ForeignKey(SnP500Price, on_delete=models.CASCADE) # need to differientiat this from the sell price
    # snp500_sell_date_price
    history = HistoricalRecords()
    def __str__(self):
        return self.ticker

'''
# just messing around down here 
class Security(models.Model):
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    cost_basis = models.DecimalField(decimal_places=2, max_digits=25) # total, should never change
    dividends = models.DecimalField(decimal_places=2, max_digits=25)
    buy_date = models.DateField('date bought')
    sell_date = models.DateField('date sold')
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    # SnP500Price = models.ForeignKey(SnP500Price, on_delete=models.CASCADE)
    history = HistoricalRecords()
    def __str__(self):
        return self.ticker
    
class SecurityTransaction(models.Model):
    TRANSACTION_TYPES = [
        ('BUY', 'Buy'),
        ('SELL', 'Sell'),
        ('DIVIDEND', 'Dividend'),
        ('TRANSFER', 'Transfer'),
        ('INTEREST', 'Interest'),
    ]
    security = models.ForeignKey(Security, on_delete=models.CASCADE)
    date = models.DateField()
    type = models.CharField(max_length=10, choices=TRANSACTION_TYPES)
    share_change = models.DecimalField(decimal_places=5, max_digits=25)
    amount = models.DecimalField(decimal_places=2, max_digits=25)
    history = HistoricalRecords()

class UserBank(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    amount = models.DecimalField(decimal_places=2, max_digits=25, default=1000000)
'''
