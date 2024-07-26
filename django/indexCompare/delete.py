import yfinance as yf
import requests
import json

# import required module
import os
# assign directory
directory = 'django/EDGAR/companyfacts'
 
# iterate over files in
# that directory
'''
for filename in os.listdir(directory):
    with open(os.path.join(directory, filename)) as f:
        if (filename == 'CIK0000789019.json'):
            data = json.load(f)
            floatSharesEntries = data["facts"]["dei"]["EntityPublicFloat"]["units"]["USD"]
            outstandingSharesEntries = data["facts"]["dei"]["EntityCommonStockSharesOutstanding"]["units"]["shares"]
            floatShares = floatSharesEntries[len(floatSharesEntries) - 1]["val"]
            outstandingShares = outstandingSharesEntries[len(outstandingSharesEntries) - 1]["val"]
            print(data["facts"]["dei"]["EntityPublicFloat"]["units"]["USD"])
            #print("Freefloat %", floatShares / outstandingShares)
            #print("Float Shares:", floatShares, "Outstanding Shares", outstandingShares)
'''
response = requests.get('https://finnhub.io/api/v1/stock/financials-reported?cik=1551152&freq=quarterly&token=ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0')
print(response.json()["data"][0]["endDate"])