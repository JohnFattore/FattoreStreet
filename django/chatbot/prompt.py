"""Builds the advisor's system instruction from the Markdown files in context/.

Every *.md file in context/ is included, in filename order, so adding a reference document is
just dropping a file in (prefix it with a number to place it, e.g. 20_tax_limits_2026.md).
"""
from datetime import date
from functools import cache
from pathlib import Path

CONTEXT_DIR = Path(__file__).resolve().parent / "context"


@cache
def reference_text() -> str:
    """The context files joined in order; read once per process, since they ship in the image."""
    files = sorted(CONTEXT_DIR.glob("*.md"))
    if not files:
        raise FileNotFoundError(f"No advisor context files in {CONTEXT_DIR}")
    return "\n\n".join(path.read_text(encoding="utf-8").strip() for path in files)


def build_system_prompt(today: date | None = None) -> str:
    """The reference text plus today's date, so the model knows which tax year it is in."""
    today = today or date.today()
    return f"{reference_text()}\n\n# Today\n\nToday's date is {today.isoformat()}."
