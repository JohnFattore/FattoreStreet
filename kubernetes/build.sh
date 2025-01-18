#!/bin/sh
cd ..
cd react-app
npm run build

cd ..
cd nginx
docker build -t johnfattore/nginx .
docker push johnfattore/nginx

cd ..
cd django
docker build -t johnfattore/django .
docker push johnfattore/django