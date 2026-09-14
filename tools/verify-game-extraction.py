#!/usr/bin/env python3
"""Audit a private extraction without changing any files. See ARCHIVE_CHECK.md."""
import argparse
import json
import sys
from game_content import audit_archive, audit_dax_tree


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", help="Game folder, or extraction parent with --archive")
    parser.add_argument("--archive", help="Original classic .SIT file: verify every archived fork")
    args = parser.parse_args()
    try:
        result = audit_archive(args.archive, args.directory) if args.archive else audit_dax_tree(args.directory)
        print(json.dumps(result, indent=2))
        return 0
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
