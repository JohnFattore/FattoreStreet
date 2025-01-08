from celery import shared_task
from portfolio.models import SnP500Price, Asset
import yfinance as yf
from datetime import date, timedelta

def daterange(start_date: date, end_date: date):
    days = int((end_date - start_date).days)
    for n in range(days):
        yield start_date + timedelta(n)

@shared_task
def SnP500PriceUpdate():
    yfinance = yf.Ticker('SPY')
    start_date = date(2019, 1, 1)
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

@shared_task
def ReinvestSnP500Dividends():
    yfinance = yf.Ticker("SPY")
    data = yfinance.history(start=date(2019, 1, 1).strftime("%Y-%m-%d"), end=date.today().strftime("%Y-%m-%d"))
    dividends = yfinance.dividends
    for Snp500 in SnP500Price.objects.all():
        currentShares = 1
        totalDividends = 0
        for dividend_date, dividend in dividends.items():
            if dividend_date > Snp500.date:
                price = data["Close"][dividend_date.strftime("%Y-%m-%d")]
                newShares = ((dividend * currentShares) / price)
                currentShares = currentShares + newShares
                totalDividends = totalDividends + (dividend * currentShares)

        Snp500.reinvestShares = currentShares
        Snp500.dividends = totalDividends
        Snp500.save()
    