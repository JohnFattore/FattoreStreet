from django.urls import path
from . import views

urlpatterns = [
    path('api/options/', views.OptionListAPI.as_view(), name="optionList"),
    path('api/selections/', views.SelectionListCreateAPI.as_view(), name="selectionListCreate"),
    path('api/selection/<int:pk>/', views.SelectionRetrieveDestroyAPI.as_view(), name="selectionRetrieveDestroy"),
    path('api/results/', views.ResultListAPI.as_view(), name="resultList"),
    path('api/result/<int:pk>/', views.ResultRetrieveAPI.as_view(), name="resultRetrieve"),
]