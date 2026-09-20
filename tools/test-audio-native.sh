#!/usr/bin/env bash
# Exercise production sound hardware and read-only game options with sanitizers.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p scratch
AUDIO_TEST_DIR="$(mktemp -d scratch/audio-mute.XXXXXX)"
trap 'rm -f "$AUDIO_TEST_DIR/test"; rmdir "$AUDIO_TEST_DIR"' EXIT
"${CC:-gcc}" -std=c99 -g -O1 -ffunction-sections -fdata-sections \
  -fsanitize=address,undefined -fno-omit-frame-pointer \
  -Iandroid/minivmac/src/main/jni/cfg \
  -Iandroid/minivmac/src/main/jni/variants/macII/cfg \
  -Iandroid/minivmac/src/main/jni/src \
  tools/test-audio-mute.c -Wl,--gc-sections -o "$AUDIO_TEST_DIR/test"
"$AUDIO_TEST_DIR/test"
