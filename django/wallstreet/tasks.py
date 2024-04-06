from wallstreet.models import Option, Result, Selection
from portfolio.models import User
from celery import shared_task
import requests

@shared_task
def startWeek(sunday):
    tickers = ["NVDA", "MSFT", "AAPL", "C", "KO", "F", "JNJ", "META", "LLY", "V"]
    # Company Profile 2 to get name
    for ticker in tickers:
        # should be ENV, URL and key
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        responseName = requests.get("https://finnhub.io/api/v1/stock/profile2/", 
                                params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        Option.objects.create(ticker=ticker, name=responseName.json()["name"] ,sunday=sunday, startPrice=response.json()["c"], benchmark=False)
    # also make benchmark options
    benchmarks = ["SPY", "VTWO", "VT"]
    benchmarkNames = ["S&P 500", "Russell 2000", "World Economy"]
    index = 0
    for ticker in benchmarks:
        # should be ENV, URL and key
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        Option.objects.create(ticker=ticker, name=benchmarkNames[index], sunday=sunday, startPrice=response.json()["c"], benchmark=True)
        index = index + 1
    print("Creating Weekly Options")

@shared_task
def endWeek(sunday):
    # set end price for all options
    for option in Option.objects.filter(sunday=sunday):
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": option.ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        quote = response.json()["c"]
        option.endPrice = quote
        option.percentChange = (100 * (float(quote) - float(option.startPrice)) / float(option.startPrice))
        option.save()

    # rank weekly options + set Even Allocation benchmark Option
    rank = 1
    percentChange = 0
    for option in Option.objects.filter(sunday=sunday, benchmark=False).order_by('-percentChange'):
        option.rank = rank
        option.save()
        percentChange = percentChange + option.percentChange
        rank = rank + 1
    Option.objects.create(ticker="", name="Even Allocation", sunday=sunday, startPrice=-1, endPrice=-1, percentChange=((percentChange) / rank), rank=0, benchmark=True)
    print("Setting End Price and Rank")
    
    # set weekly results
    for user in User.objects.all():
        percentChange = 0
        selections = Selection.objects.filter(user=user)
        for selection in selections:
            percentChange = percentChange + Option.objects.get(id=selection.option.id).percentChange
        selectionCount = selections.count()
        if selectionCount != 0:
            percentChange = percentChange / selections.count()
        Result.objects.create(portfolioPercentChange=percentChange, sunday=sunday, user=user)