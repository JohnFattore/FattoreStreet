from celery import shared_task
from indexCompare.models import Stock, IndexMember, Outlier
from datetime import datetime
import csv
import yfinance as yf

@shared_task
def NASDAQFile():
    print("Start Stock Load")
    securityType = 'Common Stock'
    ticker = ''
    skippedTickers = []

    with open('indexCompare/NASDAQ.csv', mode ='r') as file:
        csvFile = csv.reader(file)
        for lines in csvFile:
            ticker = lines[0]
            if (ticker != "Symbol" and lines[5] != ""):
                try:
                    yfinance = yf.Ticker(ticker)
                    #response = requests.get("https://finnhub.io/api/v1/search/", params={"q": ticker, "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})
                    #for stock in response.json()["result"]:
                    #    if (ticker == stock["symbol"]):
                    #        securityType = stock["type"]
                    #if (yfinance.info.get("country")):
                    volumeUSD = yfinance.info["averageVolume"] * yfinance.info["currentPrice"]
                    freeFloat = yfinance.info["floatShares"] / yfinance.info["sharesOutstanding"]
                    if (freeFloat > 1):
                        freeFloat = 1
                    freeFloatMarketCap = freeFloat * float(lines[5])
                    Stock.objects.create(ticker=ticker, name=lines[1], marketCap=float(lines[5]), countryIncorp=lines[6], countryHQ=yfinance.info["country"], 
                                         securityType=securityType, volume=yfinance.info["averageVolume"], volumeUSD=volumeUSD, freeFloat=freeFloat, freeFloatMarketCap=freeFloatMarketCap)
                except:
                    skippedTickers.append(ticker)

    # data fix GOOG and GOOGL
    Stock.objects.filter(ticker="GOOG").update(freeFloat=((5.19 / 5.59) / 2))
    Stock.objects.filter(ticker="GOOGL").update(freeFloat=((5.84 / 5.86) / 2))

    # data fix MLPs and REITS
    MLPs = ['EPD', 'ET', 'MPLX', 'CQP', 'WES', 'PAA', 'SUN']
    Stock.objects.filter(ticker__in=MLPs).update(securityType="MLP")

    Preferred = ['AGNCN', 'AGNCM', 'FITBI', 'SLMBP', 'VLYPO', 'VLYPP']
    Stock.objects.filter(ticker__in=Preferred).update(securityType="Preferred Stock")

    print("Skipped Tickers", skippedTickers)
    print("Finish Stock Load")

@shared_task
def createRussell1000():

    print("Creating Index")

    i = 0
    totalMarketCap = 0
    banList = ["BRK.A"]
    for stock in Stock.objects.all().order_by('-marketCap'):
        # make this function
        if ((stock.countryHQ == "United States") and (stock.securityType == 'Common Stock') and (stock.freeFloat > 0.10) and (stock.volumeUSD > 125000) and (stock.ticker not in banList)):
            totalMarketCap = totalMarketCap + stock.marketCap
            i = i + 1

        if (i == 1000):
            break
    
    i = 0
    for stock in Stock.objects.all().order_by('-marketCap'):
        if ((stock.countryHQ == "United States") and (stock.securityType == 'Common Stock') and (stock.freeFloat > 0.10) and (stock.volumeUSD > 125000) and (stock.ticker not in banList)):
            percent = (100 * stock.marketCap / totalMarketCap)
            IndexMember.objects.create(ticker=stock.ticker, percent=percent, index="Russell 1000")
            i = i + 1

        if (i == 1000):
            break
    print("Index Complete")

    print("I Love Indexing")
    myIndex = []
    for stock in IndexMember.objects.all():
        myIndex.append(stock.ticker)

    Russell1000 = []
    with open('indexCompare/russell-1000-index-07-07-2024.csv', mode ='r') as file:
        csvFile = csv.reader(file)
        for lines in csvFile:
            if (lines[0] != 'Symbol' and lines[2] != 'N/A'):
                Russell1000.append(lines[0])

    InMyIndexNotInRussell1000 = []
    InRussell1000NotInMyIndex = []

    for ticker in myIndex:
        if (ticker not in Russell1000):
            InMyIndexNotInRussell1000.append(ticker)


    for ticker in Russell1000:
        if (ticker not in myIndex):
            InRussell1000NotInMyIndex.append(ticker)

    print("In My Index, Not in Russell 1000", len(InMyIndexNotInRussell1000), InMyIndexNotInRussell1000)
    print("In Russell 1000, Not in My Index", len(InRussell1000NotInMyIndex), InRussell1000NotInMyIndex)

    for stock in Stock.objects.filter(ticker__in=InMyIndexNotInRussell1000):
        Outlier.objects.create(ticker=stock.ticker, name=stock.name, marketCap=stock.marketCap, volume=stock.volume,
                                volumeUSD=stock.volumeUSD, freeFloat=stock.freeFloat, freeFloatMarketCap=stock.freeFloatMarketCap,
                                countryIncorp=stock.countryIncorp, countryHQ=stock.countryHQ, securityType=stock.securityType)


@shared_task
def outlierResearch():
    data = []
    for outlier in Outlier.objects.all():
        outlierObject = {
            "ticker": outlier.ticker,
            "name": outlier.name,
            "marketCap": outlier.marketCap,
            "volume": outlier.volume,
            "volumeUSD": outlier.volumeUSD,
            "freeFloat": outlier.freeFloat,
            "freeFloatMarketCap": outlier.freeFloatMarketCap,
            "countryIncorp": outlier.countryIncorp,
            "countryHQ": outlier.countryHQ,
            "securityType": outlier.securityType
        }
        data.append(outlierObject)

    filename = 'outliers' + str(datetime.today().strftime('%Y-%m-%d')) + ".csv"

    with open(filename, 'w', newline='') as csvfile:
        fieldnames = ['ticker', 'name', 'marketCap', 'volume', 'volumeUSD', 'freeFloat', 'freeFloatMarketCap', 'countryIncorp', 'countryHQ', 'securityType']
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)