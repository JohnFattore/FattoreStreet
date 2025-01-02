FROM python:3.10

# Set work directory
WORKDIR /celery/

# Install system dependencies
# RUN apt-get update && apt-get install -y gcc libpq-dev && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY ../django/requirements.txt /celery/
RUN pip install --no-cache-dir -r requirements.txt

# Copy the entire project into the container
COPY ../django /celery/django

# Set environment variables for Django
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

# Celery command to run worker
CMD ["celery", "-A", "mysite", "worker", "--loglevel=info"]