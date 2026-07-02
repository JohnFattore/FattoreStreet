# FROM python:3.12-slim
FROM python:3.14-slim

# set a directory for this app
WORKDIR /django/
ENV PYTHONUNBUFFERED=1

# AWS CLI v2 + jq so the entrypoint can pull config from Secrets Manager at start.
# uname -m -> aarch64 | x86_64, which matches the AWS CLI download names exactly.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip jq ca-certificates \
    && curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/aws /tmp/awscliv2.zip /var/lib/apt/lists/*

# install dependencies first for better layer caching
COPY requirements.txt .
RUN pip3 install -r requirements.txt --no-cache-dir
#    pip3 install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

# copy the rest of the app
COPY . .
RUN chmod +x /django/docker-entrypoint.sh

# Entrypoint loads SECRETS_ARN from Secrets Manager (if set), then runs the CMD.
ENTRYPOINT ["/django/docker-entrypoint.sh"]
# CMD ["gunicorn", "-b", "0.0.0.0:8000", "mysite.wsgi"]
CMD ["gunicorn", "-b", "0.0.0.0:8000", "--workers", "1", "--threads", "2", "mysite.wsgi"]