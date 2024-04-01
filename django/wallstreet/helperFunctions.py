from datetime import date, timedelta
import environ

env = environ.Env()
environ.Env.read_env()

# last sunday = 0
def getSunday(week):
    today = date.today()
    lastSunday = today - (timedelta(((today - timedelta(days=int(env("CUTOVER_ISOWEEKDAY")), hours=int(env("CUTOVER_HOUR")))).isoweekday()) % 7)) # monday is 1
    return lastSunday + timedelta(week * 7)