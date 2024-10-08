from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Asset, SnP500Price

admin.site.register(Asset)


@admin.register(SnP500Price)
class SnP500PriceAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'date', 'price')
    history_list_display = ('id', 'date', 'price')