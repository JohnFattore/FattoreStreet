# The self-hosted GPU inference stack — llama.cpp/stable-diffusion.cpp CUDA builds and VRAM budgeting

_FattoreStreet @ [`b7d12439`](https://github.com/JohnFattore/FattoreStreet/tree/b7d12439fe7d4824d80a74e5dd788d7e50c00750) — 2026-07-30_

_Source: [#166](https://github.com/JohnFattore/FattoreStreet/issues/166)_

## Overview

`llm/` is the one directory in the repo that isn't a deployed service — it's a set of shell scripts that build and run three separate local-inference engines (`llama.cpp` for text, `stable-diffusion.cpp` for images, Kokoro-82M for speech) directly from source against a single, VRAM-constrained GPU (a GTX 980 Ti, 6 GB). A prior learning topic (#151) covered how Spring Boot's `FilingSummaryService` *calls* the llama.cpp server as an HTTP dependency; this topic is about the other side — how that server (and its siblings) actually get built and configured, and what tradeoffs a small, fixed-VRAM card forces onto quantization, context length, and concurrency. It's a useful case study in "vendor a C++ inference engine and compile it yourself" versus reaching for a managed API, and in how hardware constraints (not code) end up dictating architecture — e.g. why nothing here ever runs two GPU jobs at once.

## Files to read

- `llm/README.md` — the whole file (146 lines); it's the primer for all three engines. Pay attention to the tuning table (lines 43–49) and the "Do not run simultaneously with the LLM — both need the GPU" note (line 100)
- `llm/setup.sh` — clones `llama.cpp`, builds it with `-DGGML_CUDA=ON`, then downloads the Qwen2.5-7B-Instruct Q4_K_M GGUF **as two split files** and merges them with `llama-gguf-split --merge` (lines 34–46) before deleting the splits
- `llm/run.sh` vs `llm/run-server.sh` — same model, two entry points: `run.sh` uses `llama-cli -cnv -sysf system-prompt.txt` for an interactive terminal chat; `run-server.sh` uses `llama-server` to expose the OpenAI-compatible `/v1/chat/completions` HTTP API on port 8081 (the one Spring Boot's `FilingSummaryService` calls). Compare the flags each passes to the same underlying model file
- `llm/setup-diffusion.sh` — builds `stable-diffusion.cpp` with `-DSD_CUDA=ON -DCMAKE_CUDA_ARCHITECTURES=52` (52 is the 980 Ti's Maxwell compute capability — this pins the build to the specific card rather than a generic CUDA target) and downloads the SD 1.5 safetensors directly (no split/merge needed here, unlike the GGUF)
- `llm/generate-image.sh` — always passes `--vae-tiling -H 512 -W 512` and forwards any extra CLI args (`--steps`, `--seed`, `--cfg-scale`, etc.) straight through to `sd-cli` via `"${@:2}"`
- `llm/setup-tts.sh` and `llm/generate_speech.py` — the odd one out: pure Python + a `pip`-installed `kokoro` package instead of a C++ build, and it explicitly runs on CPU (`KPipeline(..., device="cpu")`) rather than competing for the GPU
- `llm/system-prompt.txt` — the financial-advisor persona loaded by `run.sh`'s `-sysf` flag

## Questions to answer while reading

1. Why does the Qwen model get downloaded as two split GGUF files and merged locally (`setup.sh` lines 37–44) instead of downloading one pre-merged file? What does `llama-gguf-split --merge` actually do to the two parts?
2. `setup-diffusion.sh` hardcodes `CMAKE_CUDA_ARCHITECTURES=52` but `setup.sh`'s `llama.cpp` build doesn't specify a CUDA architecture at all — why might one build need to pin the exact compute capability and the other not?
3. The README says the llama-server "processes one request at a time" and warns not to run image generation at the same time as the LLM. Given that constraint plus the FilingSummaryService's sequential per-ticker loop (from #151), what would break if two people tried to use the chatbot and trigger `/admin/summarize-filings` at the same moment against the same box?
4. `-ngl 99` tells llama.cpp to offload "all layers" to GPU, and the README says the Q4_K_M quant uses ~4.5 GB VRAM at idle with each 2048 tokens of context adding ~0.25–0.5 GB more, on a 6 GB card. Work out roughly how much headroom is left for context at `-c 4096`, and why the README suggests dropping to `-c 2048` on OOM rather than lowering `-ngl` first.
5. Kokoro TTS is the only engine here that's *not* a from-source CUDA build — it's a pip package running on CPU. Why might that be the right call for TTS specifically, given the constraints already established for the other two engines?

## Primer: quantization and why a 6 GB card can run a 7B model at all

A "7B" model has 7 billion parameters; stored at full 16-bit precision that's ~14 GB — more VRAM than this GPU has, before any context is even allocated. GGUF quantization (the format llama.cpp uses) compresses each weight down to a lower-precision representation — Q4_K_M packs most weights into roughly 4 bits each with some higher-precision "important" tensors kept at higher bit-width for quality — shrinking the same 7B model to ~4.5 GB on disk and in VRAM, at some (usually small, model-dependent) quality cost versus the full-precision original. That's what makes `-ngl 99` (offload every transformer layer to GPU) survivable on a 6 GB card: the quantized weights leave a few hundred MB to a couple GB free for the KV cache that grows with context length (`-c`), which is exactly the budget the README's tuning table is walking through. Stable Diffusion's CUDA-kernel build follows the same "compile against your exact hardware" philosophy as llama.cpp — both projects avoid a PyTorch/CUDA-toolkit-version dependency hell by compiling their own kernels directly for the target compute capability, which is also why `stable-diffusion.cpp`'s build pins `sm_52` explicitly rather than relying on autodetection.

## External references

- llama.cpp GGUF quantization types overview: https://github.com/ggml-org/llama.cpp/blob/master/tools/quantize/README.md
- stable-diffusion.cpp README (build flags, CUDA architectures, sampling methods): https://github.com/leejet/stable-diffusion.cpp

## Exercise (optional)

Run `bash llm/run-server.sh` and, while it's up, `curl` its `/v1/chat/completions` endpoint directly with a small JSON body (skip the Spring Boot layer entirely) to see the raw OpenAI-compatible response shape. Then check `nvidia-smi` while the server is idle vs. mid-generation with a long prompt, and compare the VRAM delta you observe against the README's "~0.25–0.5 GB per 2048 tokens" estimate.
