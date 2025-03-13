#!/bin/sh
# network
sudo docker network create --driver bridge dockerNet

# postgres
sudo docker run -d \
  --name postgres \
  --network dockerNet \
  -e POSTGRES_PASSWORD=postgres \
  -v /mnt/ebs/postgres-data:/var/lib/postgresql/data \
  postgres

# django
sudo docker run --name django --network dockerNet -d \
  -e DATABASE=postgresDocker \
  -e DEBUG=False \
  -e REDIS_URL=redis://redis:6379 \
  johnfattore/django

# nginx
sudo docker run --name nginx --network dockerNet \
  -v /mnt/ebs/certbot/letsencrypt:/etc/letsencrypt \
  -v /mnt/ebs/certbot/www:/var/www/certbot \
  -p 80:80 -p 443:443 \
  -d johnfattore/nginx

# Celery beat worker  
sudo docker run \
  --name celery \
  --network dockerNet \
  -e DATABASE=postgresDocker \
  -e DEBUG=False \
  -e REDIS_URL=redis://redis:6379 \
  -d johnfattore/django \
  celery -A mysite worker --beat -E -n beat

# Celery worker
sudo docker run \
  --name celery \
  --network dockerNet \
  -e DATABASE=postgresDocker \
  -e DEBUG=False \
  -e REDIS_URL=redis://redis:6379 \
  -d johnfattore/django \
  celery -A mysite worker -E -n worker

# Redis
sudo docker run --network dockerNet --name redis -d -p 6379:6379 redis

# Certbot get SSL cert
sudo docker run --rm \
  --name certbot \
  --network dockerNet \
  -v /mnt/ebs/certbot/letsencrypt:/etc/letsencrypt \
  -v /mnt/ebs/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --webroot \
  -w /var/www/certbot \
  -d fattorestreet.com \
  --email johnefattore@gmail.com \
  --agree-tos

# watchtower for updates
sudo docker run -d \
  --name watchtower \
  --network dockerNet \
  -e WATCHTOWER_POLL_INTERVAL=300 \
  -e WATCHTOWER_CLEANUP=true \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower


sudo docker stop postgres
sudo docker container rm postgres
sudo docker image rm postgres


sudo docker stop django
sudo docker container rm django
sudo docker image rm johnfattore/django


sudo docker stop nginx
sudo docker container rm nginx
sudo docker image rm johnfattore/nginx


sudo docker stop redis
sudo docker container rm redis


sudo docker stop celery
sudo docker container rm celery

# exec into django
sudo docker exec -it django bash

# migrate database changes
python3 manage.py migrate