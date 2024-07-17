from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path('portfolio/', include('portfolio.urls')),
    path("wallstreet/", include("wallstreet.urls")),
    path("index_compare/", include("indexCompare.urls")),
    path('admin/', admin.site.urls),
]