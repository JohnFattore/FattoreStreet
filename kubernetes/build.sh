#!/bin/sh
docker build -t johnfattore/nginx .
docker push johnfattore/nginx

docker build -t johnfattore/django .
docker push johnfattore/django

#docker build --build-arg CMD_ARG='["celery", "-A", "mysite", "worker", "--loglevel=info"]' -t johnfattore/celery .
#docker push johnfattore/celery