#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"

if [ ! -d "$VENV_DIR" ]; then
    echo "Error: Virtual environment not found. Run setup-tts.sh first."
    exit 1
fi

source "$VENV_DIR/bin/activate"

python "$SCRIPT_DIR/generate_speech.py" "$@"
