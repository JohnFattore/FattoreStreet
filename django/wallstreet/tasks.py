from wallstreet.models import Selection, Option
from celery import shared_task

@shared_task
def test(sunday):
    Option.objects.create(ticker="VTI", sunday=sunday)
    print("Hello Spike")

@shared_task
def startWeek(sunday):
    Option.objects.create(ticker="VO", sunday=sunday, startPrice=200)
    Option.objects.create(ticker="SPY", sunday=sunday, startPrice=500)
    Option.objects.create(ticker="VB", sunday=sunday, startPrice=175)
    # set Price
    # gotta make an API here for price
    #quote = 300
    #options = Option.objects.filter(sunday=sunday)
    #options.update(startPrice=quote)
    print("Creating Weekly Options")

@shared_task
def endWeek(sunday):
    # gotta make an API here for price
    quote = 325
    options = Option.objects.filter(sunday=sunday)
    options.update(endPrice=quote)
    # rank
    options = Option.objects.filter(sunday=sunday)
    options.all().order_by('percentChange').values()
    # sort options, assign rank
    options.all().update(rank=2)
    print("Setting End Price and Rank")