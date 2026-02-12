from rest_framework import generics, serializers
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from .serializers import AssetSerializer, AccountSerializer, FredSeriesItemSerializer
from .permissions import IsOwner
from .models import Asset, Account
from datetime import datetime
import environ
from .helper import get_realtime_price, get_yfinance_data, is_market_open, get_fred_data, percent_change, get_historical_prices, get_market_reference_dates
env = environ.Env()
environ.Env.read_env()

class AccountListCreateView(generics.ListCreateAPIView):
    queryset = Account.objects.all()
    serializer_class = AccountSerializer
    permission_classes = [IsAuthenticated, IsOwner]

    def get_queryset(self):
        return Account.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

class AccountRetrieveDestroyView(generics.RetrieveUpdateDestroyAPIView):
    queryset = Account.objects.all()
    serializer_class = AccountSerializer
    permission_classes = [IsAuthenticated, IsOwner]

    def get_queryset(self):
        return Account.objects.filter(user=self.request.user)

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [IsAuthenticated, IsOwner]
    # return only the assets the user owns
    def get_queryset(self):
        queryset = Asset.objects.filter(user=self.request.user)
        account_id = self.request.query_params.get('account_id')
        if account_id:
            queryset = queryset.filter(account_id=account_id)
        return queryset

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        ticker = self.request.data["ticker"]        
        shares = self.request.data["shares"]
        buy_date = datetime.strptime(self.request.data["buy_date"], "%Y-%m-%d").date()
        # should just check some list of stock market open / closed
        if not is_market_open(buy_date):
            raise serializers.ValidationError({"detail": f"Market closed on {buy_date}"})           

        account = None
        account_id = self.request.data.get("account_id")
        if account_id:
            try:
                account = Account.objects.get(id=account_id)
                if account.user != self.request.user:
                    raise serializers.ValidationError({"detail": "You do not own this account."})
            except Account.DoesNotExist:
                 raise serializers.ValidationError({"detail": "Account does not exist."})

        serializer.save(user=self.request.user, ticker=ticker, shares=shares, buy_date=buy_date, account=account)

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
        try:
            data = get_realtime_price(symbol)
        except Exception as e:
            raise serializers.ValidationError({"symbol": e})
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
        errors = []
        dates = get_market_reference_dates()
        all_financials = get_yfinance_data(ticker_list)
        all_prices = get_historical_prices(ticker_list)
        for ticker in ticker_list:
            try:
                historical_prices = all_prices[ticker]
                reference_prices = {}
                for label, date in dates.items():
                    price = historical_prices.get(date.strftime("%Y-%m-%d"), None)
                    reference_prices[label] = price

                financials = all_financials[ticker]
                price = 0
                percent_change_daily = 0
                if financials["type"] == "MUTUALFUND":
                    price = reference_prices["yesterday"]
                else:
                    quote = get_realtime_price(ticker)
                    price = quote["price"]
                    percent_change_daily = quote["percent_change_daily"]
                financials["current_price"] = price
                financials["percent_change_daily"] = percent_change_daily
                financials["percent_change_weekly"] = percent_change(price, reference_prices["1_week_ago"])
                financials["percent_change_monthly"] = percent_change(price, reference_prices["1_month_ago"])
                financials["percent_change_YTD"] = percent_change(price, reference_prices["year_to_date"])
                financials["percent_change_yearly"] = percent_change(price, reference_prices["1_year_ago"])
                financials["percent_change_3_years"] = percent_change(price, reference_prices["3_years_ago"])
                financials["percent_change_5_years"] = percent_change(price, reference_prices["5_years_ago"])
                data.append(financials)
            except Exception as e:
                errors.append({"ticker": ticker, "error": str(e)})
                # make this error better
                continue        
        return Response({"data": data, "errors": errors})

class AssetHistoricalPricesRetrieveView(APIView):
    def get(self, request):
        ticker = request.query_params.get("ticker")
        if (ticker == None):
            raise serializers.ValidationError({"ticker": "This field is required."})
        prices = get_historical_prices([ticker])[ticker]
        output = []
        for date, price in prices.items():
            output.append({"date": date, "value": price})
        return Response(output)

class FredDataRetrieveView(APIView):
    def post(self, request):
        serializer = FredSeriesItemSerializer(data=request.data, many=True)
        serializer.is_valid(raise_exception=True)
        data = {}
        for item in serializer.validated_data:
            output = get_fred_data(item["series_id"], item["compute_yoy"])
            data[item["series_id"]] = output

        return Response(data)