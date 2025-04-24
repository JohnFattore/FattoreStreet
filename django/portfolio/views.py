# only API views, see retiredViews for old django frontend views
# API modules using drf
from rest_framework import generics, serializers
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from .serializers import AssetSerializer, SnP500PriceSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price, AssetInfo
import yfinance as yf
from datetime import datetime, timedelta, date
import environ
import requests
from django.core.cache import cache
from decimal import Decimal
from .helper import get_or_create_SnP500Price, get_or_create_AssetInfo, get_ticker_price

env = environ.Env()
environ.Env.read_env()

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsAuthenticated, IsOwner]
    # return only the assets the user owns
    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user).select_related("snp500_buy_date", "snp500_sell_date") 

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        buy_date = datetime.strptime(self.request.data["buy_date"], "%Y-%m-%d").date()
        ticker = self.request.data["ticker"]
        try:
            snp500_buy_date = get_or_create_SnP500Price(buy_date)  
        except Exception as e:
            if str(e) == "Error getting S&P 500 Price":
                print(str(e))
            else:
                raise serializers.ValidationError({"detail": "Stock market was closed that day."})
        
        try:
            asset_info = get_or_create_AssetInfo(ticker=ticker)
        except:
            raise serializers.ValidationError({"detail": "Ticker doesn't exist."})           

        cost_basis_per_share = get_ticker_price(ticker, buy_date)
        cost_basis = cost_basis_per_share * self.request.data["shares"]
        serializer.save(user=self.request.user, asset_info=asset_info, snp500_buy_date=snp500_buy_date, cost_basis=cost_basis)

# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Asset.objects.all().select_related("snp500_buy_date", "snp500_sell_date")
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]

class AssetUpdateSellDateView(generics.UpdateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsAuthenticated, IsOwner]

    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user)

    def perform_update(self, serializer):
        sell_date = self.request.data.get("sell_date")
        if not sell_date:
            raise serializers.ValidationError({"sell_date": "This field is required for updating."})

        try:
            ticker = serializer.instance.asset_info.ticker
            sell_date = datetime.strptime(self.request.data["sell_date"], "%Y-%m-%d").date()
            snp500_sell_date = get_or_create_SnP500Price(sell_date)
            price_per_share = get_ticker_price(ticker, sell_date)
            sell_price = serializer.instance.shares * price_per_share

        except SnP500Price.DoesNotExist:
            raise serializers.ValidationError({"sell_date": "Stock market was closed that day."})

        # might not have to explicitly save sell_date
        serializer.save(sell_date=sell_date, sell_price=sell_price, snp500_sell_date=snp500_sell_date)

# API endpoint to get specific SnP500 prices / dates
class SnP500RetrieveView(generics.RetrieveAPIView):
    queryset = SnP500Price.objects.all()
    serializer_class = SnP500PriceSerializer
    def get_object(self):
        date = datetime.strptime(self.request.query_params.get("date"), "%Y-%m-%d").date()
        return get_or_create_SnP500Price(date)

class QuoteRetrieveView(APIView):
    def get(self, request):
        symbol = request.query_params.get("symbol")
        if (symbol == None):
            raise serializers.ValidationError({"symbol": "This field is required."})
        cache_key = f"finnhub_quote_{symbol}"
        cached_data = cache.get(cache_key)

        if cached_data:
            return Response(cached_data)
        
        api_key = env("FINNHUB_API_KEY")
        url = f"https://finnhub.io/api/v1/quote?symbol={symbol}&token={api_key}"
        response = requests.get(url)
        data = response.json()
        cache.set(cache_key, data, timeout=60 * 5)
        return Response(data)
    
class FinancialsRetrieveView(APIView):
    def get(self, request):
        symbol = request.query_params.get("symbol")
        if (symbol == None):
            raise serializers.ValidationError({"symbol": "This field is required."})
        cache_key = f"finnhub_financials_{symbol}"
        cached_data = cache.get(cache_key)

        if cached_data:
            return Response(cached_data)
        
        api_key = env("FINNHUB_API_KEY")
        url = f"https://finnhub.io/api/v1/stock/financials-reported?symbol={symbol}&freq=quarterly&token={api_key}"
        response = requests.get(url)
        data = response.json()
        cache.set(cache_key, data, timeout=60 * 5)
        return Response(data)