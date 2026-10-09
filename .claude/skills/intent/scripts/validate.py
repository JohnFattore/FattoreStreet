#!/usr/bin/env python3
"""Validate the intent tree: every branch has a README, every node has a why, links resolve.

Exits 1 when any error is found.
"""

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

REQUIREMENT_HEADING = re.compile(r"^### \S")
WHY_HEADING = re.compile(r"^## Why\s*$", re.MULTILINE)
LINK = re.compile(r"\]\(([^)\s]+)\)")
INFERRED = re.compile(r"\(inferred", re.IGNORECASE)


@dataclass
class Node:
    path: Path
    text: str

    @property
    def requirements(self) -> int:
        return sum(1 for line in self.text.splitlines() if REQUIREMENT_HEADING.match(line))

    @property
    def inferred(self) -> int:
        return len(INFERRED.findall(self.text))

    @property
    def open_questions(self) -> int:
        match = re.search(r"^## Open questions\s*$(.*?)(?=^## |\Z)", self.text, re.MULTILINE | re.DOTALL)
        return len(re.findall(r"^- ", match.group(1), re.MULTILINE)) if match else 0


def blank_code_blocks(text: str) -> str:
    """Blank fenced code blocks so examples are not parsed as content."""
    lines = text.splitlines()
    fenced = False
    for index, line in enumerate(lines):
        if line.lstrip().startswith("```"):
            fenced = not fenced
            lines[index] = ""
        elif fenced:
            lines[index] = ""
    return "\n".join(lines)


def load_tree(root: Path) -> list[Node]:
    return [
        Node(path, blank_code_blocks(path.read_text(encoding="utf-8")))
        for path in sorted(root.rglob("*.md"))
    ]


def check(root: Path, nodes: list[Node]) -> list[str]:
    errors = []
    if not (root / "README.md").exists():
        errors.append("README.md: the root node is missing")
    for directory in sorted(p for p in root.rglob("*") if p.is_dir()):
        if not (directory / "README.md").exists():
            errors.append(f"{directory.relative_to(root)}/: branch has no README.md")
    exempt = {root / "README.md", root / "principles.md"}
    for node in nodes:
        relative = node.path.relative_to(root)
        if node.path not in exempt and not WHY_HEADING.search(node.text):
            errors.append(f"{relative}: no '## Why' section")
        for target in LINK.findall(node.text):
            if re.match(r"^[a-z]+:", target) or target.startswith("#"):
                continue
            if not (node.path.parent / target.split("#")[0]).exists():
                errors.append(f"{relative}: broken link {target}")
    return errors


def print_summary(root: Path, nodes: list[Node]) -> None:
    print(f"{'node':<45} {'reqs':>5} {'inferred':>9} {'open q':>7}")
    for node in nodes:
        relative = str(node.path.relative_to(root))
        print(f"{relative:<45} {node.requirements:>5} {node.inferred:>9} {node.open_questions:>7}")
    print()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=None, help="intent tree root (default: <repo>/intent)")
    parser.add_argument("--summary", action="store_true", help="print per-node counts")
    args = parser.parse_args()

    if args.root is None:
        repo = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True
        ).stdout.strip()
        args.root = Path(repo) / "intent"
    root = args.root.resolve()
    if not root.is_dir():
        print(f"error: {root} does not exist", file=sys.stderr)
        return 1

    nodes = load_tree(root)
    if args.summary:
        print_summary(root, nodes)
    errors = check(root, nodes)
    for error in errors:
        print(f"error: {error}")
    print(f"{len(nodes)} nodes, {len(errors)} errors")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
