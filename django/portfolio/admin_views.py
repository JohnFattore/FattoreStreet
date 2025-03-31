# only API views, see retiredViews for old django frontend views
# API modules using drf
from rest_framework import views, response
from .models import Asset, SnP500Price, AssetInfo
from .tasks import updateCostBasis
import yfinance as yf
from datetime import timedelta, date
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from decimal import Decimal
from .choices import ASSET_TYPES, EXCHANGES, MARKETS
from rest_framework import permissions

def daterange(start_date, end_date):
    """Helper function to iterate over a range of dates."""
    for n in range((end_date - start_date).days + 1):
        yield start_date + timedelta(n)

class SnP500PriceCreateView(APIView):
    permission_classes = [permissions.IsAdminUser]

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

# make not celery task
class UpdateCostBasis(views.APIView):
    permission_classes = [permissions.IsAdminUser]

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
    
class CreateAssetInfo(views.APIView):
    permission_classes = [permissions.IsAdminUser]

    def post(self, request):
        for asset in Asset.objects.all():
            yfinance = yf.Ticker(asset.ticker)
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

            created = AssetInfo.objects.get_or_create(ticker=asset.ticker,
                                                      short_name=data["shortName"],
                                                      long_name=data["longName"],
                                                      type=type,
                                                      market=market,
                                                      exchange=exchange)
            if created:
                print(f"Created new AssetInfo for {asset.ticker}")
            else:
                print(f"AssetInfo already exists for {asset.ticker}")

class UpdateAssetWithInfo(views.APIView):
    permission_classes = [permissions.IsAdminUser]

    def post(self, request):
        for asset in Asset.objects.all():
            try:
                asset_info = AssetInfo.objects.get(ticker=asset.ticker)
                if asset_info:
                    asset.asset_info = asset_info
                    asset.save()

                else:
                    yfinance = yf.Ticker(asset.ticker)
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
                    AssetInfo.objects.create(ticker=asset.ticker,
                                                      short_name=data["shortName"],
                                                      long_name=data["longName"],
                                                      type=type,
                                                      market=market,
                                                      exchange=exchange)   
                    asset.asset_info = asset_info
                    asset.save()                     
            except:
                raise Exception(f"an error has occured processing {asset.ticker}")

        return response.Response({"message": "assets updated successfully."})