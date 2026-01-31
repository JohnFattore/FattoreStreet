from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Interaction

@admin.register(Interaction)
class InteractionAdmin(SimpleHistoryAdmin):
    list_display = ('user', 'timestamp', 'input_text')
