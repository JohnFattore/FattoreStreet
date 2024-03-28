from wallstreet.models import Selection, Option
from celery import shared_task
import requests

@shared_task
def test(sunday):
    Option.objects.create(ticker="VTI", sunday=sunday)
    print("Hello Spike")

@shared_task
def startWeek(sunday):
    tickers = ["NVDA", "MSFT", "AAPL", "C", "KO", "F", "JNJ", "META", "LLY", "V"]
    for ticker in tickers:
        # should be ENV, URL and key
        response = requests.get("https://finnhub.io/api/v1/quote/", params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        print(response.json()["c"])
        Option.objects.create(ticker=ticker, sunday=sunday, startPrice=response.json()["c"])
    print("Creating Weekly Options")

@shared_task
def endWeek(sunday):
    # set end price
    for option in Option.objects.filter(sunday=sunday):
        response = requests.get("https://finnhub.io/api/v1/quote/", params={"symbol": option.ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        quote = response.json()["c"]
        option.endPrice = quote
        option.percentChange = ((float(quote) - float(option.startPrice)) / float(option.startPrice))
        option.save()

    # rank weekly options
    rank = 1
    for option in Option.objects.filter(sunday=sunday).order_by('-percentChange'):
        option.rank = rank
        option.save()
        rank = rank + 1
    print("Setting End Price and Rank")