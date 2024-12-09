from celery import shared_task
from indexes.models import Stock, IndexMember
import csv
import yfinance as yf

@shared_task
def NASDAQFile():
    print("Start Stock Load")
    skippedTickers = []

    # for stock in Stock.objects.all():
    #     if stock.marketCap == 0:
    #         stock.delete()


    # # Tickers in file not in database are identified and added
    # with open('indexCompare/NASDAQ.csv', mode ='r') as file:
    #     csvFile = csv.reader(file)
    #     fileTickers = []
    #     for lines in csvFile:
    #         fileTickers.append(lines[0])

    # existingTickers = []
    # for stock in Stock.objects.all():
    #     existingTickers.append(stock.ticker)
    # newTickers = list(set(fileTickers) - set(existingTickers))
    # print("New Tickers: ", newTickers)

    # this adds far too many, need a reduced amount

    # # add new stocks to database
    # for ticker in newTickers:
    #     try:
    #         if (Stock.objects.filter(ticker=ticker).count() == 0):
    #             yfinance = yf.Ticker(ticker)
    #             Stock.objects.create(
    #                 ticker=ticker, 
    #                 name=yfinance.info["shortName"],
    #                 marketCap=0, 
    #                 countryIncorp="United States",
    #                 countryHQ=yfinance.info["country"],
    #                 securityType="Common Stock", 
    #                 volume=0, 
    #                 volumeUSD=0, 
    #                 freeFloat=0, 
    #                 freeFloatMarketCap=0, 
    #                 yearIPO=2025
    #             )
    #         else:
    #             print("trying to add existing ticker: ", ticker)
    #     except:
    #         skippedTickers.append(ticker)




    # update all stocks in universe
    for stock in Stock.objects.all():
        try:
            yfinance = yf.Ticker(stock.ticker)
            stock.marketCap = float(yfinance.info["marketCap"])
            stock.volume=yfinance.info["averageVolume"]
            stock.volumeUSD = yfinance.info["averageVolume"] * yfinance.info["currentPrice"]
            freeFloat = yfinance.info["floatShares"] / yfinance.info["sharesOutstanding"]
            if (freeFloat > 1):
                freeFloat = 1
            stock.freeFloatMarketCap = freeFloat * float(yfinance.info["marketCap"])
            stock.freeFloat = freeFloat
            stock.save()
        except:
            skippedTickers.append(stock.ticker)

    # data fix GOOG and GOOGL
    Stock.objects.filter(ticker="GOOG").update(freeFloat=((5.19 / 5.59) / 2))
    Stock.objects.filter(ticker="GOOGL").update(freeFloat=((5.84 / 5.86) / 2))

    # data fix MLPs and Preferred Stock
    MLPs = ['EPD', 'ET', 'MPLX', 'CQP', 'WES', 'PAA', 'SUN']
    Stock.objects.filter(ticker__in=MLPs).update(securityType="MLP")

    Preferred = ['AGNCN', 'AGNCM', 'FITBI', 'SLMBP', 'VLYPO', 'VLYPP']
    Stock.objects.filter(ticker__in=Preferred).update(securityType="Preferred Stock")

    ADR = ['CUK']
    Stock.objects.filter(ticker__in=Preferred).update(securityType="ADR")

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
            IndexMember.objects.create(stock=stock, percent=percent, index="Russell 1000", outlier=False, notes='')
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

    for indexMember in IndexMember.objects.filter(ticker__in=InMyIndexNotInRussell1000):
        indexMember.outlier = True
        indexMember.save()