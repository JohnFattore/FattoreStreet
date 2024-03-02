from django.urls import path
from . import views

urlpatterns = [
    path('api/options/', views.OptionListCreateView.as_view(), name="options"),
    path('api/selections/', views.SelectionListCreateView.as_view(), name="selections"),
    path('api/selection/<int:pk>/', views.SelectionRetrieveDestroyView.as_view(), name="selection"),

]