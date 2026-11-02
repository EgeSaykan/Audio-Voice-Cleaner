#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
export EDITX_SOURCE="${EDITX_SOURCE:-$root/.voice-tools/Step-Audio-EditX}"
export EDITX_MODEL="${EDITX_MODEL:-$root/.voice-tools/models/Step-Audio-EditX}"
export EDITX_TOKENIZER="${EDITX_TOKENIZER:-$root/.voice-tools/models/Step-Audio-Tokenizer}"
export HF_HUB_OFFLINE=1 HF_HUB_DISABLE_TELEMETRY=1 VLLM_NO_USAGE_STATS=1 VLLM_WORKER_MULTIPROC_METHOD=spawn VLLM_ATTENTION_BACKEND=TRITON_ATTN
cd "$EDITX_SOURCE"
exec .venv/bin/python -m uvicorn service:app --app-dir "$here" --host 127.0.0.1 --port "${EDITX_PORT:-7861}" --workers 1 --log-level "${LOG_LEVEL:-warning}"
