docker run -d -p 6379:6379 redis

### Run Celery Beat Worker
celery -A mysite worker --beat -E -n beat

# Portfolio Manager, a Django RESTful API backend
## Changing database schema
Stage the migrations and commit changes to database. Portfolio is the name of the app.
```
python3 manage.py makemigrations
python3 manage.py migrate
```
## Running the development server
    python3 manage.py runserver

## Building images and deploying containers to production
The kubernetes folder contains the necessary scripts for building and deployment. The build.sh script builds all containers, while the run.sh script runs the containers in production.

## Gunicorn, NGINX, PostgreSQL, and Docker
 - Django based web app is run on a WSGI Gunicorn server
 - Revere proxy NGINX Server sits infront of Gunicorn server
 - PostgreSQL database server stores permanant data
 - All programs/apps are run in individual Docker containers

## Hosting on AWS with EC2
The whole project is hosted on a EC2 instance keeping the project cloud agnostic and cost effective.

## Testing
Tests are broken up into three rough types: Unit, Integration, and End to End (E2E).
The app is small now so not many E2E tests exist, just heavy integration tests
Unit Tests use APIRequestFactory to test the view directly
Integration Tests use APIClient to more wholy test the app
E2E tests like integration tests use APIClient, but are more of a story... idk what ill do for these

## Porfolio
The main app in the project featuring a mock portfolio builder. Stocks can be bought/sold and their performance can be compared to popular benchmarks such as the S&P 500.

## Chatbot
A boglehead chatbot prompted to deliver investing advice based on buying and holding low cost index funds.

## Restaurants
This app allows users to write reviews for restaurants and then get recommendations based on their reviews.