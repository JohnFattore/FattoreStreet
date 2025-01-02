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


# game should reset at the beginnong of before hours trading on first trading day of the week (usually monday)
app.conf.beat_schedule = {
#'Test Kaggle': {
# 'task': 'indexCompare.tasks.createMarketCapIndex',
# 'schedule': crontab(hour=00, minute=53),
# 'args': (1000,),
#   },
'Test Kaggle2': {
 'task': 'portfolio.tasks.SnP500PriceUpdate',
 'schedule': crontab(hour=17, minute=23),
   },
}