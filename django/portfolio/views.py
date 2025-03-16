# only API views, see retiredViews for old django frontend views
# API modules using drf
from rest_framework import permissions, generics, serializers, views, response
from .serializers import AssetSerializer, SnP500PriceSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price
from .tasks import updateCostBasis
import yfinance as yf
from datetime import datetime, timedelta, date
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
import environ
import requests
from django.core.cache import cache

env = environ.Env()
environ.Env.read_env()

def get_next_day(date_str, date_format="%Y-%m-%d"):
    date_obj = datetime.strptime(date_str, date_format)
    next_day = date_obj + timedelta(days=1)
    return next_day.strftime(date_format)

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]
    # return only the assets the user owns
    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user).select_related("snp500_buy_date") 

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        buy_date = self.request.data["buy_date"]
        try:
            SnP = SnP500Price.objects.get(date=buy_date)
        except:
            raise serializers.ValidationError({"detail": "Stock market was closed that day."})
        yfinance = yf.Ticker(self.request.data["ticker"])
        data = yfinance.history(start=buy_date, end=get_next_day(buy_date))
        serializer.save(user=self.request.user, snp500_buy_date=SnP, cost_basis=data['Close'].get(buy_date, None)) 

# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Asset.objects.all().select_related("snp500_buy_date")
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]

# API endpoint to get specific SnP500 prices / dates
class SnP500RetrieveView(generics.RetrieveAPIView):
    queryset = SnP500Price.objects.all()
    serializer_class = SnP500PriceSerializer
    def get_object(self):
        try:
            return SnP500Price.objects.get(date=self.request.query_params.get("date"))
        except SnP500Price.DoesNotExist:
            raise serializers.ValidationError({"detail": "No record found for the given date."})

class QuoteRetrieveView(APIView):
    def get(self, request, *args, **kwargs):
        symbol = request.query_params.get("symbol", "AAPL")
        cache_key = f"finnhub_{symbol}"  # Unique cache key per stock symbol
        cached_data = cache.get(cache_key)  # Check Redis cache

        if cached_data:
            print("used cached data!")
            return Response(cached_data)  # Return cached response
        
        api_key = env("FINNHUB_API_KEY")
        url = f"https://finnhub.io/api/v1/quote?symbol={symbol}&token={api_key}"
        response = requests.get(url)
        data = response.json()
        cache.set(cache_key, data, timeout=60 * 5)  # Cache for 5 minutes
        return Response(data)

def daterange(start_date, end_date):
    """Helper function to iterate over a range of dates."""
    for n in range((end_date - start_date).days + 1):
        yield start_date + timedelta(n)

# shouldnt need, part of celery beat task now
class SnP500PriceCreateView(APIView):
    def post(self, request):
        yfinance = yf.Ticker('SPY')
        start_date = date(2005, 1, 1)
        end_date = date(2025, 12, 31)
        data = yfinance.history(start=start_date.strftime("%Y-%m-%d"), end=end_date.strftime("%Y-%m-%d"))
        queryset = SnP500Price.objects.all()

        for single_date in daterange(start_date, end_date):
            if not queryset.filter(date=single_date).exists():
                try:
                    SnP500Price.objects.create(date=single_date, price=data['Close'][single_date.strftime("%Y-%m-%d")])
                except:
                    print(single_date.strftime("%Y-%m-%d") + " has no value")
            else:
                print("Date already exists")
        return Response({"message": "S&P 500 prices populated successfully!"}, status=status.HTTP_200_OK)

# shouldn't need, part of celery beat task now 
class UpdateCostBasis(views.APIView):
    def post(self, request):
        # Trigger the Celery task
        task = updateCostBasis.delay()  # Asynchronously starts the task
        return response.Response(
            {
                "message": "update cost basis task has been initiated.",
                "task_id": task.id,  # Return the Celery task ID
            },
            status=status.HTTP_202_ACCEPTED
        )