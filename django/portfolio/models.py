from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords


class Account(models.Model):
    ACCOUNT_TYPES = [
        ('ROTH_IRA', 'Roth IRA'),
        ('TRADITIONAL_IRA', 'Traditional IRA'),
        ('TAXABLE_ACCOUNT', 'Taxable Account'),
        ('ROTH_401K', 'Roth 401k'),
        ('TRADITIONAL_401K', 'Traditional 401k'),
        ('OTHER', 'Other'),
    ]
    name = models.CharField(max_length=255)
    account_type = models.CharField(max_length=20, choices=ACCOUNT_TYPES, default='OTHER')
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    history = HistoricalRecords()

    def __str__(self):
        return self.name

class Asset(models.Model):
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    buy_date = models.DateField('date bought')
    sell_date = models.DateField('date sold', null=True, blank=True)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    account = models.ForeignKey(Account, on_delete=models.CASCADE, null=True, blank=True)
    history = HistoricalRecords()

    def __str__(self):
        return self.ticker

'''
# just messing around down here. Note that this should totally be done in java spring boot when im ready to implement
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
