from django import forms

class Register(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password1 = forms.CharField(label='Password', max_length=100)
    password2 = forms.CharField(label='Match Password', max_length=100)
    email = forms.EmailField()
    fname = forms.CharField(label='First Name', max_length=100)
    lname = forms.CharField(label='Last Name', max_length=100)

class Login_user(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password = forms.CharField(label='Password', max_length=100)

class Stock_buy(forms.Form):
    ticker_text = forms.CharField(label='Stock', max_length=4)
    shares_integer = forms.DecimalField(label='Shares', decimal_places=5, max_digits=10)
    costbasis_price = forms.DecimalField(label='Cost Basis', decimal_places=2, max_digits=10)
    buy_date = forms.DateField(label='Date Bought')

class Stock_sell(forms.Form):
    buy_date = forms.DateField(label='Date Bought')