from django.contrib import admin
from .models import AdvisorUsage, Conversation, Interaction

class InteractionInline(admin.TabularInline):
    model = Interaction
    extra = 0

@admin.register(Conversation)
class ConversationAdmin(admin.ModelAdmin):
    list_display = ('user', 'title', 'last_message_at')
    inlines = [InteractionInline]


# Lowering a month's totals here lifts the spend cap early, if you decide the spike was fine
@admin.register(AdvisorUsage)
class AdvisorUsageAdmin(admin.ModelAdmin):
    list_display = ('month', 'input_tokens', 'output_tokens', 'total_tokens')
