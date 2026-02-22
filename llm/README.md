# Local AI — LLM, Image Generation & Text-to-Speech

Run Qwen2.5-7B-Instruct locally via llama.cpp with CUDA GPU offload.

## Prerequisites

- **CUDA toolkit** (nvcc, drivers) — tested with a GTX 980 Ti (6 GB VRAM)
- **cmake** >= 3.14
- **git**, **curl**
- A C++ compiler (gcc/g++)

## Setup

```bash
cd llm
bash setup.sh
```

This clones llama.cpp, builds it with CUDA, and downloads the Q4_K_M GGUF (~4.5 GB).

## Run

```bash
bash run.sh
```

Opens an interactive chat session with a financial advisor system prompt. Type your messages and press Enter.

The system prompt is loaded from `system-prompt.txt` — edit that file to change the model's personality and guidelines.

## Server Mode (HTTP API)

```bash
bash run-server.sh
```

Starts an OpenAI-compatible HTTP server on port 8081 (override with `bash run-server.sh 9090`). Exposes `POST /v1/chat/completions` which Spring Boot uses for 10-K filing summarization.

The server must be running before triggering `/admin/summarize-filings`. It processes one request at a time.

## Tuning

| Flag | Default | Notes |
|------|---------|-------|
| `-ngl 99` | all layers on GPU | Lower if you hit OOM (try `-ngl 28`) |
| `-c 4096` | 4096 token context | Lower to 2048 if you hit OOM |
| `-t 4` | 4 CPU threads | Match your physical core count |

The Q4_K_M quant uses ~4.5 GB VRAM at idle. Each 2048 tokens of context adds roughly 0.25–0.5 GB depending on batch size. On a 6 GB card, 4096 context may be tight — drop to 2048 if you see OOM errors.

---

## Stable Diffusion v1.5 — Image Generation (stable-diffusion.cpp)

Generate images from text prompts using [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp), a pure C/C++ implementation that compiles CUDA kernels for your GPU (same approach as llama.cpp).

### Prerequisites

Same as the LLM setup — CUDA toolkit, cmake, git, curl, gcc.

### Setup

```bash
cd llm
bash setup-diffusion.sh
```

This clones stable-diffusion.cpp, builds it with CUDA and flash attention, and downloads the SD 1.5 safetensors (~4.3 GB).

### Generate an Image

```bash
bash generate-image.sh "a sunset over mountains"
```

Images are saved to `llm/output/images/` with a timestamp filename.

### Options

Extra flags are passed through to `sd-cli`:

```bash
bash generate-image.sh "a cat in space" --steps 30 --seed 42
bash generate-image.sh "a landscape" --cfg-scale 9 --sampling-method euler_a
```

| Flag | Default | Notes |
|------|---------|-------|
| `--steps` | 20 | More steps = better quality, slower |
| `--seed` | random | Set for reproducible results |
| `--cfg-scale` | 7.0 | How closely to follow the prompt |
| `--sampling-method` | euler_a | Options: euler, euler_a, heun, dpm2, dpm++2m, lcm |
| `-n` | (none) | Negative prompt, e.g. `-n "blurry, low quality"` |

### Notes

- Compiles CUDA kernels for sm_52 (980 Ti) at build time — no PyTorch compatibility issues
- Uses `--vae-tiling` to reduce peak VRAM usage
- Output resolution is 512x512
- Do not run simultaneously with the LLM — both need the GPU

---

## Kokoro-82M — Text-to-Speech

Generate speech from text using [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M), a lightweight 82M parameter TTS model. Runs on CPU — no GPU needed.

### Prerequisites

- **Python 3.10+**
- **espeak-ng**: `sudo apt-get install -y espeak-ng`

### Setup

```bash
cd llm
bash setup-tts.sh
```

This creates a Python virtual environment and installs the `kokoro` and `soundfile` packages.

### Generate Speech

```bash
bash generate-speech.sh "Hello, welcome to Fattore Street."
```

Audio files are saved to `llm/output/audio/` as 24kHz WAV files.

### Options

```bash
bash generate-speech.sh "Bonjour le monde" --voice ff_siwis --lang f
```

| Flag | Default | Notes |
|------|---------|-------|
| `--voice` | af_heart | Voice ID (see [full list](https://huggingface.co/hexgrad/Kokoro-82M)) |
| `--lang` | a | a=American, b=British, e=Spanish, f=French, i=Italian, j=Japanese |

### Notes

- 82M parameters — runs fast on CPU, no GPU required
- Can run simultaneously with the LLM or image generation
- Long text is automatically split into segments (one WAV per segment)
