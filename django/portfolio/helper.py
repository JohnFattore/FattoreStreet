import yfinance as yf
from datetime import date, timedelta, datetime
from .models import SnP500Price, AssetInfo
from .choices import ASSET_TYPES, EXCHANGES, MARKETS
from decimal import Decimal

def get_or_create_SnP500Price(price_date: date):
    try:
        snp500Price = SnP500Price.objects.get(date=price_date)
        return snp500Price
    except SnP500Price.DoesNotExist:
        try:
            yfinance = yf.Ticker('SPY')
            data = yfinance.history(start=price_date.strftime("%Y-%m-%d"), end=(price_date + timedelta(days=1)).strftime("%Y-%m-%d"))
            snp500Price = SnP500Price.objects.create(date=price_date, price=data['Close'][price_date.strftime("%Y-%m-%d")])
            return snp500Price
        except: 
            raise Exception("Error getting S&P 500 Price")

def get_or_create_AssetInfo(ticker: str):
    try:
        asset_info = AssetInfo.objects.get(ticker=ticker)
        return asset_info
    except AssetInfo.DoesNotExist:
        yfinance = yf.Ticker(ticker)
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
        asset_info = AssetInfo.objects.create(ticker=ticker,
                                            short_name=data["shortName"],
                                            long_name=data["longName"],
                                            type=type,
                                            market=market,
                                            exchange=exchange)
        return asset_info

def get_ticker_price(ticker: str, date: date):
    yfinance = yf.Ticker(ticker)
    data = yfinance.history(start=date.strftime("%Y-%m-%d"), end=(date + timedelta(days=1)).strftime("%Y-%m-%d"))
    price = data['Close'].get(date.strftime("%Y-%m-%d"), None)
    if price == None:
        raise Exception(f"No Price for ticker {ticker} on day {date}")
    return Decimal(price)