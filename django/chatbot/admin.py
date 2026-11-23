from django.contrib import admin
from .models import AdvisorUsage, ComparisonAnalysisRequest, Conversation, Interaction

INTERACTION_READONLY_FIELDS = ('timestamp', 'model_id', 'input_tokens', 'output_tokens')


class InteractionInline(admin.TabularInline):
    model = Interaction
    extra = 0
    readonly_fields = INTERACTION_READONLY_FIELDS

    def has_add_permission(self, request, obj=None):
        return False


@admin.register(Conversation)
class ConversationAdmin(admin.ModelAdmin):
    list_display = ('user', 'title', 'last_message_at')
    inlines = [InteractionInline]


@admin.register(Interaction)
class InteractionAdmin(admin.ModelAdmin):
    list_display = ('conversation', 'model_id', 'input_tokens', 'output_tokens', 'timestamp')
    list_filter = ('model_id',)
    list_select_related = ('conversation', 'conversation__user')
    readonly_fields = INTERACTION_READONLY_FIELDS

    def has_add_permission(self, request):
        return False


# Lowering a month's totals here lifts the spend cap early, if you decide the spike was fine
@admin.register(AdvisorUsage)
class AdvisorUsageAdmin(admin.ModelAdmin):
    list_display = ('month', 'input_tokens', 'output_tokens', 'total_tokens')


@admin.register(ComparisonAnalysisRequest)
class ComparisonAnalysisRequestAdmin(admin.ModelAdmin):
    list_display = ('requested_at', 'user', 'ticker_a', 'ticker_b')
    list_filter = ('user',)
    search_fields = ('user__username', 'ticker_a', 'ticker_b')
