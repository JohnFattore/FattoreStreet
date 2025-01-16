from celery import shared_task
from portfolio.models import SnP500Price, Asset
import yfinance as yf
from datetime import date, timedelta

def daterange(start_date: date, end_date: date):
    days = int((end_date - start_date).days)
    for n in range(days):
        yield start_date + timedelta(n)

@shared_task
def SnP500PriceUpdate():
    print("HEllo World")