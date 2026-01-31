from django.db import models
from django.contrib.auth.models import User
from simple_history.models import HistoricalRecords

class Interaction(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    input_text = models.TextField()
    output_text = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)
    history = HistoricalRecords()

    def __str__(self):
        return f"{self.user.username} - {self.timestamp}"
