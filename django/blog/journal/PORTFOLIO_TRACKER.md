# Spec-Driven Development with a Google Sheets Portfolio Tracker

_FattoreStreet @ [`1324ec2e`](https://github.com/JohnFattore/FattoreStreet/tree/1324ec2e79ae4996dfa4c8153416508a625ce84c) — 2026-11-11_

I built a portfolio tracker in Google Sheets using spec-driven development (SDD). Spec-driven development shifts the work. Instead of writing code and documenting it afterward, you write down what the software should do and why. The implementation follows from that document, and the document stays the source of truth. You can make your own copy of the tracker for free at https://docs.google.com/spreadsheets/d/1gTdUHR769WEa58gfhRSP76oVkD_UiX766xkiaohZCRc/copy. It opens on a Start here tab that walks through setup.

## What does the tracker do?

The sheet has grown to twelve tabs. It imports my Fidelity positions, pulls live prices from Google Finance, and breaks the portfolio down by asset class and by account. It also compares my mix against target allocations, turns regular contributions into a buy plan, and projects the next 30 years. Every one of those numbers is described in plain language in the spec.

## How does the spec stay in sync?

Claude is connected to my Google Drive, and the project has three pieces. A Google Sheet is the portfolio tracker. A Google Doc spec describes what every tab, input and formula in that sheet is for. A custom Claude skill ties them together, so the spec and the sheet always change together. The doc is the intent. The sheet is the product.

## Why not a full SDD framework?

There are frameworks built around this, like GitHub Spec Kit and BMAD. They add commands and agents that structure the process into plans, tasks and functional requirements. I find it all too heavy. I prefer a lightweight version of SDD where I keep just the spec. Intent is the most important part for humans to write and review. The code follows.

## The same idea runs Fattore Street

The same idea runs fattorestreet.com. The repo has an intent folder that says why each part of the site exists and what it must do, and the code is derived from it. You can read it at https://github.com/JohnFattore/FattoreStreet/tree/main/intent.

Write down the why, and the code follows.
