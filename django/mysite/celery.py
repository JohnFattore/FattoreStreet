import os
from celery import Celery
from celery.schedules import crontab
from datetime import date, timedelta

# Set the default Django settings module for the 'celery' program.
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'mysite.settings')
app = Celery('mysite')
app.config_from_object('django.conf:settings', namespace='CELERY')

# Load task modules from all registered Django apps.
app.autodiscover_tasks()

# if current date is sunday, that sunday is lastSunday
today = date.today()
lastSunday = today - (timedelta((today.weekday() + 1) % 7)) # monday is 0
nextSunday = lastSunday + timedelta(7)

app.conf.beat_schedule = {
'Start Week': {
 'task': 'wallstreet.tasks.startWeek',
 'schedule': crontab(day_of_week='sun', hour=5, minute=0),
 'args': (nextSunday,),
 },
'End Week': {
 'task': 'wallstreet.tasks.endWeek',
 'schedule': crontab(day_of_week='sun', hour=5, minute=0),
 # should be lastSunday FYI
 'args': (lastSunday,),
 },
}