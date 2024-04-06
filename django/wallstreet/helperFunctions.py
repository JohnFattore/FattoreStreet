from datetime import datetime, timedelta
import environ

env = environ.Env()
environ.Env.read_env()

# last sunday = 0
def getSunday(week):
    today = datetime.now()
    offset = timedelta(days=int(env("CUTOVER_ISOWEEKDAY")), hours=int(env("CUTOVER_HOUR")))
    cutoff = today - offset
    thisSunday = cutoff - timedelta(days=cutoff.isoweekday() % 7)
    sunday = thisSunday + timedelta(week * 7)
    return str(sunday.year) + '-' + str(sunday.month) + '-' + str(sunday.day)