#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SD_DIR="$SCRIPT_DIR/stable-diffusion.cpp"
MODEL_DIR="$SCRIPT_DIR/models"
MODEL_URL="https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors"
MODEL_FILE="$MODEL_DIR/v1-5-pruned-emaonly.safetensors"

# ---------- 1. Clone & build stable-diffusion.cpp ----------

if [ ! -d "$SD_DIR" ]; then
    echo "Cloning stable-diffusion.cpp..."
    git clone --recursive --depth 1 https://github.com/leejet/stable-diffusion.cpp.git "$SD_DIR"
else
    echo "stable-diffusion.cpp already cloned, pulling latest..."
    git -C "$SD_DIR" pull --ff-only || true
fi

echo "Building stable-diffusion.cpp with CUDA support..."
cmake -S "$SD_DIR" -B "$SD_DIR/build" \
    -DSD_CUDA=ON \
    -DCMAKE_CUDA_ARCHITECTURES=52 \
    -DCMAKE_BUILD_TYPE=Release
cmake --build "$SD_DIR/build" --config Release -j "$(nproc)"

# ---------- 2. Download SD 1.5 model ----------

mkdir -p "$MODEL_DIR"

if [ -f "$MODEL_FILE" ]; then
    echo "Model already downloaded: $MODEL_FILE"
else
    echo "Downloading Stable Diffusion v1.5 (~4.3 GB)..."
    curl -L --progress-bar -o "$MODEL_FILE" "$MODEL_URL"
fi

echo ""
echo "Setup complete."
echo "  Build:  $SD_DIR/build/bin/sd-cli"
echo "  Model:  $MODEL_FILE"
echo "Run:  bash generate-image.sh \"your prompt here\""
