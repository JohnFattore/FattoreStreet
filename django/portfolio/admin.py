from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Asset, SnP500Price

@admin.register(Asset)
class AssetAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'shares', 'costbasis', 'buyDate', 'dividends', 'reinvestShares', 'user')
    history_list_display = ('id', 'ticker', 'shares', 'costbasis', 'buyDate', 'dividends', 'reinvestShares', 'user')

@admin.register(SnP500Price)
class SnP500PriceAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'date', 'price', 'dividends', 'reinvestShares')
    history_list_display = ('id', 'date', 'price', 'dividends', 'reinvestShares')