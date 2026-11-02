#!/usr/bin/env bash
set -euo pipefail
umask 077
root=$(cd "$(dirname "$0")/../.." && pwd)
tools="$root/.voice-tools"
cd "$tools/iamspeaker"
export WHISPER_BIN="${WHISPER_BIN:-$tools/bin/whisper-cli}"
export WHISPER_MODEL_PATH="${WHISPER_MODEL_PATH:-$tools/models/ggml-base.en.bin}"
export DATA_DIR="${DATA_DIR:-$tools/iam-data}" DATABASE_URL="${DATABASE_URL:-file:$tools/iam-data/iamspeaker.db}"
export NEXT_TELEMETRY_DISABLED=1 USE_STUB_ADAPTERS=0 PRONUNCIATION_SCORER=heuristic JOB_MAX_ATTEMPTS=1
export OPENAI_API_KEY= ANTHROPIC_API_KEY= AZURE_SPEECH_KEY= ELEVENLABS_API_KEY=
export LOG_LEVEL="${LOG_LEVEL:-warn}"
if [[ ! -x "$WHISPER_BIN" || ! -s "$WHISPER_MODEL_PATH" ]]; then echo "Install the local Whisper binary and model first." >&2; exit 1; fi
exec node node_modules/next/dist/bin/next dev -H 127.0.0.1 -p "${PORT:-3000}"
