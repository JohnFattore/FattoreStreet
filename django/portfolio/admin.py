from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Asset

@admin.register(Asset)
class AssetAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'shares', 'buy_date', 'sell_date', 'user')
    history_list_display = ('id', 'ticker', 'shares', 'buy_date', 'sell_date', 'user')

    def asset_info_ticker(self, obj):
        return obj.ticker