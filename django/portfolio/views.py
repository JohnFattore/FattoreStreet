# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
# API modules using drf
from rest_framework import permissions, generics
from .serializers import AssetSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price
from .tasks import SnP500PriceUpdate, ReinvestSnP500Dividends
from rest_framework.response import Response
from rest_framework import status
from rest_framework.views import APIView
import yfinance as yf
from datetime import datetime, timedelta, date

def get_next_day(date_str, date_format="%Y-%m-%d"):
    date_obj = datetime.strptime(date_str, date_format)
    next_day = date_obj + timedelta(days=1)
    return next_day.strftime(date_format)

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the assets the user owns
    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user).select_related("SnP500Price")

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        buyDate = self.request.data["buyDate"]
        SnP = SnP500Price.objects.get(date=buyDate)
        yfinance = yf.Ticker(self.request.data["ticker"])
        data = yfinance.history(start=buyDate, end=get_next_day(buyDate))
        serializer.save(user=self.request.user, SnP500Price=SnP, costbasis=data['Close'].get(buyDate, None))

# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Asset.objects.all().select_related("SnP500Price")
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]

class UpdateSnP500PriceView(APIView):
    def post(self, request):
        # Trigger the Celery task
        task = SnP500PriceUpdate.delay()
        return Response({"task_id": task.id}, status=status.HTTP_202_ACCEPTED)
    
class ReinvestSnP500DividendsView(APIView):
    def post(self, request):
        # Trigger the Celery task
        task = ReinvestSnP500Dividends.delay()
        return Response({"task_id": task.id}, status=status.HTTP_202_ACCEPTED)
    
class ReinvestDividendsView(generics.RetrieveUpdateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    def perform_update(self, serializer):
        asset = serializer.instance
        yfinance = yf.Ticker(asset.ticker)
        data = yfinance.history(start=asset.buyDate.strftime("%Y-%m-%d"), end=date.today().strftime("%Y-%m-%d"))
        dividends = yfinance.dividends
        currentShares = 1
        for dividend_date, dividend in dividends.items():
            price = data["Close"][dividend_date.strftime("%Y-%m-%d")]
            newShares = ((dividend * currentShares) / price)
            currentShares = currentShares + newShares
        asset.shares = currentShares
        serializer.save()