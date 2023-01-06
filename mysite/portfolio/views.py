from django.shortcuts import render, get_object_or_404
from django.http import HttpResponse
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from .forms import Register, Login_user, Stock_buy, Stock_CSV_buy, Stock_sell, Logout_user
from django.db import models
from .models import Asset, Allocation
from .helper import seperate_accounts, allocate
import yfinance as yf
import csv

# Create your views here.
def index(request):
    return render(request, 'portfolio/index.html')

def register_view(request):
    # route for post, when the form is submitted
    if request.method == 'POST':
        # create form object from submission
        form = Register(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            username = form.cleaned_data['username']
            password1 = form.cleaned_data['password1']
            password2 = form.cleaned_data['password2']
            email = form.cleaned_data['email']
            fname = form.cleaned_data['fname']
            lname = form.cleaned_data['lname']
            # ensure passwords match so user doesn't mistype
            if password1 != password2:
                # if passwords different, render error
                error = "Passwords Must Match"
                return render(request, 'portfolio/register.html', {'error': error})
            # Register New User
            user = User.objects.create_user(username=username, password=password1, email=email, first_name=fname, last_name=lname)
            user.save()
            # the functions authenticate() and login() are used to login the user
            user = authenticate(request, username=username, password=password1)
            login(request, user)
            return render(request, 'portfolio/portfolio.html')
    # if GET request, create blank register form
    else:
        form = Register()
    return render(request, 'portfolio/register.html', {'form': form})

def login_user_view(request):
    # route for post, when the form is submitted
    if request.method == 'POST':
        # create form object from submission
        form = Login_user(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            username = form.cleaned_data['username']
            password = form.cleaned_data['password']
            # authenticate() here ensures the correct username and password were inputted
            user = authenticate(request, username=username, password=password)
            if user is not None:
                # if password and username correct, then login the user
                login(request, user)
                return render(request, 'portfolio/portfolio.html', {'user': user})
            else:
                return render(request, 'portfolio/index.html')
    # if GET request, create blank Login form
    else:
        form = Login_user()
    return render(request, 'portfolio/login_user.html', {'form': form})

def buy_view(request, user_id):
    # route for post, when the form is submitted
    if request.method == 'POST':
        # Get user first
        user = get_object_or_404(User, pk=user_id)
        # create form object from submission
        form = Stock_buy(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            ticker_text = form.cleaned_data['ticker_text'] #.upper()
            shares_integer = form.cleaned_data['shares_integer']
            costbasis_price = form.cleaned_data['costbasis_price']
            buy_date = form.cleaned_data['buy_date']
            account = form.cleaned_data['account']
            # collect all the data and then create a new stock entry
            # the user object is directly linked in this model and automatically inputted
            new_stock = Asset(ticker_text=ticker_text,
                                    shares_integer=shares_integer,
                                    costbasis_price=costbasis_price,
                                    buy_date=buy_date,
                                    account=account,
                                    user=user)
            # new_stock = Asset.objects.create(ticker_text=ticker_text, shares_integer=shares_integer, costbasis_price=costbasis_price, buy_date=buy_date, user=user)
            new_stock.save()
            return render(request, 'portfolio/portfolio.html', {'user': user})
        else:
            error = 'Form Not Valid, Try Again'
            return render(request, 'portfolio/apology.html', {'error': error})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
        form = Stock_buy()
        return render(request, 'portfolio/buy.html', {'form': form, 'portfolio': portfolio})

def buy_CSV_view(request, user_id):
    # route for post, when the form is submitted
    if request.method == 'POST':
        # Get user first
        user = get_object_or_404(User, pk=user_id)
        # create form object from submission
        form = Stock_CSV_buy(request.POST, request.FILES)
        # if valid form, collect all data in variables
        if form.is_valid():
            # collect all the data and then create a new stock entry
            # the user object is directly linked in this model and automatically inputted
            csvfile = request.FILES['file'].read().decode("utf-8")	
            stocks = csvfile.split('\r\n')
            for stock in stocks:
                fields = stock.split(",")
                ticker_text = fields[0]
                shares_integer = fields[1]
                costbasis_price = fields[2]
                buydata = fields[3].split("/")
                buy_date = buydata[2] + "-" + buydata[0] + "-" + buydata[1]
                account = fields[4]
                new_stock = Asset.objects.create(ticker_text=ticker_text, shares_integer=shares_integer, costbasis_price=costbasis_price, buy_date=buy_date, account=account, user=user)
            return render(request, 'portfolio/portfolio.html', {'user': user})
        else:
            error = 'Form Not Valid, Try Again'
            return render(request, 'portfolio/apology.html', {'error': error})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
        form = Stock_CSV_buy()
        return render(request, 'portfolio/buy_CSV.html', {'form': form, 'portfolio': portfolio})

def sell_view(request, user_id):
    # route for post, when the form is submitted
    if request.method == 'POST':
        user = get_object_or_404(User, pk=user_id)
        # create form object from submission
        form = Stock_sell(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            buy_date = form.cleaned_data['buy_date']
            # collect all the data and then delete stock entry
            Asset.objects.filter(buy_date=buy_date, user=user).delete()
            # The next line should not be commented out unless deleting all stocks
            #Asset.objects.filter().delete()
            portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
            # the user object is directly linked in this model and automatically inputted
            return render(request, 'portfolio/portfolio.html', {'user': user, 'portfolio': portfolio})
        else:
            error = 'Form Not Valid, Try Again'
            return render(request, 'portfolio/apology.html', {'error': error})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
        form = Stock_sell()
        return render(request, 'portfolio/sell.html', {'form': form, 'portfolio': portfolio})

def portfolio_view(request, user_id):
    # pull current user or return an error page
    user = get_object_or_404(User, pk=user_id)
    # pull all assets associated with logged in user
    portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
    accounts, keysList = seperate_accounts(portfolio)
    roth_ira = accounts[keysList[0]]
    individual = accounts[keysList[1]]
    return render(request, 'portfolio/portfolio.html', {'user': user, 'roth_ira': roth_ira, 'individual': individual})

def schedule_view(request, user_id):
    # route for post, when the form is submitted
    if request.method == 'POST':
        user = get_object_or_404(User, pk=user_id)
        return render(request, 'portfolio/schedule.html', {'user': user})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
        # seperate_accounts returns dictonary of lists containing the stocks of each accont
        # accountkeys is a list of account names or keys for the dictonary
        accounts, accountkeys = seperate_accounts(portfolio)
        roth_ira = accounts[accountkeys[0]]
        individual = accounts[accountkeys[1]]
        # account0 is roth_ira
        # allocation returns a dictonary with keys of tickers and value the quality of shares
        # roth_ira_keys is a list of keys for the dictonary
        roth_ira, roth_ira_keys = allocate(accounts[accountkeys[0]])
        # account1 is individual, not iteriable let, but function is iterable
        individual, individual_keys = allocate(accounts[accountkeys[1]])
        roth_ira_prices = {}
        individual_prices = {}
        roth_ira_allocation = []
        individual_allocation = []
        # create allocation models for roth ira assets
        for key in roth_ira_keys:
            price = yf.Ticker(key).info['regularMarketPrice'] * float(roth_ira[key])
            roth_ira_allocation.append(Allocation(ticker_text = key, shares_integer = roth_ira[key], currentPrice = price))
        
        # create allocation models for individual assets
        for key in individual_keys:
            price = yf.Ticker(key).info['regularMarketPrice'] * float(individual[key])
            individual_allocation.append(Allocation(ticker_text = key, shares_integer = individual[key], currentPrice = price))
        
        return render(request, 'portfolio/schedule.html', {'user': user, 'roth_ira': roth_ira, 'individual': individual,
                                                            'roth_ira_allocation': roth_ira_allocation, 'individual_allocation': individual_allocation})

def logout_view(request, user_id):
    # route for post, when the form is submitted
    if request.method == 'POST':
        user = get_object_or_404(User, pk=user_id)
        # create form object from submission
        form = Logout_user(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            username = form.cleaned_data['username']
            # Logout User
            logout(request)
            # the user object is directly linked in this model and automatically inputted
            return render(request, 'portfolio/login_user.html')
        else:
            error = 'Form Not Valid, Try Again'
            return render(request, 'portfolio/apology.html', {'error': error})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        form = Logout_user()
        return render(request, 'portfolio/logout.html', {'user': user, 'form': form})