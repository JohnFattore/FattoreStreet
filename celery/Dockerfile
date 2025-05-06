
#unused, celery container run off django image

FROM python:3.10

# Set work directory
WORKDIR /celery/
# copy all the files to the container

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*

COPY django .
ENV PYTHONUNBUFFERED=1
RUN pip3 install --upgrade pip
# Install Python dependencies
RUN pip3 install -r django/requirements.txt --no-cache-dir

# Celery command to run worker
CMD ["celery", "-A", "mysite", "worker", "--loglevel=info"]
