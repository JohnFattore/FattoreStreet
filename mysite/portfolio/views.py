from django.shortcuts import render, get_object_or_404
from django.http import HttpResponse
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from .forms import Register, Login_user, Stock_buy, Stock_sell, Logout_user
from django.db import models
from .models import Asset as Asset

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
        # create form object from submission
        form = Stock_buy(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            ticker_text = form.cleaned_data['ticker_text']
            shares_integer = form.cleaned_data['shares_integer']
            costbasis_price = form.cleaned_data['costbasis_price']
            buy_date = form.cleaned_data['buy_date']
            user = get_object_or_404(User, pk=user_id)
            # collect all the data and then create a new stock entry
            # the user object is directly linked in this model and automatically inputted
            new_stock = Asset(ticker_text=ticker_text,
                                    shares_integer=shares_integer,
                                    costbasis_price=costbasis_price,
                                    buy_date=buy_date,
                                    user=user)
            # new_stock = Asset.objects.create(ticker_text=ticker_text, shares_integer=shares_integer, costbasis_price=costbasis_price, buy_date=buy_date, user=user)
            new_stock.save()
            return render(request, 'portfolio/portfolio.html', {'user': user})
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
        form = Stock_buy()
    return render(request, 'portfolio/buy.html', {'form': form, 'portfolio': portfolio})

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
            portfolio = Asset.objects.all().filter(user=user).order_by('buy_date')
            # the user object is directly linked in this model and automatically inputted
            return render(request, 'portfolio/portfolio.html', {'user': user, 'portfolio': portfolio})
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
    return render(request, 'portfolio/portfolio.html', {'user': user, 'portfolio': portfolio})

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
    # if GET request, create blank Login form
    else:
        user = get_object_or_404(User, pk=user_id)
        form = Logout_user()
    return render(request, 'portfolio/logout.html', {'user': user, 'form': form})