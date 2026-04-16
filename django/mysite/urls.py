from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path('users/', include('users.urls')),
    path('portfolio/', include('portfolio.urls')),
    path("restaurants/", include("restaurants.urls")),
    path("chatbot/", include("chatbot.urls")),
    path("changeflow/", include("changeflow.urls")),
    path("blog/", include("blog.urls")),
    path("entertainment/", include("entertainment.urls")),
    path('admin/', admin.site.urls),
]
