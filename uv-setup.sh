curl -LsSf https://astral.sh/uv/install.sh |
  env \
    UV_INSTALL_DIR="$PORTABLE_ROOT/bin" \
    UV_NO_MODIFY_PATH=1 \
    sh

uv python install 3.12