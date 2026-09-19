#!/usr/bin/env bash
# Send documented game commands through the existing timed input. No guest RAM edits.
set -euo pipefail
SERIAL="${1:?usage: guest-command.sh emulator-NNNN load|quit|begin|view|save|text|key [TEXT]}"
shift
exec bash "$(dirname "$0")/debug-ui.sh" "$SERIAL" GuestCommand "$@"
