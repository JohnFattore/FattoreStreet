from django.shortcuts import render, get_object_or_404
from django.http import HttpResponse
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from .forms import Register, Login_user, Stock_buy, Stock_CSV_buy, Stock_sell, Logout_user
from django.db import models
from .models import Asset as Asset
import csv

# function to read CSV files
def read_csv(file_path):
    with open(file_path, 'r') as f:
        reader = csv.reader(f)
        return list(reader)