FROM python:3.10

# set a directory for this app
WORKDIR /django/
# copy all the files to the container
COPY . .
ENV PYTHONUNBUFFERED=1
# install dependencies
RUN pip3 install -r requirements.txt --no-cache-dir
# EXPOSE 8000

# run development server
# CMD ["python", "manage.py", "runserver", "127.0.0.1:8000"]
# run Gunicorn HTTP server
# just make this an agruement to the docker build command
CMD ["gunicorn", "-b", "0.0.0.0:8000", "mysite.wsgi"]

# Define the build argument for CMD
#ARG CMD_ARG

# Use the CMD_ARG for the CMD
#CMD ${CMD_ARG}