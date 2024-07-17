import yfinance as yf
import requests

yfinance = yf.Ticker("ET")
response = requests.get("https://finnhub.io/api/v1/search/", params={"q": 'ET', "token": "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"})

for stock in response.json()["result"]:
    if ("ET" == stock["symbol"]):
        print(stock["type"])