#!/bin/bash
# Claude Code status line: shows current directory, git branch, the branch's PR, model name, context usage, and session cost.

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

# PR for the current branch. `gh` takes ~1s, too slow to run on every redraw,
# so the result is cached per repo+branch and refreshed in the background
# once it is a minute old; a redraw never waits on the network.
pr_number=""
pr_state=""
pr_url=""
gh_bin=$(command -v gh)
[ -x "$HOME/.local/bin/gh" ] && gh_bin="$HOME/.local/bin/gh"
if [ -n "$branch" ] && [ -n "$gh_bin" ] && [ "$branch" != "main" ]; then
  cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/claude-statusline"
  mkdir -p "$cache_dir"
  cache_key=$(printf '%s|%s' "$cwd" "$branch" | md5sum | cut -c1-16)
  cache_file="$cache_dir/pr-$cache_key"
  now=$(date +%s)
  mtime=$(stat -c %Y "$cache_file" 2>/dev/null || echo 0)
  if [ $((now - mtime)) -ge 60 ]; then
    touch "$cache_file"  # claim the refresh so concurrent redraws don't pile up
    (cd "$cwd" && "$gh_bin" pr view "$branch" --json number,state,url \
      -q '"\(.number) \(.state) \(.url)"' >"$cache_file.tmp" 2>/dev/null \
      ; mv -f "$cache_file.tmp" "$cache_file") >/dev/null 2>&1 &
  fi
  read -r pr_number pr_state pr_url <"$cache_file" 2>/dev/null
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

if [ -n "$pr_number" ]; then
  case "$pr_state" in
    MERGED) pr_label="PR #${pr_number} merged" ;;
    CLOSED) pr_label="PR #${pr_number} closed" ;;
    *) pr_label="PR #${pr_number}" ;;
  esac
  # OSC 8 hyperlink so the PR is clickable in terminals that support it.
  parts+=("\033]8;;${pr_url}\a${CYAN}${pr_label}${RESET}\033]8;;\a")
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
