#!/bin/sh
docker network create --driver bridge dockerNet
docker run --name django --network dockerNet -d johnfattore/django
docker run --name nginx --network dockerNet -p 80:80 -d johnfattore/nginx
# docker run --name postgres --network dockerNet -e POSTGRES_PASSWORD=postgres -d postgres
# obtain SSL cert
docker run -it --rm --name certbot --network dockerNet -p 80:80 -p 443:443 -d certbot/certbot certonly --webroot -w /var/www/certbot fattorestreet.com

# Copy into volume
docker run --rm -v volume_name:/container_path -v $(pwd):/host busybox cp /host/yourfile /container_path/yourfile

# Plan
# Copy certbot files from certbot container
# Copy nginx files from nginx container

# file locations needed: /etc/letsencrypt, /var/www/certbot, /var/lib/letsencrypt, /etc/nginx/conf