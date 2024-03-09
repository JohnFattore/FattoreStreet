from django.contrib import admin
from .models import Option, Selection

# Register your models here.
# admin.site.register(Option)
@admin.register(Option)
class SelectionAdmin(admin.ModelAdmin):
    list_display = ('ticker', 'sunday', 'id')
# admin.site.register(Selection)
@admin.register(Selection)
class SelectionAdmin(admin.ModelAdmin):
    list_display = ('option', 'user', 'id')