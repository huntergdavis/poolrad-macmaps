#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p scratch
WAIT_TEST_DIR="$(mktemp -d scratch/emulation-wait.XXXXXX)"
trap 'rm -f "$WAIT_TEST_DIR/test"; rmdir "$WAIT_TEST_DIR"' EXIT
"${CC:-gcc}" -std=c11 -g -O1 -Wall -Wextra -Werror -pthread \
  -fsanitize=address,undefined -fno-omit-frame-pointer \
  tools/test-emulation-wait.c -o "$WAIT_TEST_DIR/test"
"$WAIT_TEST_DIR/test"
