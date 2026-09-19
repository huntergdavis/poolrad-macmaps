#!/usr/bin/env bash
# Exercise production device traversal and packet state with sanitizers.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p scratch
SNAPSHOT_TEST_DIR="$(mktemp -d scratch/snapshot-packets.XXXXXX)"
trap 'rm -f "$SNAPSHOT_TEST_DIR/test"; rmdir "$SNAPSHOT_TEST_DIR"' EXIT
for SNAPSHOT_TEST in devices packets; do
"${CC:-gcc}" -std=c99 -g -O1 -ffunction-sections -fdata-sections \
  -fsanitize=address,undefined -fno-omit-frame-pointer \
  -Iandroid/minivmac/src/main/jni/cfg \
  -Iandroid/minivmac/src/main/jni/variants/macII/cfg \
  -Iandroid/minivmac/src/main/jni/src \
  "tools/test-snapshot-${SNAPSHOT_TEST}.c" -Wl,--gc-sections \
  -o "$SNAPSHOT_TEST_DIR/test"
"$SNAPSHOT_TEST_DIR/test"
done
