from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone


class Conversation(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='conversations')
    title = models.CharField(max_length=100)
    created_at = models.DateTimeField(auto_now_add=True)
    # Bumped on each new message, not on rename, so the list orders by real activity
    last_message_at = models.DateTimeField(default=timezone.now)

    class Meta:
        ordering = ['-last_message_at']

    def __str__(self):
        return f"{self.user.username} - {self.title}"


# No HistoricalRecords here: deleting a conversation must actually remove it
class Interaction(models.Model):
    conversation = models.ForeignKey(Conversation, on_delete=models.CASCADE, related_name='interactions')
    input_text = models.TextField()
    output_text = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['timestamp', 'id']

    def __str__(self):
        return f"{self.conversation} - {self.timestamp}"


class AdvisorUsage(models.Model):
    """Bedrock tokens spent per calendar month (UTC), summed over every user.

    This is the spend cap's ledger. It is kept apart from Interaction, so deleting a
    conversation cannot reset it, and it also counts calls whose reply was then discarded.
    """
    month = models.DateField(unique=True, help_text="First day of the month")
    input_tokens = models.BigIntegerField(default=0)
    output_tokens = models.BigIntegerField(default=0)

    class Meta:
        ordering = ['-month']
        verbose_name_plural = 'advisor usage'

    @property
    def total_tokens(self) -> int:
        return self.input_tokens + self.output_tokens

    def __str__(self):
        return f"{self.month:%Y-%m}: {self.total_tokens:,} tokens"
