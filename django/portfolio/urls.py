from django.urls import path
from . import views
from . import admin_views

urlpatterns = [
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/asset/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/update-sell-date/<int:pk>/', views.AssetUpdateSellDateView.as_view(), name="update-sell-date"),
    path('api/snp500-price/', views.SnP500RetrieveView.as_view(), name='snp500-price'),
    path('api/quote/', views.QuoteRetrieveView.as_view(), name='quote'),
    # admin views
    path('api/snp500-price-create/', admin_views.SnP500PriceCreateView.as_view(), name='snp500-price-create'),
    path('api/update-cost-basis/', admin_views.UpdateCostBasis.as_view(), name='update-cost-basis'),
    path('api/asset-info-create/', admin_views.CreateAssetInfo.as_view(), name='asset-info-create'),
    path('api/asset-update-info/', admin_views.UpdateAssetWithInfo.as_view(), name='asset-update-info'),
]