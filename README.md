# Portfolio Manager, a Django RESTful API backend
## Changing database schema
Stage the migrations and commit changes to database. Portfolio is the name of the app.
```
python3 manage.py makemigrations portfolio
python3 manage.py migrate
```
## Running the development server
    python3 manage.py runserver

## Create Docker Image
    docker build -t django ./
## Create Docker Container from Image
    docker run -d -p 8000:8000 django

## Requirements for Website
- Store users' portfolio holdings, allocation preferences
- Display a users portfolio and allocation
- Allow users to input new assets, new allocation preferences, and existing Schwab Vanguard, or Fidelity portfolios
- Automatically inputs mutual fund transcations, maybe other assets, APIs?

## Variable Naming Conventions
### PostgreSQL database variables are all lowercase with an underscore seperating words
    examples: ticker_text, shares_number, user_key
    common fields
    CharField: text
    DecimalField: number
    DateTimeField: date
    Foreign Key: key
### Python local variable will follow conventional camelcase, no type

## Gunicorn, NGINX, PostgreSQL, and Docker
 - Django based web app is run on a WSGI Gunicorn server
 - TODO: Revere proxy NGINX Server sits infront of Gunicorn server
 - TODO: PostgreSQL database server stores permanant data
 - All programs/apps are run in individual Docker containers