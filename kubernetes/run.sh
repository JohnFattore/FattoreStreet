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
  johnfattore/django

# nginx
sudo docker run --name nginx --network dockerNet \
  -v /mnt/ebs/certbot/letsencrypt:/etc/letsencrypt \
  -v /mnt/ebs/certbot/www:/var/www/certbot \
  -p 80:80 -p 443:443 \
  -d johnfattore/nginx

# Celery
sudo docker run \
  --name celery \
  --network my-dockerNet \
  -e CELERY_BROKER_URL=redis://redis-container:6379/0 \
  -e DJANGO_SETTINGS_MODULE=mysite.settings \
  -d johnfattore/celery

# Redis
sudo docker run --network dockerNet --name redis -d redis

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


sudo docker stop postgres
sudo docker container rm postgres
sudo docker image rm postgres


sudo docker stop django
sudo docker container rm django
sudo docker image rm johnfattore/django


sudo docker stop nginx
sudo docker container rm nginx
sudo docker image rm johnfattore/nginx


# can probably remove this
# django RDS
sudo docker run --name django --network dockerNet -d \
  -e HOST=postgres.cpqoakmk692s.us-east-1.rds.amazonaws.com  \
  -e USERNAME=postgres \
  -e PASSWORD_RDS=OOjf7vvsOd0zaPTK3jhh \
  -e DATABASE=postgresRDS \
  johnfattore/django