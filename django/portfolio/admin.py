from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Asset, SnP500Price, AssetInfo

@admin.register(Asset)
class AssetAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'asset_info_ticker', 'shares', 'cost_basis', 'buy_date', 'user')
    history_list_display = ('id', 'asset_info_ticker', 'shares', 'cost_basis', 'buy_date','user')

    def asset_info_ticker(self, obj):
        return obj.asset_info.ticker
    
@admin.register(AssetInfo)
class AssetAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'short_name', 'long_name', 'type', 'exchange', 'market')
    history_list_display = ('id', 'ticker', 'short_name', 'long_name', 'type', 'exchange', 'market')

    
@admin.register(SnP500Price)
class SnP500PriceAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'date', 'price')
    history_list_display = ('id', 'date', 'price')