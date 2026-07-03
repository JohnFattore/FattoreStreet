FROM python:3.14-slim

COPY --from=ghcr.io/astral-sh/uv:0.11.26 /uv /uvx /bin/

# set a directory for this app
WORKDIR /django/
ENV PYTHONUNBUFFERED=1
# Use the base image's Python; never download a uv-managed interpreter.
ENV UV_PYTHON_DOWNLOADS=never

# AWS CLI v2 + jq so the entrypoint can pull config from Secrets Manager at start.
# uname -m -> aarch64 | x86_64, which matches the AWS CLI download names exactly.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip jq ca-certificates \
    && curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/aws /tmp/awscliv2.zip /var/lib/apt/lists/*

# install dependencies first for better layer caching (exact versions from uv.lock)
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-cache --compile-bytecode

# copy the rest of the app (.venv is protected by .dockerignore)
COPY . .
ENV PATH="/django/.venv/bin:$PATH"
RUN chmod +x /django/docker-entrypoint.sh

# Entrypoint loads SECRETS_ARN from Secrets Manager (if set), then runs the CMD.
ENTRYPOINT ["/django/docker-entrypoint.sh"]
# CMD ["gunicorn", "-b", "0.0.0.0:8000", "mysite.wsgi"]
CMD ["gunicorn", "-b", "0.0.0.0:8000", "--workers", "1", "--threads", "2", "mysite.wsgi"]