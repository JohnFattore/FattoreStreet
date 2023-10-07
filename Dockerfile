FROM python:3.10

# set a directory for this app
WORKDIR /Portfolio-Manager/mysite/
# copy all the files to the container
COPY . .
ENV PYTHONUNBUFFERED=1
# install dependencies
RUN pip3 install -r requirements.txt --no-cache-dir
EXPOSE 8000

CMD ["python3", "mysite/manage.py", "runserver", "0.0.0.0:8000"]