#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_SERVER="$SCRIPT_DIR/llama.cpp/build/bin/llama-server"
MODEL_FILE="$SCRIPT_DIR/models/qwen2.5-7b-instruct-q4_k_m.gguf"

if [ ! -x "$LLAMA_SERVER" ]; then
    echo "Error: llama-server not found. Run setup.sh first."
    exit 1
fi

if [ ! -f "$MODEL_FILE" ]; then
    echo "Error: Model not found at $MODEL_FILE. Run setup.sh first."
    exit 1
fi

PORT="${1:-8081}"

exec "$LLAMA_SERVER" \
    -m "$MODEL_FILE" \
    -ngl 99 \
    -c 4096 \
    -t 4 \
    --host 0.0.0.0 \
    --port "$PORT"
