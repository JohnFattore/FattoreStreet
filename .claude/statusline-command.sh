#!/bin/bash
# Claude Code status line: shows current directory, git branch, model name, context usage, and session cost.

input=$(cat)

cwd=$(echo "$input" | jq -r '.workspace.current_dir // .cwd')
dir_display=$(basename "$cwd")

branch=""
if git -C "$cwd" --no-optional-locks rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  branch=$(git -C "$cwd" --no-optional-locks branch --show-current 2>/dev/null)
  if [ -z "$branch" ]; then
    branch=$(git -C "$cwd" --no-optional-locks rev-parse --short HEAD 2>/dev/null)
  fi
fi

model=$(echo "$input" | jq -r '.model.display_name // empty')
cost=$(echo "$input" | jq -r '.cost.total_cost_usd // empty')
ctx_pct=$(echo "$input" | jq -r '.context_window.used_percentage // empty')

# Colors
BLUE="\033[34m"
GREEN="\033[32m"
YELLOW="\033[33m"
MAGENTA="\033[35m"
RED="\033[31m"
CYAN="\033[36m"
RESET="\033[0m"

parts=()
parts+=("${BLUE}${dir_display}${RESET}")

if [ -n "$branch" ]; then
  parts+=("${GREEN}${branch}${RESET}")
fi

if [ -n "$model" ]; then
  parts+=("${MAGENTA}${model}${RESET}")
fi

if [ -n "$ctx_pct" ]; then
  ctx_int=${ctx_pct%%.*}
  if [ "$ctx_int" -ge 80 ]; then
    ctx_color="$RED"
  elif [ "$ctx_int" -ge 50 ]; then
    ctx_color="$YELLOW"
  else
    ctx_color="$CYAN"
  fi
  parts+=("${ctx_color}${ctx_int}% ctx${RESET}")
fi

if [ -n "$cost" ]; then
  cost_fmt=$(printf '$%.4f' "$cost")
  parts+=("${YELLOW}${cost_fmt}${RESET}")
fi

sep=" | "
out=""
for p in "${parts[@]}"; do
  if [ -z "$out" ]; then
    out="$p"
  else
    out="$out$sep$p"
  fi
done

printf "%b\n" "$out"
