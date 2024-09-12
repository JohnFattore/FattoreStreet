from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import Stock, IndexMember, Outlier

@admin.register(Stock)
class StockAdmin(SimpleHistoryAdmin):
    list_display = ('ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap')

@admin.register(IndexMember)
class IndexMemberAdmin(SimpleHistoryAdmin):
    list_display = ('ticker', 'percent', 'index')

@admin.register(Outlier)
class OutlierAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'yearIPO', 'notes')
    history_list_display = ('id', 'ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'yearIPO', 'notes')