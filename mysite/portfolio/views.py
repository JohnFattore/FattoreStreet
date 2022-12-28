from django.shortcuts import render
from django.http import HttpResponse
from django.contrib.auth import authenticate, login
from django.contrib.auth.models import User
from .forms import Register, Login

# Create your views here.
def index(request):
    return render(request, 'portfolio/index.html')

def portfolio(request):
    return render(request, 'portfolio/portfolio.html')

def register(request):
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
            if password1 != password2:
                return render(request, 'portfolio/index.html')
            # Register New User
            user = User.objects.create_user(username=username, password=password1, email=email, first_name=fname, last_name=lname)
            user.save()
            user = authenticate(username=username, password=password1)
            return render(request, 'portfolio/portfolio.html')
    # if GET request, create blank register form
    else:
        form = Register()
    return render(request, 'portfolio/register.html', {'form': form})

def login(request):
    # route for post, when the form is submitted
    if request.method == 'POST':
        # create form object from submission
        form = Login(request.POST)
        # if valid form, collect all data in variables
        if form.is_valid():
            username = form.cleaned_data['username']
            password = form.cleaned_data['password']
            user = authenticate(username=username, password=password)
            if user is not None:
                id = request.session
                return render(request, 'portfolio/portfolio.html')
            else:
                return render(request, 'portfolio/index.html')
    # if GET request, create blank Login form
    else:
        form = Login()
    return render(request, 'portfolio/login.html', {'form': form})
