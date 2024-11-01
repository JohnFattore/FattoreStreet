#!/bin/sh
docker network create --driver bridge dockerNet
docker run --name django --network dockerNet -d johnfattore/django
docker run --name nginx --network dockerNet -p 80:80 -d johnfattore/nginx
# docker run --name postgres --network dockerNet -e POSTGRES_PASSWORD=postgres -d postgres