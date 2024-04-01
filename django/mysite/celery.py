import os
from celery import Celery
from celery.schedules import crontab
import environ
from wallstreet.helperFunctions import getSunday

env = environ.Env()
environ.Env.read_env()
# Set the default Django settings module for the 'celery' program.
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'mysite.settings')
app = Celery('mysite')
app.config_from_object('django.conf:settings', namespace='CELERY')

# Load task modules from all registered Django apps.
app.autodiscover_tasks()

# if current date is sunday, that sunday is lastSunday
lastSunday = getSunday(0)
nextSunday = getSunday(1)

# game should reset at the beginnong of before hours trading on first trading day of the week (usually monday)
app.conf.beat_schedule = {
'Start Week': {
 'task': 'wallstreet.tasks.startWeek',
 'schedule': crontab(day_of_week=env("CUTOVER_WEEKDAY"), hour=int(env("CUTOVER_HOUR")), minute=int(env("CUTOVER_MINUTE")) + 1),
 'args': (nextSunday,),
 },
'End Week': {
 'task': 'wallstreet.tasks.endWeek',
 'schedule': crontab(day_of_week=env("CUTOVER_WEEKDAY"), hour=int(env("CUTOVER_HOUR")), minute=int(env("CUTOVER_MINUTE")) + 1),
 'args': (lastSunday,),
 },
}