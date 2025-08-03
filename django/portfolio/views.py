from rest_framework import generics, serializers
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from .serializers import AssetSerializer
from .permissions import IsOwner
from .models import Asset
import yfinance as yf
from datetime import datetime
import environ
import requests
from django.core.cache import cache
from .helper import get_ticker_price
from .choices import ASSET_TYPES, EXCHANGES, MARKETS

env = environ.Env()
environ.Env.read_env()

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsAuthenticated, IsOwner]
    # return only the assets the user owns
    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user)

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        ticker = self.request.data["ticker"]        
        shares = self.request.data["shares"]
        buy_date = datetime.strptime(self.request.data["buy_date"], "%Y-%m-%d").date()
        # should just check some list of stock market open / closed
        try:
            get_ticker_price(ticker, buy_date)
        except:
            raise serializers.ValidationError({"detail": f"Market closed on {buy_date}"})           

        serializer.save(user=self.request.user, ticker=ticker, shares=shares, buy_date=buy_date)

# API endpoint for 'get' or 'delete' or "patch" asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveUpdateDestroyAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsAuthenticated, IsOwner]

    def get_queryset(self):     
        return Asset.objects.filter(user=self.request.user)

class QuoteRetrieveView(APIView):
    def get(self, request):
        symbol = request.query_params.get("symbol")
        if (symbol == None):
            raise serializers.ValidationError({"symbol": "This field is required."})
        data = get_ticker_price(symbol)
        return Response(data)

class AssetInfoRetrieveView(APIView):
    def get(self, request):
        tickers = request.query_params.get("tickers")
        if (tickers == None):
            raise serializers.ValidationError({"tickers": "This field is required."})
        ticker_list = tickers.split(",")
        if len(ticker_list) == 1 and ticker_list[0] == "":
            raise serializers.ValidationError({"tickers": "This field must contain at least 1 ticker."})
        data = []
        for ticker in ticker_list:
            try:
                quote = get_ticker_price(ticker)
                cache_key = f"financials_{ticker}"
                cached_data = cache.get(cache_key)

                if cached_data:
                    cached_data["current_price"] = quote["price"]
                    cached_data["percent_change_daily"] = quote["percent_change"]
                    data.append(cached_data)
                else:
                    yfinance = yf.Ticker(ticker)  # Use your stock ticker here
                    market = yfinance.info["market"]
                    if market not in {m[0] for m in MARKETS}:
                        raise Exception(f"Market {market} not recognized")

                    type = yfinance.info["quoteType"]
                    if type not in {t[0] for t in ASSET_TYPES}:
                        raise Exception(f"type {type} not recognized")

                    exchange = yfinance.info["fullExchangeName"]

                    if exchange in {"NasdaqGS", "NasdaqGM", "NasdaqCM"}:
                        exchange = "NASDAQ"
                    elif exchange in {"NYSEArca"}:
                        exchange = "NYSE"

                    if exchange not in {e[0] for e in EXCHANGES}:
                        raise Exception(f"exchange {exchange} not recognized")
                    
                    financials = {
                        "ticker": ticker,
                        "current_price": quote["price"],
                        "percent_change_daily": quote["percent_change"],
                        "short_name": yfinance.info["shortName"],
                        "long_name": yfinance.info["longName"],
                        "type": type,
                        "market": market,
                        "exchange": exchange
                    }

                    if yfinance.info["quoteType"] == "EQUITY":
                        financials["market_cap"] = yfinance.info["marketCap"]
                        financials["net_income"] = yfinance.quarterly_financials.loc["Net Income"].iloc[:4].sum()
                        financials["total_revenue"] = yfinance.quarterly_financials.loc["Total Revenue"].iloc[:4].sum()

                        cache.set(cache_key, financials, timeout=60 * 60 * 24)
                        data.append(financials)
                    elif yfinance.info["quoteType"] == "ETF":
                        financials["market_cap"] = 0
                        financials["ttm_pe"] = 0
                        if yfinance.info.get("marketCap") != None:
                            financials["market_cap"] = yfinance.info["marketCap"]
                        financials["expenseRatio"] = yfinance.info["netExpenseRatio"] / 100
                        if yfinance.info.get("ttm_pe") != None:
                            financials["ttm_pe"] = yfinance.info["trailingPE"]

                        cache.set(cache_key, financials, timeout=60 * 60 * 24)
                        data.append(financials)
                    else:
                        raise serializers.ValidationError({"tickers": "unrecognized ticker"})
            except:
                continue        
        return Response(data)