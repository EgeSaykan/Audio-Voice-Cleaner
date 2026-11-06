#!/usr/bin/env bash
# Install each optional local backend explicitly. Safe to rerun against the same pinned checkout.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
tools="$root/.voice-tools"
models="$tools/models"
mkdir -p "$models"
export UV_CACHE_DIR="$tools/cache/uv"
export UV_PYTHON_INSTALL_DIR="$tools/python"
export HF_HOME="$tools/cache/huggingface"
cmake_bin=
cmake_command() {
    if cmake_bin=$(command -v cmake); then
        return
    fi
    command -v uv >/dev/null || {
        echo "CMake is missing. Install uv in your user account, then rerun this command." >&2
        exit 1
    }
    local env="$tools/cmake"
    if [[ ! -x "$env/bin/cmake" ]]; then
        uv venv --clear --python 3.12 "$env"
        uv pip install --python "$env/bin/python" cmake==3.31.6
    fi
    cmake_bin="$env/bin/cmake"
}
checkout() {
    local name=$1 remote=$2 revision=$3 dir="$tools/$1"
    if [[ ! -d "$dir" ]]; then
        local staging
        staging=$(mktemp -d "$tools/.checkout-XXXXXX")
        git -C "$staging" init -q
        git -C "$staging" fetch --quiet --depth=1 "$remote" "$revision"
        git -C "$staging" checkout --quiet --detach FETCH_HEAD
        mv -T "$staging" "$dir"
    fi
    [[ "$(git -C "$dir" rev-parse HEAD)" == "$revision" ]] || { echo "Unexpected $name revision in $dir" >&2; exit 1; }
}
speaker() {
    checkout iamspeaker https://github.com/spark798/iamspeaker.git 5be74a21457ae2c556058bde57084af8ce5d5079
    local patch="$root/integrations/iamspeaker/voice-only.patch"
    if ! git -C "$tools/iamspeaker" apply --unidiff-zero --reverse --check "$patch" 2>/dev/null; then
        git -C "$tools/iamspeaker" apply --unidiff-zero "$patch"
    fi
    local model="$models/ggml-base.en.bin" sha=a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002
    if [[ ! -f "$model" ]]; then
        local part
        part=$(mktemp "$models/.whisper-XXXXXX")
        curl --fail --location --retry 3 -o "$part" https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.en.bin
        echo "$sha  $part" | sha256sum -c -
        mv "$part" "$model"
    fi
    echo "$sha  $model" | sha256sum -c -
}
case "${1:-}" in
    speaker)
        speaker
        checkout whisper.cpp https://github.com/ggml-org/whisper.cpp.git 4979e04f5dcaccb36057e059bbaed8a2f5288315
        cmake_command
        "$cmake_bin" -S "$tools/whisper.cpp" -B "$tools/whisper-build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DWHISPER_BUILD_TESTS=OFF
        "$cmake_bin" --build "$tools/whisper-build" -j4 --target whisper-cli
        install -Dm755 "$tools/whisper-build/bin/whisper-cli" "$tools/bin/whisper-cli"
        cd "$tools/iamspeaker"; pnpm install --frozen-lockfile --store-dir "$tools/cache/pnpm"
        ;;
    speaker-docker)
        speaker
        ;;
    editx|editx-awq)
        checkout Step-Audio-EditX https://github.com/stepfun-ai/Step-Audio-EditX.git a652e87052c109e26f616d60971376ff47a829d4
        cd "$tools/Step-Audio-EditX"
        # Upstream's frozen inference lock includes its exact vLLM wheel and matching Torch build.
        uv sync --frozen --python 3.12
        py="$PWD/.venv/bin/python"
        uv pip install --python "$py" -r "$root/integrations/step-audio-editx/training-requirements.txt"
        .venv/bin/hf download stepfun-ai/Step-Audio-Tokenizer --revision af7e5a3ec06175a7facae9d4100073d6e4dbb36c --local-dir "$models/Step-Audio-Tokenizer"
        if [[ "$1" == editx-awq ]]; then
            .venv/bin/hf download stepfun-ai/Step-Audio-EditX-AWQ-4bit --revision a3493ca914ef0dd3c265cf589b962e6213124958 --local-dir "$models/Step-Audio-EditX-AWQ-4bit"
        else
            .venv/bin/hf download stepfun-ai/Step-Audio-EditX --revision 5fe2f8a05c2353301ad47d3c1747b262115da138 --local-dir "$models/Step-Audio-EditX"
        fi
        ;;
    *) echo "Usage: $0 {speaker|speaker-docker|editx|editx-awq}" >&2; exit 2 ;;
esac
