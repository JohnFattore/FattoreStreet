# Portfolio-Manager
Django Web App

Migrate new portfolio model
    python3 manage.py makemigrations portfolio
    python3 manage.py migrate

Running the Server
CD into mysite folder and run the command line prompt
    python3 manage.py runserver

Running in docker container
CD into (Portfolio-Manager)? and run command on CLI
    docker run -p 8000:8000 -d torkfat/django

# Requirements for Website
    Store users' portfolio holdings, allocation preferences
    Display a users portfolio and allocation
    Allow users to input new assets, new allocation preferences, and existing Schwab, Vanguard, or Fidelity portfolios
    Automatically inputs mutual fund transcations, maybe other assets, APIs?

# Variable Naming Conventions
## Django backend variables are all lowercase with an underscore seperating words
    examples: ticker_text, shares_number, user_key
    common fields
    CharField: text
    DecimalField: number
    DateTimeField: date
    Foreign Key: key
## Python local variable will follow conventional camelcase, no type

DELETE LATER
spike
spike123