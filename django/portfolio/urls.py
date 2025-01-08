from django.urls import path
from . import views

urlpatterns = [
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/asset/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/update-snp500-price/', views.UpdateSnP500PriceView.as_view(), name='update-snp500-price'),
    path('api/reinvest-snp500-dividends/', views.ReinvestSnP500DividendsView.as_view(), name='reinvest-snp500-dividends'),
    path('api/reinvest-dividends/<int:pk>/', views.ReinvestDividendsView.as_view(), name='reinvest-dividends'),
]