#!/usr/bin/env python3
"""Explicit, pinned HTTPS fetches for PRIVATE personal APK inputs. No default sources."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

SPEC = importlib.util.spec_from_file_location(
    "personal_package", Path(__file__).with_name("prepare-personal-package.py"))
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)

PRIVATE_ROOT = Path(__file__).resolve().parents[1] / "android/minivmac/private-assets"
CONFIG_MAX = 64 * 1024
REQUEST_TIMEOUT = 30
DOWNLOAD_TIMEOUT = 300  # Checked between reads; an in-flight socket read can wait 30s more.


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


def open_download(url):
    """Injectable transport. Default TLS certificate verification remains enabled."""
    opener = urllib.request.build_opener(NoRedirect())
    request = urllib.request.Request(url, headers={"Accept-Encoding": "identity"})
    return opener.open(request, timeout=REQUEST_TIMEOUT)


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate config key")
        result[key] = value
    return result


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def _source(value, is_rom=False):
    if not isinstance(value, dict) or set(value) != {"url", "bytes", "sha256"}:
        raise ValueError("Each source requires exactly url, bytes, and sha256")
    size = value["bytes"]
    if type(size) is not int or size <= 0:
        raise ValueError("Source byte counts must be positive integers, not booleans")
    if is_rom and size != builder.ROM_BYTES:
        raise ValueError("Mac II ROM must contain exactly 262144 bytes")
    if not is_rom and (size > builder.DISK_MAX or size % 512):
        raise ValueError("Raw disks must be sector-aligned and at most 128 MiB each")
    if not _sha(value["sha256"]):
        raise ValueError("Every source requires a lowercase SHA-256 pin")
    url = value["url"]
    if (not isinstance(url, str) or not url or any(c.isspace() or ord(c) < 32 or ord(c) == 127 for c in url)
            or "?" in url or "#" in url):
        raise ValueError("Source URLs cannot contain whitespace, controls, queries, or fragments")
    try:
        parsed = urllib.parse.urlsplit(url)
        port = parsed.port
        valid = (parsed.scheme == "https" and parsed.hostname and not parsed.username
                 and not parsed.password and "@" not in parsed.netloc and port != 0)
    except ValueError:
        raise ValueError("Invalid HTTPS source URL") from None
    if not valid:
        raise ValueError("Sources must be HTTPS URLs without credentials")
    if parsed.hostname.lower().rstrip(".") == "raw.githubusercontent.com":
        parts = parsed.path.split("/")
        if (len(parts) < 5 or not parts[1] or not parts[2]
                or not re.fullmatch(r"[0-9a-fA-F]{40}", parts[3]) or not any(parts[4:])
                or any(part in (".", "..") for part in urllib.parse.unquote(parsed.path).split("/"))):
            raise ValueError("Raw GitHub sources must use a full 40-hex commit, not a branch or tag")
    return value


def _config(path, config_sha256):
    try:
        _, raw = builder._inspect(Path(path), CONFIG_MAX, True)
    except (ValueError, OSError):
        raise ValueError("Config must be a regular, nonempty file of at most 64 KiB") from None
    if config_sha256 is not None:
        if not _sha(config_sha256) or hashlib.sha256(raw).hexdigest() != config_sha256:
            raise ValueError("Config SHA-256 changed; select the cache again using these exact config bytes")
    try:
        config = json.loads(raw, object_pairs_hook=_unique_object)
    except (ValueError, UnicodeError, RecursionError):
        raise ValueError("Invalid JSON config or duplicate keys") from None
    if (not isinstance(config, dict) or set(config) != {"format", "rom", "disks"}
            or type(config["format"]) is not int or config["format"] != 1):
        raise ValueError("Config requires exactly format=1, rom, and disks")
    _source(config["rom"], True)
    disks = config["disks"]
    if not isinstance(disks, list) or not 1 <= len(disks) <= 8:
        raise ValueError("Config must contain 1 through 8 disks in boot order")
    for disk in disks:
        _source(disk)
    if sum(disk["bytes"] for disk in disks) > builder.DISKS_TOTAL_MAX:
        raise ValueError("Combined disk payload exceeds 256 MiB")
    return config


def _private_output(output):
    root = Path(os.path.abspath(PRIVATE_ROOT))
    output = Path(output)
    if ".." in output.parts or ".." in Path(PRIVATE_ROOT).parts:
        raise ValueError("Private paths cannot contain parent traversal")
    output = Path(os.path.abspath(output))
    if output == root or not output.is_relative_to(root):
        raise ValueError("Output must be below the ignored private-assets directory")
    # Do not resolve away a symlink: reject redirects at every existing component,
    # including the configured private root and ancestors above it.
    for path in [output, *output.parents]:
        try:
            mode = path.lstat().st_mode
        except FileNotFoundError:
            continue
        if not stat.S_ISDIR(mode):
            raise ValueError("Private output path must contain real directories only; no symlinks")
    return output


class _DownloadError(ValueError):
    pass


def _download(source, target):
    started, received, digest = time.monotonic(), 0, hashlib.sha256()
    try:
        with open_download(source["url"]) as response:
            if response.status != 200:
                raise _DownloadError("Download requires HTTP 200; redirects are disabled. Configure the final HTTPS URL.")
            length = response.headers.get("Content-Length")
            if length is not None and (not re.fullmatch(r"[0-9]+", str(length))
                                       or int(length) != source["bytes"]):
                raise _DownloadError("Download Content-Length does not match its pinned byte count")
            with target.open("xb") as output:
                # HTTPResponse.read(n) may wait for n bytes while a peer drips data.
                # read1 does one buffered/socket read so the deadline is rechecked.
                # Simple offline fixtures may expose only read().
                read = getattr(response, "read1", None) or response.read
                while True:
                    if time.monotonic() - started > DOWNLOAD_TIMEOUT:
                        raise _DownloadError("Download exceeded its five-minute time limit")
                    block = read(min(65536, source["bytes"] - received + 1))
                    if time.monotonic() - started > DOWNLOAD_TIMEOUT:
                        raise _DownloadError("Download exceeded its five-minute time limit")
                    if not block:
                        break
                    received += len(block)
                    if received > source["bytes"]:
                        raise _DownloadError("Download exceeds its pinned byte count")
                    output.write(block)
                    digest.update(block)
                output.flush()
                os.fsync(output.fileno())
        if received != source["bytes"] or digest.hexdigest() != source["sha256"]:
            raise _DownloadError("Download is truncated or does not match its pinned SHA-256")
    except _DownloadError:
        raise
    except urllib.error.HTTPError as error:
        try:
            error.close()
        except Exception:
            pass
        if 300 <= error.code < 400:
            raise ValueError("Redirects are disabled. Configure the final HTTPS URL.") from None
        raise ValueError("HTTPS download failed; check source availability and byte/hash pins") from None
    except Exception:
        # urllib exceptions can contain signed/private URLs or upstream content.
        # Never echo URLs, server bodies, or raw transport exceptions.
        raise ValueError("HTTPS download failed; check connectivity, final URL, and byte/hash pins") from None


def _download_labeled(source, target, label):
    try:
        _download(source, target)
    except ValueError as error:
        raise ValueError(f"{label}: {error}") from None


def _matches(output, config):
    raw, _ = builder.verify(output)
    entries = builder.parse_manifest(raw)
    sources = [config["rom"], *config["disks"]]
    if int(entries["disk.count"]) != len(config["disks"]):
        raise ValueError("Existing private bundle does not match this config; choose a new output")
    for index, source in enumerate(sources):
        prefix = "rom" if index == 0 else f"disk.{index}"
        if (int(entries[prefix + ".bytes"]) != source["bytes"]
                or entries[prefix + ".sha256"] != source["sha256"]):
            raise ValueError("Existing private bundle does not match this config; choose a new output")


def fetch(config_path, output, acknowledge=False, offline=False, config_sha256=None):
    """Return a strict B1 bundle. Exact pinned caches are reusable without network."""
    if not acknowledge:
        raise ValueError("Pass --acknowledge-private-assets; fetched bundles and personal APKs are private")
    config = _config(config_path, config_sha256)
    output = _private_output(output)
    if output.exists():
        _matches(output, config)
        return output
    if offline:
        raise ValueError("Offline mode: no verified private bundle is cached for this config")
    output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    _private_output(output)
    with tempfile.TemporaryDirectory(prefix=".poolrad-fetch-", dir=output.parent) as temporary:
        staging = Path(temporary)
        rom = staging / "MacII.ROM"
        _download_labeled(config["rom"], rom, "ROM")
        builder.validate_rom(rom.read_bytes())
        disks = []
        for index, source in enumerate(config["disks"], 1):
            disk = staging / f"disk{index}.dsk"
            _download_labeled(source, disk, f"Disk {index}")
            disks.append(disk)
        _private_output(output)
        builder.prepare(rom, disks, output, acknowledge=True)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=
        "No default sources, credentials, redirects, or archive extraction. Never publish fetched assets or personal APKs.")
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--acknowledge-private-assets", action="store_true")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--config-sha256")
    args = parser.parse_args()
    try:
        fetch(args.config, args.output, args.acknowledge_private_assets, args.offline, args.config_sha256)
    except (ValueError, OSError) as error:
        parser.exit(2, f"Error: {error}\n")
    print("Verified private personal bundle. Do not publish its assets or APK.")


if __name__ == "__main__":
    main()
