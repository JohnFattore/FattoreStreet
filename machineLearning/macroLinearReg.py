import pandas as pd
from datetime import date
import yfinance as yf
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_squared_error, r2_score

GDPCSV = pd.read_csv("fred/GDP.csv")
UNRATECSV = pd.read_csv("fred/UNRATE.csv")
CPICSV = pd.read_csv("fred/CPI.csv")

data = pd.merge(UNRATECSV, CPICSV, on='observation_date', how="inner")
data = pd.merge(data, GDPCSV, on='observation_date', how="outer")
data = data.rename(columns={"observation_date": "date", "CORESTICKM159SFRBATL": "CPI"})
data["date"] = pd.to_datetime(data["date"]).dt.date
data = data.fillna(method='ffill')
data = data[data["date"] > date(1969, 1, 1)]
data = data[data["date"] < date(2024, 1, 1)]

spy = yf.Ticker("SPY")
spy_data = spy.history(period="max", interval="1mo")  # Use "max" for all available data
spy_data = spy_data.reset_index()
spy_data = spy_data.drop(columns=["Open", "High", "Low", "Volume", "Dividends", "Stock Splits", "Capital Gains"])
spy_data = spy_data.rename(columns={"Date": "date", "Close": "SPY"})
spy_data["date"] = pd.to_datetime(spy_data["date"]).dt.date

data = pd.merge(data, spy_data, on="date")
X = data[["UNRATE", "CPI", "GDP"]]
y = data["SPY"]
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, shuffle=False)
model = LinearRegression()
model.fit(X_train, y_train)
y_pred = model.predict(X_test)
mse = mean_squared_error(y_test, y_pred)
r2 = r2_score(y_test, y_pred)

