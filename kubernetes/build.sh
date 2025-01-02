#!/bin/sh
docker build -t johnfattore/nginx .
docker push johnfattore/nginx

docker build -t johnfattore/django .
docker push johnfattore/django

docker build -f celery/Dockerfile -t johnfattore/celery .
docker push johnfattore/celery