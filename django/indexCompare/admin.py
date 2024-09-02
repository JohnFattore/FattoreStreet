from django.contrib import admin
from .models import Stock, IndexMember, Outlier

@admin.register(Stock)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap')

@admin.register(IndexMember)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'percent', 'index')

@admin.register(Outlier)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('id', 'ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'yearIPO', 'notes')