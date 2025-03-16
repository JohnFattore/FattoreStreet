import yfinance as yf
from datetime import datetime, timedelta, date

def daterange(start_date, end_date):
    """Helper function to iterate over a range of dates."""
    for n in range((end_date - start_date).days + 1):
        yield start_date + timedelta(n)

yfinance = yf.Ticker('JNJ')
start_date = date(2021, 1, 1)
end_date = date(2021, 2, 1)
data = yfinance.history(start=start_date.strftime("%Y-%m-%d"), end=end_date.strftime("%Y-%m-%d"), auto_adjust=False)
for single_date in daterange(start_date, end_date):
    try:
        print(data['Close'][single_date.strftime("%Y-%m-%d")])
    except:
        print(single_date.strftime("%Y-%m-%d") + " has no value")
