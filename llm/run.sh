#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_CLI="$SCRIPT_DIR/llama.cpp/build/bin/llama-cli"
MODEL_FILE="$SCRIPT_DIR/models/qwen2.5-7b-instruct-q4_k_m.gguf"

if [ ! -x "$LLAMA_CLI" ]; then
    echo "Error: llama-cli not found. Run setup.sh first."
    exit 1
fi

if [ ! -f "$MODEL_FILE" ]; then
    echo "Error: Model not found at $MODEL_FILE. Run setup.sh first."
    exit 1
fi

exec "$LLAMA_CLI" \
    -m "$MODEL_FILE" \
    -ngl 99 \
    -c 4096 \
    -t 4 \
    --chat-template chatml \
    -cnv \
    -sysf "$SCRIPT_DIR/system-prompt.txt"
