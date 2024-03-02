from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone
import datetime

# You select an option... model naming is subject to change although i think this is a good start
class Option(models.Model):
    ticker = models.CharField(max_length=200)
    sunday = models.DateField()
    def __str__(self):
        return self.ticker
    # probably unnessesary
    #def currentWeek(self):
    #    return self.sunday >= timezone.now() - datetime.timedelta(days=7)

class Selection(models.Model):
    option = models.ForeignKey(Option, on_delete=models.CASCADE)
    sunday = models.DateField()
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    def __str__(self):
        return self.option.ticker