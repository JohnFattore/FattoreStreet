from django.shortcuts import render, get_object_or_404
from django.http import HttpResponse
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from .forms import Register, Login_user, Stock_buy, Stock_CSV_buy, Stock_sell, Logout_user
from django.db import models
from .models import Asset as Asset
import csv

# function to seperate portfolios
def seperate_accounts(portfolio):
    # creates dictonary with key words of each account and value of how many stock entrys in the account
    accounts = {}
    for stock in portfolio:
        if stock.account not in accounts:
            accounts[stock.account] = 1
        else:
            accounts[stock.account] = accounts[stock.account] + 1
    # creates list of keys for dict
    keysList = [key for key in accounts]
    n = len(accounts)
    dict = {}
    for i in range(n):
        dict[keysList[i]] = []
    for stock in portfolio:
        for i in range(n):
            if stock.account == keysList[i]:
                dict[keysList[i]].append(stock)
    # returns dictonary of lists containing each account
    # i.e. dict = {'roth_ira': [stock1, stock2], 'individual': [stock1, stock2]}
    return dict, keysList

def allocate(portfolio):
    groups = {}
    for stock in portfolio:
        if stock.ticker_text not in groups:
            groups[stock.ticker_text] = stock.shares_integer
        else:
            groups[stock.ticker_text] = groups[stock.ticker_text] + stock.shares_integer
    keysList = [key for key in groups]
    # returns a dictonary where the keys are different assets and the values are quantity of shares
    return groups, keysList
#def scheduler():
#    return

