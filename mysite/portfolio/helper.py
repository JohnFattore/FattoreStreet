from django.shortcuts import render, get_object_or_404
from django.http import HttpResponse
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from .forms import Register, Login_user, Stock_buy, Stock_CSV_buy, Stock_sell, Logout_user
from django.db import models
from .models import Asset, Allocation
import yfinance as yf
import csv

# function to seperate portfolios
def seperate_accounts(portfolio):
    # creates dictonary with key words of each account and value of how many stock entrys in the account
    shares = {}
    for stock in portfolio:
        if stock.account not in shares:
            shares[stock.account] = 1
        else:
            shares[stock.account] = shares[stock.account] + 1
    # creates list of keys for dict
    keysList = [key for key in shares]
    n = len(shares)
    accounts = {}
    for i in range(n):
        accounts[keysList[i]] = []
    for stock in portfolio:
        for i in range(n):
            if stock.account == keysList[i]:
                accounts[keysList[i]].append(stock)
    # returns dictonary of lists containing each account
    # i.e. accounts = {'roth_ira': [stock1, stock2], 'individual': [stock1, stock2]}
    return accounts

# takes 1 list of asset models and returns a list of allocation models
def allocate(account):
    groups = {}
    for stock in account:
        if stock.ticker_text not in groups:
            groups[stock.ticker_text] = stock.shares_integer
        else:
            groups[stock.ticker_text] = groups[stock.ticker_text] + stock.shares_integer
    
    # declare variables
    keys = [key for key in groups]
    prices = {}
    allocation = []
    total = 0

    # get prices of each asset, return prices dictonary
    for key in keys:
        price = yf.Ticker(key).info['regularMarketPrice'] * float(groups[key])
        total = total + price
        prices[key] = price
    
    # create allocation models, return list of allocation models
    for key in keys:
        allocated = 100.0 * (prices[key] / total)
        # list of all roth_ira allocations
        allocation.append(Allocation(ticker_text = key, shares_integer = ('{:.2f}'.format(groups[key])), currentPrice = ('{:.2f}'.format(prices[key])), percent_allocated = '{:.2f}%'.format(allocated)))
    return allocation
#def scheduler():
#    return

