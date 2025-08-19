import requests
import pandas as pd

CIK = "0000320193"#"0000789019" #"0000320193"  # Apple Inc CIK
HEADERS = {"User-Agent": "johnefattore@gmail.com"}  # SEC requires a contact

url = f"https://data.sec.gov/api/xbrl/companyconcept/CIK{CIK}/us-gaap/NetIncomeLoss.json"
net_income = requests.get(url, headers=HEADERS).json()
reports = net_income["units"]["USD"]
reports.reverse()
count = 4
quarters_seen = 0
net_income = 0
annual_hit = False
for report in reports:
    if "frame" in report:
        if not annual_hit:
            net_income += report["val"]
            quarters_seen += 1
        else:
            net_income -= report["val"]
            count -= 1
        if report["fp"] == "FY":
            count = quarters_seen - 1
            annual_hit = True

    if count <= 0:
        break
print(net_income)

url = f"https://data.sec.gov/api/xbrl/companyconcept/CIK{CIK}/us-gaap/Revenues.json"
revenue = requests.get(url, headers=HEADERS).json()
reports = revenue["units"]["USD"]
count = 4
quarters_seen = 0
revenue = 0
annual_hit = False
for report in reports:
    if "frame" in report:
        print(report)
        if not annual_hit:
            revenue += report["val"]
            quarters_seen += 1
        else:
            revenue -= report["val"]
            count -= 1
        if report["fp"] == "FY":
            count = quarters_seen - 1
            annual_hit = True

    if count <= 0:
        break
print(revenue)