#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SD_CLI="$SCRIPT_DIR/stable-diffusion.cpp/build/bin/sd-cli"
MODEL_FILE="$SCRIPT_DIR/models/v1-5-pruned-emaonly.safetensors"
OUTPUT_DIR="$SCRIPT_DIR/output/images"

if [ ! -x "$SD_CLI" ]; then
    echo "Error: sd-cli not found. Run setup-diffusion.sh first."
    exit 1
fi

if [ ! -f "$MODEL_FILE" ]; then
    echo "Error: Model not found at $MODEL_FILE. Run setup-diffusion.sh first."
    exit 1
fi

if [ $# -lt 1 ]; then
    echo "Usage: bash generate-image.sh \"your prompt here\" [--steps N] [--seed N]"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_FILE="$OUTPUT_DIR/${TIMESTAMP}.png"

exec "$SD_CLI" \
    -m "$MODEL_FILE" \
    -p "$1" \
    -o "$OUTPUT_FILE" \
    --vae-tiling \
    -H 512 \
    -W 512 \
    "${@:2}"
