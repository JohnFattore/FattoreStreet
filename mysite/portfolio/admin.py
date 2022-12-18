from django.contrib import admin

from .models import Stock, ETF

admin.site.register(Stock)
admin.site.register(ETF)