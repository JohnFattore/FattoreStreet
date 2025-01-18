from celery import shared_task

@shared_task
def SnP500PriceUpdate():
    print("HEllo World")