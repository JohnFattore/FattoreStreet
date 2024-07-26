from celery import shared_task
from indexCompare.models import Stock, IndexMember
import requests
import time
import csv
import json
import yfinance as yf

@shared_task
def addOptions():
    print("I Love Indexing")

@shared_task
def createStockData():
    print("Start Stock Update")
    file = open('indexCompare/tickers.json')
    data = (json.load(file))
    
    for stock in data:
        ticker = data[stock]["ticker"]
        name = data[stock]["title"]
        response = requests.get("https://finnhub.io/api/v1/stock/profile2/", params={"symbol": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
        if ((response.json() != {}) and (response.json()["currency"] == "USD")):
            Stock.objects.create(ticker=ticker, name=name, marketCap=response.json()["marketCapitalization"])
        time.sleep(1)
    print("Finish Stock Update")

@shared_task
def NASDAQFile():
    print("Start Stock Load")
    securityType = ''
    with open('indexCompare/NASDAQ.csv', mode ='r') as file:
        csvFile = csv.reader(file)
        for lines in csvFile:
            if (lines[0] != "Symbol" and lines[5] != "" and lines[0] > 'JNJ'):
                yfinance = yf.Ticker(lines[0])
                response = requests.get("https://finnhub.io/api/v1/search/", params={"q": lines[0], "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
                for stock in response.json()["result"]:
                    if (lines[0] == stock["symbol"]):
                        securityType = stock["type"]
                if (yfinance.info.get("country")):
                    Stock.objects.create(ticker=lines[0], name=lines[1], marketCap=float(lines[5]), countryIncorp=lines[6], countryHQ=yfinance.info["country"], securityType=securityType)
        # time.sleep(0.1)
    print("Finish Stock Load")

@shared_task
def createMarketCapIndex(companies):
    print("Created Index")
    i = 0
    totalMarketCap = 0
    banList = ["GOOG", "GOOGL", "BRK.A", "CCZ"]
    inclusionList = ["Common Stock", "REIT"]
    for stock in Stock.objects.all().order_by('-marketCap'):
        if (stock.countryHQ == "United States" and stock.securityType in inclusionList and stock.ticker not in banList):
            totalMarketCap = totalMarketCap + stock.marketCap
            i = i + 1
        elif (stock.ticker == "GOOG" or stock.ticker == "GOOGL"):
            totalMarketCap = totalMarketCap + (stock.marketCap / 2 )
            i = i + (1/2)

        if (i == companies):
            break
    
    i = 0
    for stock in Stock.objects.all().order_by('-marketCap'):
        if (stock.countryHQ == "United States" and stock.securityType in inclusionList and stock.ticker not in banList):
            IndexMember.objects.create(ticker=stock.ticker, percent=(100 * stock.marketCap / totalMarketCap), index="Russell 1000")
            i = i + 1
        elif (stock.ticker == "GOOG" or stock.ticker == "GOOGL"):
            IndexMember.objects.create(ticker=stock.ticker, percent=(50 * stock.marketCap / totalMarketCap), index="Russell 1000")
            i = i + (1/2)
        if (i == companies):
            break

@shared_task
def compareIndexes():
    print("I Love Indexing")
    myIndex = []
    for stock in IndexMember.objects.all():
        myIndex.append(stock.ticker)

    NotInMyIndex = {}

    counter = 0
    with open('indexCompare/russell-1000-index-07-07-2024.csv', mode ='r') as file:
        csvFile = csv.reader(file)
        for lines in csvFile:
            if (lines[0] not in myIndex and lines[0] != 'Symbol' and lines[2] != 'N/A'):
                counter = counter + 1
                NotInMyIndex.update({lines[0]: int(lines[2])})
    print(counter)
    # print(dict(sorted(NotInMyIndex.items(), key=lambda item: item[1])))
    for ticker in dict(sorted(NotInMyIndex.items(), key=lambda item: item[1])):
        yfinance = yf.Ticker(ticker)
        if (yfinance.info.get("country")):
            print(ticker, yfinance.info["country"], NotInMyIndex[ticker])
        else:
            print(ticker)

@shared_task
def compareIndexes2():
    print("I Love Indexing2")
    myIndex = []
    for stock in IndexMember.objects.all():
        myIndex.append(stock.ticker)

    Russell1000 = []
    with open('indexCompare/russell-1000-index-07-07-2024.csv', mode ='r') as file:
        csvFile = csv.reader(file)
        for lines in csvFile:
            if (lines[0] != 'Symbol' and lines[2] != 'N/A'):
                Russell1000.append(lines[0])

    NotInRussell1000 = []
    for ticker in myIndex:
        try:
            if ticker not in Russell1000:
                NotInRussell1000.append(ticker)
                yfinance = yf.Ticker(ticker)
                print(ticker, "Free Float:", yfinance.info["floatShares"] * 100 / yfinance.info["sharesOutstanding"])
        except:
            print(ticker)

    print(len(NotInRussell1000))
    print(NotInRussell1000)