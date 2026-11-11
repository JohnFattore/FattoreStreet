# A Google Sheets Portfolio Tracker

_FattoreStreet @ [`df8a6378`](https://github.com/JohnFattore/FattoreStreet/tree/df8a6378c8cec3f40f0f79a3b4269435895f2383) — 2026-11-11_

Lately I have been building a portfolio tracker in Google Sheets with Claude. It imports my Fidelity positions and pulls live prices from Google Finance. It shows my whole portfolio across every account in one place. It's free, and you can make your own copy of the template at https://docs.google.com/spreadsheets/d/1gTdUHR769WEa58gfhRSP76oVkD_UiX766xkiaohZCRc/copy. Your copy lives in your own Google Drive and is private to you.

## What does it do?

The tracker has twelve tabs. The Portfolio tab lists every holding with its price, market value, gain or loss and change today, with totals at the top. Allocation splits the portfolio by asset class. Account allocation shows that split for each account side by side. Targets compares your mix against the one you want and shows how many dollars to buy or sell to get back on target. Buy plan turns regular contributions into a buy list that steers new money toward whatever is underweight. That way you can rebalance without selling. Forecast projects the portfolio 30 years out from a growth rate and what you add each year. Some funds hold several asset classes, like a target-date fund in a 401(k). The Fund mix tab splits one of those by its actual mix.

## How do you set it up?

A new copy opens on the Start here tab, which walks through setup in order. Clear out the three example holdings first. Then set up your accounts on the Lists tab, such as Brokerage or Roth IRA. Fidelity users can download their Positions file and import it straight into the Fidelity positions tab, with no typing. Anything held elsewhere goes on the Manual holdings tab as a ticker, shares, average cost and account. Give each ticker an asset class on the Lists tab, and the allocation tabs fill in on their own. Targets and contributions are optional, but they are what make the Buy plan tab useful. The cells you type in have a light yellow fill and blue text. Everything else is a formula.

## Why is there a spec?

The sheet is backed by a Google Doc spec that describes every tab, input and formula in plain language. A custom Claude skill ties the two together, so every change goes into the spec and the sheet at the same time. The doc is the intent. The sheet is the product. It's the same approach behind this site. The intent folder in the repo is the source of truth, and the code is derived from it.

## What it doesn't do

Prices come from Google Finance and can be delayed up to 20 minutes, so this is for tracking, not trading. The return is a simple gain on cost. The sheet doesn't record transactions, dividends, fees or taxes, and it treats everything as US dollars. Fidelity holdings are only as current as your last download, though prices stay live.

Set it up once, and the sheet does the rest.
