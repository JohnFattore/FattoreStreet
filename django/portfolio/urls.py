from django.urls import path
from . import views

urlpatterns = [
    path('api/accounts/', views.AccountListCreateView.as_view(), name='accounts'),
    path('api/accounts/<int:pk>/', views.AccountRetrieveDestroyView.as_view(), name='account'),
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/assets/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/asset-info/', views.AssetInfoRetrieveView.as_view(), name='asset-info'),
    path('api/asset-prices/', views.AssetHistoricalPricesRetrieveView.as_view(), name='asset-prices'),
    path('api/quote/', views.QuoteRetrieveView.as_view(), name='quote'),
    path('api/quarterly-data/', views.QuarterlyDataRetrieveView.as_view(), name='quarterly-data'),
    path('api/fred-data/', views.FredDataRetrieveView.as_view(), name='fred-data')
]