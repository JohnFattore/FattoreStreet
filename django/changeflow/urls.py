from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import ChangeRequestViewSet, TicketCreateView

router = DefaultRouter()
router.register(r'requests', ChangeRequestViewSet)

urlpatterns = [
    path('api/', include(router.urls)),
    path("api/tickets/", TicketCreateView.as_view(), name="tickets"),
]
