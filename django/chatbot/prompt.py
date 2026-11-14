"""Builds the advisor's system instruction from the Markdown files in context/.

Every *.md file in context/ is included, in filename order, so adding a reference document is
just dropping a file in (prefix it with a number to place it, e.g. 20_tax_limits_2026.md).
"""
from datetime import date, datetime
from functools import cache
from pathlib import Path
from zoneinfo import ZoneInfo

CONTEXT_DIR = Path(__file__).resolve().parent / "context"
# Users are in the US and TIME_ZONE is UTC, so a UTC date would roll over to tomorrow on US evenings,
# which matters on Dec 31 when the prompt tells the model to name the tax year.
USER_TIME_ZONE = ZoneInfo("America/New_York")


@cache
def reference_text() -> str:
    """The context files joined in order; read once per process, since they ship in the image.

    ChatbotConfig.ready() calls this at startup, so an image missing its context files fails at boot
    instead of on the first user question.
    """
    files = sorted(CONTEXT_DIR.glob("*.md"))
    if not files:
        raise FileNotFoundError(f"No advisor context files in {CONTEXT_DIR}")
    return "\n\n".join(path.read_text(encoding="utf-8").strip() for path in files)


def build_system_prompt(today: date | None = None) -> str:
    """The reference text plus today's US Eastern date, so the model knows which tax year it is in."""
    today = today or datetime.now(USER_TIME_ZONE).date()
    return f"{reference_text()}\n\n# Today\n\nToday's date is {today.isoformat()}."


# The figures the company comparison page shows, in its order: key -> (label, kind). Only these reach
# the model, and each value is checked against its kind, so no free text from the request is sent.
COMPARISON_FIGURES: dict[str, tuple[str, str]] = {
    "market_cap": ("Market cap", "money"),
    "market_cap_as_of": ("Market cap as of", "date"),
    "ttm_revenue": ("TTM revenue", "money"),
    "ttm_revenue_yoy": ("TTM revenue YoY growth", "percent"),
    "ttm_gross_profit": ("TTM gross profit", "money"),
    "ttm_operating_income": ("TTM operating income", "money"),
    "ttm_net_income": ("TTM net income", "money"),
    "ttm_net_income_yoy": ("TTM net income YoY growth", "percent"),
    "ttm_operating_cash_flow": ("TTM operating cash flow", "money"),
    "latest_assets": ("Total assets", "money"),
    "latest_liabilities": ("Total liabilities", "money"),
    "latest_equity": ("Total equity", "money"),
    "latest_cash": ("Cash and equivalents", "money"),
    "latest_inventory": ("Inventory", "money"),
    "latest_eps": ("Latest quarterly EPS", "eps"),
    "latest_quarter_end": ("Latest quarter end", "date"),
    "net_margin": ("Net margin", "percent"),
    "gross_margin": ("Gross margin", "percent"),
    "ro_a": ("Return on assets", "percent"),
    "debt_to_assets": ("Debt to assets", "percent"),
    "cash_to_liabilities": ("Cash to liabilities", "percent"),
    "ocf_to_net_income": ("Operating cash flow to net income", "percent"),
}

COMPARISON_SYSTEM_PROMPT = """You write short, plain-language comparisons of two US public companies \
for long-term, index-minded investors who are new to reading financial statements.

You are given a table of figures from each company's SEC filings, plus market cap from SEC share \
counts and exchange closing prices. TTM means trailing twelve months.

Rules:
- Use only the figures in the table. Do not bring in outside facts, news, products or history, and \
never invent a number. If a figure is "not available", say so only if it matters.
- Compare scale, growth, profitability, cash generation and balance-sheet strength, explaining in \
a few words what each difference means.
- Do not say which company is the better investment, recommend buying or selling, give price \
targets, or predict future results.
- Write 3 to 5 short paragraphs or a brief bulleted summary in Markdown, under 300 words. No title."""


def format_figure(value: str | None, kind: str) -> str:
    """A validated figure as the model should read it; money is shortened to T/B/M."""
    if value in (None, "", "N/A"):
        return "not available"
    if kind == "money":
        amount = float(value)
        for divisor, suffix in ((1e12, "T"), (1e9, "B"), (1e6, "M")):
            if abs(amount) >= divisor:
                return f"${amount / divisor:,.2f}{suffix}"
        return f"${amount:,.0f}"
    if kind == "eps":
        return f"${value}"
    return value


def build_comparison_message(companies: list[dict]) -> str:
    """The two companies' figures as a Markdown table, one column per company."""
    a, b = companies
    lines = [
        f"Compare {a['ticker']} and {b['ticker']}.",
        "",
        f"| Figure | {a['ticker']} | {b['ticker']} |",
        "| --- | --- | --- |",
    ]
    for key, (label, kind) in COMPARISON_FIGURES.items():
        lines.append(f"| {label} | {format_figure(a.get(key), kind)} | {format_figure(b.get(key), kind)} |")
    return "\n".join(lines)
