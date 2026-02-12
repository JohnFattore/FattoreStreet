import os
from celery import Celery
from celery.schedules import crontab
import environ

env = environ.Env()
environ.Env.read_env()
# Set the default Django settings module for the 'celery' program.
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'mysite.settings')
app = Celery('mysite')
app.config_from_object('django.conf:settings', namespace='CELERY')

# Load task modules from all registered Django apps.
app.autodiscover_tasks()


app.conf.timezone = 'US/Eastern'

app.conf.beat_schedule = {
    'Load yfinance Cache': {
        'task': 'portfolio.tasks.load_yfinance_cache',
        'schedule': crontab(hour=4, minute=0),
    },
    'Load FRED Cache': {
        'task': 'portfolio.tasks.load_fred_cache',
        'schedule': crontab(hour=4, minute=0),
    },
}