from django.db import models
from django.contrib.auth.models import User

class Asset(models.Model):
    # need authentication in the backend, that might start here and extend to the views.... im not sure
    ticker = models.CharField(max_length=5)
    shares = models.DecimalField(decimal_places=5, max_digits=25)
    costbasis = models.DecimalField(decimal_places=2, max_digits=10)
    buy = models.DateField('date bought')
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.ticker