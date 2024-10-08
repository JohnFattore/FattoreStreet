import yfinance as yf
from datetime import date, timedelta

def daterange(start_date: date, end_date: date):
    days = int((end_date - start_date).days)
    for n in range(days):
        yield start_date + timedelta(n)

yfinance = yf.Ticker('SPY')
start_date = date(2023, 1, 1)
end_date = date(2023, 11, 1)
data = yfinance.history(start=start_date.strftime("%Y-%m-%d"), end=end_date.strftime("%Y-%m-%d"))
print(data['Close'])
# queryset = SnP500Price.objects.all()

for single_date in daterange(start_date, end_date):
    #if not queryset.filter(date=single_date).exists():
    try:
        # print(single_date.strftime("%Y-%m-%d"))
        print(data['Close'][single_date.strftime("%Y-%m-%d")])
        # SnP500Price.objects.create(date=single_date, price=data['Close'][single_date.strftime("%Y-%m-%d") + ' 00:00:00-05:00'])
    except:
        print(single_date.strftime("%Y-%m-%d") + "Date has no value")
    #else:
    #    print("Date already exists")