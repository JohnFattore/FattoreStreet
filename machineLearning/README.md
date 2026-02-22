# Machine Learning (Legacy)

> **Superseded by [`llm/`](../llm/)** — this folder contains early experiments that preceded the current LLM setup.

## Scripts

| File | Description |
|---|---|
| `dataLoad.py` | Reads raw Yelp JSON (reviews + businesses) and merges them into a single reviews dataset |
| `dataLoadNashville.py` | Filters Yelp reviews to Nashville restaurants and saves as a pickle |
| `matrixFactorization.py` | Trains a PyTorch matrix factorization model (user/item embeddings) on Nashville Yelp ratings |
| `training.py` | Trains a Neural Collaborative Filtering (NeuralCF) model on Nashville Yelp ratings |
| `testing.py` | Evaluates a trained NeuralCF model on the test set |
| `LLaMa.py` | Loads Llama-3.2-1B via Hugging Face (4-bit quantized) and runs a sample prompt |
| `macroLinearReg.py` | Fits a linear regression of SPY on FRED macro series (UNRATE, CPI, GDP) |
