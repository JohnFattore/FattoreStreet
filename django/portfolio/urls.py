from django.urls import path
from . import views

urlpatterns = [
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/asset/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/snp500-price/', views.SnP500RetrieveView.as_view(), name='snp500-price'),
    path('api/snp500-price-create/', views.SnP500PriceCreateView.as_view(), name='snp500-price-create'),
    path('api/update-cost-basis/', views.UpdateCostBasis.as_view(), name='update-cost-basis'),
]