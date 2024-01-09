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

## Variable Naming Conventions
 - PostgreSQL database variables follow django naming conventions, all lowercase with underscores, not camelCase
 - Other Python local variable will follow conventional camelCase, no Hungarian Notation!

## Gunicorn, NGINX, PostgreSQL, and Docker
 - Django based web app is run on a WSGI Gunicorn server
 - Revere proxy NGINX Server sits infront of Gunicorn server
 - PostgreSQL database server stores permanant data
 - All programs/apps are run in individual Docker containers

## Hosting on AWS with Fargate
 - A fargate task definition with all containers enables cloud hosting
 - Database hosting TBD