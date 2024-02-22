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

## Gunicorn, NGINX, PostgreSQL, and Docker
 - Django based web app is run on a WSGI Gunicorn server
 - Revere proxy NGINX Server sits infront of Gunicorn server
 - PostgreSQL database server stores permanant data
 - All programs/apps are run in individual Docker containers

## Hosting on AWS with Fargate
 - A fargate task definition with all containers enables cloud hosting
 - Database hosting TBD

## Testing!!
Tests are broken up into three rough types: Unit, Integration, and End to End (E2E).
The app is small now so not many E2E tests exist, just heavy integration tests
Unit Tests use APIRequestFactory to test the view directly
Integration Tests use APIClient to more wholy test the app
E2E tests like integration tests use APIClient, but are more of a story... idk what ill do for these

## Porfolio
The first app I made is portfoilio which utilizes basic CRUD Restful APIs with DRF

## Wallstreet
Second app, using DRF like the previous. This one will be a game
contestants picks top 3 from a set of 10/some number of stocks. Week resets sunday at midnight, so saturday night into sunday morning
stocks are picked by an algorithm from the S&P 500. random, top by volume, top by internet mentions, random weighted towards large stocks
picks could be weighted or not. each pick being equal worth is reasonable, but weighting 1st: 3x, 2nd: 2x, 3rd 1x is also considered