import yfinance as yf
from datetime import datetime, timedelta, date

def daterange(start_date, end_date):
    """Helper function to iterate over a range of dates."""
    for n in range((end_date - start_date).days + 1):
        yield start_date + timedelta(n)

yfinance = yf.Ticker('JNJ')
data = yfinance.info
print(data)

print(data["longName"])
print(data["shortName"])
print(data["quoteType"])