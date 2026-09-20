#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p scratch
IDLE_TEST_DIR="$(mktemp -d scratch/automatic-idle.XXXXXX)"
trap 'rm -f "$IDLE_TEST_DIR/test"; rmdir "$IDLE_TEST_DIR"' EXIT
"${CC:-gcc}" -std=c11 -g -O1 -Wall -Wextra -Werror \
  -fsanitize=address,undefined -fno-omit-frame-pointer \
  tools/test-automatic-idle.c -o "$IDLE_TEST_DIR/test"
"$IDLE_TEST_DIR/test" "$@"
