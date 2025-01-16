#!/bin/sh
docker build -t johnfattore/nginx .
docker push johnfattore/nginx

docker build -t johnfattore/django .
docker push johnfattore/django