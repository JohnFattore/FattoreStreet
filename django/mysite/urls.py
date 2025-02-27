from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path('users/', include('users.urls')),
    path('portfolio/', include('portfolio.urls')),
    path("indexes/", include("indexes.urls")),
    path("restaurants/", include("restaurants.urls")),
    path("chatbot/", include("chatbot.urls")),
    path('admin/', admin.site.urls),
]