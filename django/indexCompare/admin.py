from django.contrib import admin
from .models import Stock, IndexMember

@admin.register(Stock)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'name', 'marketCap', 'countryIncorp', 'countryHQ', 'securityType')

@admin.register(IndexMember)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'percent', 'index')