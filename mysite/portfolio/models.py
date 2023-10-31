from django.db import models
from django.contrib.auth.models import User

class Asset(models.Model):
    ticker_string = models.CharField(max_length=5)
    shares_number = models.DecimalField(decimal_places=5, max_digits=10)
    costbasis_number = models.DecimalField(decimal_places=2, max_digits=10)
    buy_date = models.DateField('date bought')
    account_string = models.CharField(max_length=100)
    user_key = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.ticker_string

# remove unused models
class UserInfo(models.Model):
    salary = models.DecimalField(decimal_places=0, max_digits=15)
    # account choices
    Roth_IRA = "RIRA"
    Trad_IRA = "TIRA"
    Retire401k = "401k"
    Taxable = "TAXD"
    ACCOUNT_CHOICES = [
        (Roth_IRA, "Roth IRA"),
        (Trad_IRA, "Trad IRA"),
        (Retire401k, "401k"),
        (Taxable, "Taxable"),
    ]
    account1 = models.CharField(
        max_length=4,
        choices=ACCOUNT_CHOICES,
        default=Taxable,
    )
    account2 = models.CharField(
        max_length=4,
        choices=ACCOUNT_CHOICES,
        default=Taxable,
    )    
    account3 = models.CharField(
        max_length=4,
        choices=ACCOUNT_CHOICES,
        default=Taxable,
    )