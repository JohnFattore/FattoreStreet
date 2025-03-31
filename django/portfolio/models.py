from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords
from .choices import ASSET_TYPES, EXCHANGES, MARKETS

# one to many with assets, asset model could contain this model as foriegn key
class SnP500Price(models.Model):
    date = models.DateField()
    price = models.DecimalField(decimal_places=2, max_digits=100, default=0)
    history = HistoricalRecords()

class AssetInfo(models.Model):
    ticker = models.CharField(max_length=5, unique=True, db_index=True)
    short_name = models.CharField(max_length=100)
    long_name = models.CharField(max_length=250)
    type = models.CharField(max_length=25, choices=ASSET_TYPES)
    exchange = models.CharField(max_length=25, choices=EXCHANGES)
    market = models.CharField(max_length=25, choices=MARKETS)
    history = HistoricalRecords()

class Asset(models.Model):
    asset_info = models.ForeignKey(AssetInfo, on_delete=models.CASCADE)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    cost_basis = models.DecimalField(decimal_places=2, max_digits=25)
    sell_price = models.DecimalField(decimal_places=2, max_digits=25, null=True, blank=True)
    buy_date = models.DateField('date bought')
    sell_date = models.DateField('date sold', null=True, blank=True)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    snp500_buy_date = models.ForeignKey(SnP500Price, on_delete=models.CASCADE, related_name="assets_bought")
    snp500_sell_date = models.ForeignKey(SnP500Price, on_delete=models.CASCADE, null=True, blank=True, related_name="assets_sold")
    history = HistoricalRecords()
    def __str__(self):
        return self.asset_info.ticker

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
        ('CLOSE', 'Close Position'),
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
