from django.contrib import admin
from .models import Conversation, Interaction

class InteractionInline(admin.TabularInline):
    model = Interaction
    extra = 0

@admin.register(Conversation)
class ConversationAdmin(admin.ModelAdmin):
    list_display = ('user', 'title', 'last_message_at')
    inlines = [InteractionInline]
