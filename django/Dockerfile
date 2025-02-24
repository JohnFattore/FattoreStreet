# FROM python:3.12-slim
FROM python:3.10-slim

# set a directory for this app
WORKDIR /django/
# copy all the files to the container
COPY . .
ENV PYTHONUNBUFFERED=1

RUN pip3 install -r requirements.txt --no-cache-dir

RUN pip3 install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

# CMD ["gunicorn", "-b", "0.0.0.0:8000", "mysite.wsgi"]
CMD ["gunicorn", "-b", "0.0.0.0:8000", "--workers", "1", "--threads", "2", "mysite.wsgi"]