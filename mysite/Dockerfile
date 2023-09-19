FROM python:3.9

# set a directory for this app
WORKDIR /Portfolio-Manager/mysite/
# copy all the files to the container
COPY . .
# install dependencies
#RUN pip3 install -r requirements.txt --no-cache-dir
RUN pip install Django
# expose local host port
EXPOSE 8000

CMD ["python3", "manage.py", "runserver"]