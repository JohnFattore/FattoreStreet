from django import forms

class Register(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password1 = forms.CharField(label='Password', max_length=100)
    password2 = forms.CharField(label='Match Password', max_length=100)
    email = forms.EmailField()
    fname = forms.CharField(label='First Name', max_length=100)
    lname = forms.CharField(label='Last Name', max_length=100)

class Login(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password = forms.CharField(label='Password', max_length=100)