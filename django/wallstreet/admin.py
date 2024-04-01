from django.contrib import admin
from .models import Option, Selection, Result, AltBenchmark

@admin.register(Option)
class OptionAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'sunday', 'startPrice', 'endPrice', 'percentChange', 'rank', 'benchmark', 'id')

@admin.register(Selection)
class SelectionAdmin(admin.ModelAdmin):
    list_display = ('option', 'allocation', 'user', 'id')

@admin.register(Result)
class ResultAdmin(admin.ModelAdmin):
    list_display = ('portfolioPercentChange', 'sunday', 'user', 'id')

@admin.register(AltBenchmark)
class AltBenchmarksAdmin(admin.ModelAdmin):
    list_display = ('benchmark', 'percentChange', 'sunday', 'id')