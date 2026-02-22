#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"

# ---------- 1. Check espeak-ng ----------

if ! command -v espeak-ng &>/dev/null; then
    echo "Error: espeak-ng is required but not installed."
    echo "Install it with:  sudo apt-get install -y espeak-ng"
    exit 1
fi

# ---------- 2. Create venv & install ----------

if [ ! -d "$VENV_DIR" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv "$VENV_DIR"
else
    echo "Virtual environment already exists."
fi

source "$VENV_DIR/bin/activate"

echo "Installing Kokoro TTS and dependencies..."
pip install --upgrade pip
pip install "kokoro>=0.3.1" soundfile

echo ""
echo "Setup complete."
echo "Run:  bash generate-speech.sh \"Hello, world!\""
