from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Stock, IndexMember

@admin.register(Stock)
class StockAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap')

@admin.register(IndexMember)
class IndexMemberAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'stock', 'percent', 'index', 'outlier', 'notes')