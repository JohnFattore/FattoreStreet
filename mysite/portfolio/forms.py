from django import forms

# form for register view
class Register(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password1 = forms.CharField(label='Password', max_length=100)
    password2 = forms.CharField(label='Match Password', max_length=100)
    email = forms.EmailField()
    fname = forms.CharField(label='First Name', max_length=100)
    lname = forms.CharField(label='Last Name', max_length=100)

# form for Login_user view
class Login_user(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password = forms.CharField(label='Password', max_length=100)

# form for Stock_buy view
class Stock_buy(forms.Form):
    ticker_text = forms.CharField(label='Stock', max_length=5)
    shares_integer = forms.DecimalField(label='Shares', decimal_places=5, max_digits=10)
    costbasis_price = forms.DecimalField(label='Cost Basis', decimal_places=2, max_digits=10)
    ACCOUNT_CHOICES= [('roth_ira', 'Roth Ira'),('individual', 'Individual')]
    account = forms.CharField(label="Account", max_length=100, widget=forms.Select(choices=ACCOUNT_CHOICES))
    buy_date = forms.DateField(label='Date Bought')

# form for Stock_CSV_buy view
class Stock_CSV_buy(forms.Form):
    file = forms.FileField()

# form for Stock_sell view
class Stock_sell(forms.Form):
    buy_date = forms.DateField(label='Date Bought')

# form for schedule view
class Schedule(forms.Form):
    DURATION_CHOICES= [('3_months', '3 Months'), ('6_months', '6 Months'), ('1_year', '1 Year')]
    duration = forms.CharField(label="Duration", max_length=100, widget=forms.Select(choices=DURATION_CHOICES))
    amount_biweekly = forms.DecimalField(label='Amount Biweekly', decimal_places=2, max_digits=10)

# form for Logout_user view
class Logout_user(forms.Form):
    username = forms.CharField(label='Username', max_length=100)


    