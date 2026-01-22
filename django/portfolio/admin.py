from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Asset, Account

@admin.register(Account)
class AccountAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'name', 'account_type', 'user')
    history_list_display = ('id', 'name', 'account_type', 'user')

@admin.register(Asset)
class AssetAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'shares', 'buy_date', 'sell_date', 'user', 'account')
    history_list_display = ('id', 'ticker', 'shares', 'buy_date', 'sell_date', 'user', 'account')

    def asset_info_ticker(self, obj):
        return obj.ticker