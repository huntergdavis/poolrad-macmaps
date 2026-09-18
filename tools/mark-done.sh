#!/usr/bin/env bash
# Tick a backlog item and record which release carried it.
#   tools/mark-done.sh F26 0.39.0
set -euo pipefail
ID="${1:?usage: mark-done.sh <Fnn> <version>}"
VERSION="${2:?usage: mark-done.sh <Fnn> <version>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$ROOT/docs/BACKLOG.md" "$ID" "$VERSION" <<'PY'
import re, sys
path, item, version = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path).read()
pattern = r"- \[ \] \*\*" + re.escape(item) + r" — "
if not re.search(pattern, text):
    sys.exit(f"{item} is not an open backlog item")
text = re.sub(pattern, f"- [x] **{item} (delivered {version}) — ", text, count=1)
open(path, "w").write(text)
print(f"{item} marked delivered in {version}")
PY
