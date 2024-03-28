start redis container
    docker run -d -p 6379:6379 redis

start flower, no container, no auth
    celery -A mysite flower

start celery worker
    celery -A mysite worker -E -n worker

start celery beat worker
    celery -A mysite worker --beat -E -n beat