# Portfolio-Manager
Django Web App

# Dependencies
Download Django
    pip install django
Download yfinance
    pip install yfinance
    pip install yfinance --upgrade --no-cache-dir
Download django-cors-headers
    pip install django-cors-headers

Migrate new portfolio model
    python manage.py makemigrations portfolio
    python manage.py migrate

Running the Server
CD into mysite folder and run the command line prompt
    python manage.py runserver