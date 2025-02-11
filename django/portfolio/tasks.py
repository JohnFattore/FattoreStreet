from celery import shared_task
from .models import Asset, SnP500Price
import yfinance as yf
from datetime import timedelta, date
from decimal import Decimal

@shared_task
def updateCostBasis():
    print("Beginning Cost Basis Update")
    for asset in Asset.objects.all():
        yfinance = yf.Ticker(asset.ticker)
        data = yfinance.history(start=asset.buyDate.strftime("%Y-%m-%d"), end=(asset.buyDate + timedelta(days=1)).strftime("%Y-%m-%d"))
        newCostBasis = round(Decimal(data['Close'][asset.buyDate.strftime("%Y-%m-%d")]), 2)
        if (asset.costbasis != newCostBasis):
            asset.costbasis = newCostBasis
            asset.save()

    yfinance = yf.Ticker("SPY")
    start_date = date(1993, 1, 1)
    end_date = date(2025, 12, 31)
    data = yfinance.history(start=start_date.strftime("%Y-%m-%d"), end=end_date.strftime("%Y-%m-%d"))
    for snp in SnP500Price.objects.all():    
        newCostBasis = round(Decimal(data['Close'][snp.date.strftime("%Y-%m-%d")]), 2)
        # some snp prices were returning $0.01 off each time
        if (abs(snp.price - newCostBasis) > 0.01):
            snp.price = newCostBasis
            snp.save()
    print("End Cost Basis Update")

def daterange(start_date, end_date):
    """Helper function to iterate over a range of dates."""
    for n in range((end_date - start_date).days + 1):
        yield start_date + timedelta(n)

@shared_task
def loadSnP500Prices():
    yfinance = yf.Ticker('SPY')
    start_date = date(1993, 1, 1)
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