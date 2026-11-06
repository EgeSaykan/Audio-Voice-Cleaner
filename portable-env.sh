export VOICE_REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PORTABLE_ROOT="$VOICE_REPO/.portable"

export PATH="$PORTABLE_ROOT/bin:$PATH"
export UV_PYTHON_INSTALL_DIR="$PORTABLE_ROOT/python"
export UV_CACHE_DIR="$PORTABLE_ROOT/cache/uv"
export HF_HOME="$PORTABLE_ROOT/cache/huggingface"
export XDG_CACHE_HOME="$PORTABLE_ROOT/cache/xdg"
export TORCH_HOME="$PORTABLE_ROOT/cache/torch"
export TMPDIR="$PORTABLE_ROOT/tmp"

mkdir -p \
  "$PORTABLE_ROOT/bin" \
  "$PORTABLE_ROOT/python" \
  "$PORTABLE_ROOT/cache/uv" \
  "$PORTABLE_ROOT/cache/huggingface" \
  "$PORTABLE_ROOT/cache/xdg" \
  "$PORTABLE_ROOT/cache/torch" \
  "$PORTABLE_ROOT/tmp"