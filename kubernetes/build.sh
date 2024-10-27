#!/bin/sh
docker build -t johnfattore/nginx ../nginx
docker push johnfattore/nginx
docker build -t johnfattore/django ../django
docker push johnfattore/django