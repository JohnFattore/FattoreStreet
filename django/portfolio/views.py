# only API views, see retiredViews for old django frontend views
# API modules using drf
from rest_framework import permissions, generics, serializers, views, response
from .serializers import AssetSerializer, SnP500PriceSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price, AssetInfo
from .tasks import updateCostBasis
import yfinance as yf
from datetime import datetime, timedelta, date
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
import environ
import requests
from django.core.cache import cache
from decimal import Decimal
from .choices import ASSET_TYPES, EXCHANGES, MARKETS

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
        return Asset.objects.filter(user=self.request.user).select_related("snp500_buy_date", "snp500_sell_date") 

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        buy_date = self.request.data["buy_date"]
        ticker = self.request.data["ticker"]
        try:
            snp500_buy_date = SnP500Price.objects.get(date=buy_date)
        except:
            raise serializers.ValidationError({"detail": "Stock market was closed that day."})
        try:
            asset_info = AssetInfo.objects.get(ticker=ticker)

        except:
            yfinance = yf.Ticker(ticker)
            data = yfinance.info     

            market = data["market"]
            if market not in {m[0] for m in MARKETS}:
                raise Exception(f"Market {market} not recognized")

            type = data["quoteType"]
            if type not in {t[0] for t in ASSET_TYPES}:
                raise Exception(f"type {type} not recognized")

            exchange = data["fullExchangeName"]

            if exchange in {"NasdaqGS", "NasdaqGM", "NasdaqCM"}:
                exchange = "NASDAQ"
            elif exchange in {"NYSEArca"}:
                exchange = "NYSE"

            if exchange not in {e[0] for e in EXCHANGES}:
                raise Exception(f"exchange {exchange} not recognized")        
            asset_info = AssetInfo.objects.create(ticker=ticker,
                                                short_name=data["shortName"],
                                                long_name=data["longName"],
                                                type=type,
                                                market=market,
                                                exchange=exchange)                        
#        except:
#            raise Exception(f"an error has occured processing {ticker}")        


        yfinance = yf.Ticker(self.request.data["ticker"])
        data = yfinance.history(start=buy_date, end=get_next_day(buy_date))
        cost_basis = data['Close'].get(buy_date, None) * self.request.data["shares"]
        serializer.save(user=self.request.user, asset_info=asset_info, snp500_buy_date=snp500_buy_date, cost_basis=cost_basis)

# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Asset.objects.all().select_related("snp500_buy_date", "snp500_sell_date")
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]

class AssetUpdateSellDateView(generics.UpdateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]

    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user)

    def perform_update(self, serializer):
        sell_date = self.request.data.get("sell_date")
        if not sell_date:
            raise serializers.ValidationError({"sell_date": "This field is required for updating."})

        try:
            snp500_sell_date = SnP500Price.objects.get(date=sell_date)
            yfinance = yf.Ticker(serializer.instance.asset_info.ticker)
            end_date = datetime.strptime(sell_date, "%Y-%m-%d").date() + timedelta(days=1)
            data = yfinance.history(start=sell_date, end=end_date.strftime("%Y-%m-%d"))
            sell_price = serializer.instance.shares * Decimal(data['Close'][sell_date])

        except SnP500Price.DoesNotExist:
            raise serializers.ValidationError({"sell_date": "Stock market was closed that day."})

        # might not have to explicitly save sell_date
        serializer.save(sell_date=sell_date, sell_price=sell_price, snp500_sell_date=snp500_sell_date)

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
            return Response(cached_data)  # Return cached response
        
        api_key = env("FINNHUB_API_KEY")
        url = f"https://finnhub.io/api/v1/quote?symbol={symbol}&token={api_key}"
        response = requests.get(url)
        data = response.json()
        cache.set(cache_key, data, timeout=60 * 5)
        return Response(data)