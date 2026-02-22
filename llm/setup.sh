#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_DIR="$SCRIPT_DIR/llama.cpp"
MODEL_DIR="$SCRIPT_DIR/models"
BASE_URL="https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main"
SPLIT_1="qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf"
SPLIT_2="qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf"
MODEL_FILE="$MODEL_DIR/qwen2.5-7b-instruct-q4_k_m.gguf"

# ---------- 1. Clone & build llama.cpp ----------

if [ ! -d "$LLAMA_DIR" ]; then
    echo "Cloning llama.cpp..."
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_DIR"
else
    echo "llama.cpp already cloned, pulling latest..."
    git -C "$LLAMA_DIR" pull --ff-only || true
fi

echo "Building llama.cpp with CUDA support..."
cmake -S "$LLAMA_DIR" -B "$LLAMA_DIR/build" \
    -DGGML_CUDA=ON \
    -DCMAKE_BUILD_TYPE=Release
cmake --build "$LLAMA_DIR/build" --config Release -j "$(nproc)"

# ---------- 2. Download the GGUF model ----------

mkdir -p "$MODEL_DIR"

GGUF_SPLIT="$LLAMA_DIR/build/bin/llama-gguf-split"

if [ -f "$MODEL_FILE" ]; then
    echo "Model already downloaded: $MODEL_FILE"
else
    echo "Downloading Qwen2.5-7B-Instruct Q4_K_M split files (~4.5 GB total)..."
    curl -L --progress-bar -o "$MODEL_DIR/$SPLIT_1" "$BASE_URL/$SPLIT_1"
    curl -L --progress-bar -o "$MODEL_DIR/$SPLIT_2" "$BASE_URL/$SPLIT_2"

    echo "Merging split files..."
    "$GGUF_SPLIT" --merge "$MODEL_DIR/$SPLIT_1" "$MODEL_FILE"

    rm -f "$MODEL_DIR/$SPLIT_1" "$MODEL_DIR/$SPLIT_2"
    echo "Cleaned up split files."
fi

echo ""
echo "Setup complete."
echo "  Build:  $LLAMA_DIR/build/bin/llama-cli"
echo "  Model:  $MODEL_FILE"
echo "Run:  bash $(basename "$0" | sed 's/setup/run/')"
