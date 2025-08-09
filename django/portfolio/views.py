from rest_framework import generics, serializers
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from .serializers import AssetSerializer
from .permissions import IsOwner
from .models import Asset
import yfinance as yf
from datetime import datetime, timedelta
import environ
import requests
from django.core.cache import cache
from .helper import get_ticker_price, get_market_reference_dates, is_market_open
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
        if not is_market_open(buy_date):
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
        dates = get_market_reference_dates()
        data = []
        errors = []
        for ticker in ticker_list:
            try:
                quote = get_ticker_price(ticker)
                cache_key = f"financials_{ticker}"
                cached_data = cache.get(cache_key)

                if cached_data:
                    cached_data["current_price"] = quote["price"]
                    cached_data["percent_change_daily"] = quote["percent_change"]
                    data.append(cached_data)
                if False:
                    print("what")
                else:
                    yfinance = yf.Ticker(ticker)
                    info = yfinance.info
                    market = info["market"]
                    if market not in {m[0] for m in MARKETS}:
                        raise Exception(f"Market {market} not recognized")

                    type = info["quoteType"]
                    if type not in {t[0] for t in ASSET_TYPES}:
                        raise Exception(f"type {type} not recognized")

                    exchange = info["fullExchangeName"]

                    if exchange in {"NasdaqGS", "NasdaqGM", "NasdaqCM"}:
                        exchange = "NASDAQ"
                    elif exchange in {"NYSEArca"}:
                        exchange = "NYSE"

                    if exchange not in {e[0] for e in EXCHANGES}:
                        raise Exception(f"exchange {exchange} not recognized")
                    historical_prices = {}
                    for label, date in dates.items():
                        try:
                            price = get_ticker_price(ticker, date)["price"]
                        except:
                            price = 0
                        historical_prices[label] = price
                    financials = {
                        "ticker": ticker,
                        "current_price": quote["price"],
                        "percent_change_daily": quote["percent_change"],
                        "percent_change_weekly": (quote["price"] - historical_prices["1_week_ago"]) / historical_prices["1_week_ago"],
                        "percent_change_monthly": (quote["price"] - historical_prices["1_month_ago"]) / historical_prices["1_month_ago"],
                        "percent_change_YTD": (quote["price"] - historical_prices["year_to_date"]) / historical_prices["year_to_date"],
                        "percent_change_yearly": (quote["price"] - historical_prices["1_year_ago"]) / historical_prices["1_year_ago"],
                        "percent_change_3_years": (quote["price"] - historical_prices["3_years_ago"]) / historical_prices["3_years_ago"],
                        "percent_change_5_years": (quote["price"] - historical_prices["5_years_ago"]) / historical_prices["5_years_ago"],
                        # dividend yield, forward PE
                        "short_name": info["shortName"],
                        "long_name": info["longName"],
                        "type": type,
                        "market": market,
                        "exchange": exchange
                    }

                    if info["quoteType"] == "EQUITY":
                        quarterly_financials = yfinance.quarterly_financials
                        financials["market_cap"] = info["marketCap"]
                        financials["net_income"] = quarterly_financials.loc["Net Income"].iloc[:4].sum()
                        financials["total_revenue"] = quarterly_financials.loc["Total Revenue"].iloc[:4].sum()

                        cache.set(cache_key, financials, timeout=60 * 60 * 24)
                        data.append(financials)
                    elif info["quoteType"] == "ETF":
                        financials["market_cap"] = 0
                        financials["ttm_pe"] = 0
                        if info.get("marketCap") != None:
                            financials["market_cap"] = info["marketCap"]
                        financials["expenseRatio"] = info["netExpenseRatio"] / 100
                        if info.get("ttm_pe") != None:
                            financials["ttm_pe"] = info["trailingPE"]

                        cache.set(cache_key, financials, timeout=60 * 60 * 24)
                        data.append(financials)
                    else:
                        raise serializers.ValidationError({"tickers": "unrecognized ticker"})
            except:
                errors.append(ticker)
                # make this error better
                continue        
        return Response({"data": data, "errors": errors})