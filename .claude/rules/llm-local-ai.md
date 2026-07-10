---
paths:
  - "llm/**"
---

# Local AI Workflow

## Hardware-Aware Defaults (GTX 980 Ti, 6 GB VRAM)

- Prefer VRAM-safe defaults for local runs.
- For `llama.cpp` chat models, use conservative context when needed to avoid OOM.
- For `stable-diffusion.cpp`, ensure CUDA is enabled and avoid unnecessary simultaneous GPU workloads.

## Resource Safety

- Do not run multiple GPU-heavy jobs at the same time (LLM + image generation).
- If a command fails with OOM, reduce context/resolution/steps before retrying.

## Generated and Binary Artifacts

- Treat generated outputs and model artifacts as non-source files.
- Do not edit or rewrite files under:
  - `llm/output/**`
  - `llm/models/**`
  - `llm/input/**` (user-provided media/assets)
  - `llm/**/*.gguf`
  - `llm/**/*.safetensors`
  - `llm/**/*.wav`
  - `llm/**/*.png`
- Only remove or regenerate these files when the user explicitly asks.
