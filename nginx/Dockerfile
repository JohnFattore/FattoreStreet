FROM nginx:1.25

# copy configuration into image
# COPY nginx.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/nginx.conf
COPY index.html /var/www/index.html
COPY dist/ var/www/dist
COPY static var/www/static