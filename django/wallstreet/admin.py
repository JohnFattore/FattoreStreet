from django.contrib import admin
from .models import Option, Selection

# Register your models here.
# admin.site.register(Option)
@admin.register(Option)
class OptionAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'sunday', 'startPrice', 'endPrice', 'rank', 'id')
# admin.site.register(Selection)
@admin.register(Selection)
class SelectionAdmin(admin.ModelAdmin):
    list_display = ('option', 'user', 'id')