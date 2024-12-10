#!/bin/sh
# network
sudo docker network create --driver bridge dockerNet

# postgres
sudo docker run -d \
  --name postgres \
  --network dockerNet \
  -e POSTGRES_PASSWORD=postgres \
  -v /mnt/ebs/postgres-data:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres

# django
sudo docker run --name django --network dockerNet -d \
  -e DATABASE=postgresDocker \
  -e DEBUG=False \
  johnfattore/django

# nginx
sudo docker run --name nginx --network dockerNet -p 80:80 -d johnfattore/nginx

# obtain SSL cert
docker run -it --rm --name certbot --network dockerNet -p 80:80 -p 443:443 -d certbot/certbot certonly --webroot -w /var/www/certbot fattorestreet.com

# Copy into volume
docker run --rm -v volume_name:/container_path -v $(pwd):/host busybox cp /host/yourfile /container_path/yourfile

# Certbot
docker run --rm \
  -v /path/to/certbot/conf:/etc/letsencrypt \
  -v /path/to/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --webroot \
  -w /var/www/certbot \
  -d yourdomain.com -d www.yourdomain.com

# nginx with Certbot
sudo docker run --name nginx --network dockerNet -p 80:80 -p 443:443 -d johnfattore/nginx

# Plan
# Copy certbot files from certbot container
# Copy nginx files from nginx container

# file locations needed: /etc/letsencrypt, /var/www/certbot, /var/lib/letsencrypt, /etc/nginx/conf

sudo docker stop postgres
sudo docker container rm postgres
sudo docker image rm postgres


sudo docker stop django
sudo docker container rm django
sudo docker image rm johnfattore/django


sudo docker stop nginx
sudo docker container rm nginx
sudo docker image rm johnfattore/nginx

# django RDS
sudo docker run --name django --network dockerNet -d \
  -e HOST=postgres.cpqoakmk692s.us-east-1.rds.amazonaws.com  \
  -e USERNAME=postgres \
  -e PASSWORD_RDS=OOjf7vvsOd0zaPTK3jhh \
  -e DATABASE=postgresRDS \
  johnfattore/django