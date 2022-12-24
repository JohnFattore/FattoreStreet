from django.shortcuts import render
from django.http import HttpResponse
from django.contrib.auth import authenticate, login
from django.contrib.auth.models import User

# Create your views here.
def index(request):
    return render(request, 'portfolio/index.html')

def portfolio(request):
    return render(request, 'portfolio/portfolio.html')

def register(request):
    return render(request, 'portfolio/register.html')

def login(request):
    return render(request, 'portfolio/login.html')

#def register(request):
 #   user = User.objects.create_user(username=FROM FORM, password='FROM FORM')
   # user.save()
  #  return render(request, 'portfolio/create_user.html')