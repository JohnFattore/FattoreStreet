from wallstreet.models import Option, Result, AltBenchmark, Selection
from portfolio.models import User
from celery import shared_task
import requests

@shared_task
def startWeek(sunday):
    tickers = ["NVDA", "MSFT", "AAPL", "C", "KO", "F", "JNJ", "META", "LLY", "V"]
    for ticker in tickers:
        # should be ENV, URL and key
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        Option.objects.create(ticker=ticker, sunday=sunday, startPrice=response.json()["c"], benchmark=False)
    # also make benchmark options
    benchmarks = ["SPY", "VTWO", "VT"]
    for ticker in benchmarks:
        # should be ENV, URL and key
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        Option.objects.create(ticker=ticker, sunday=sunday, startPrice=response.json()["c"], benchmark=True)
    print("Creating Weekly Options")

@shared_task
def endWeek(sunday):
    # set end price for all options
    for option in Option.objects.filter(sunday=sunday):
        response = requests.get("https://finnhub.io/api/v1/quote/", 
                                params={"symbol": option.ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        quote = response.json()["c"]
        option.endPrice = quote
        option.percentChange = ((float(quote) - float(option.startPrice)) / float(option.startPrice))
        option.save()

    # rank weekly options + set Even Allocation bench mark
    rank = 1
    percentChange = 0
    for option in Option.objects.filter(sunday=sunday, benchmark=False).order_by('-percentChange'):
        option.rank = rank
        option.save()
        percentChange = percentChange + option.percentChange
        rank = rank + 1
    AltBenchmark.objects.create(benchmark="Even Allocation", percentChange=(percentChange / rank), sunday=sunday)
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