FROM nginx:1.25

# remove default, not needed files from image
RUN rm /etc/nginx/nginx.conf /etc/nginx/conf.d/default.conf
# copy configuration into image
COPY nginx.conf /etc/nginx/nginx.conf
#RUN  nginx
COPY index.html /var/www/index.html
COPY dist var/www/dist