import pandas as pd
from .helper import get_yfinance_data

def get_all_us_tickers():
    # NASDAQ listed
    nasdaq_url = "ftp://ftp.nasdaqtrader.com/SymbolDirectory/nasdaqlisted.txt"
    nasdaq_data = pd.read_csv(nasdaq_url, sep="|")
    nasdaq_tickers = nasdaq_data['Symbol'].tolist()

    # NYSE listed
    nyse_url = "ftp://ftp.nasdaqtrader.com/SymbolDirectory/otherlisted.txt"
    nyse_data = pd.read_csv(nyse_url, sep="|")
    nyse_tickers = nyse_data['ACT Symbol'].tolist()

    # Combine and remove duplicates
    all_tickers = list(set(nasdaq_tickers + nyse_tickers))
    return all_tickers

    tickers = get_all_us_tickers()
    print(f"Total tickers: {len(tickers)}")
    print(tickers[:20])
    for ticker in tickers[:20]:
        get_yfinance_data(ticker)