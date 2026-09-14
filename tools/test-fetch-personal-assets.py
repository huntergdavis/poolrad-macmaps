#!/usr/bin/env python3
"""Synthetic-only pinned-download checks. No external requests or real game assets."""
import copy
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch
import urllib.error
import urllib.request


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SCRIPT = Path(__file__).with_name("fetch-personal-assets.py")
fetcher = load_module("poolrad_fetch_assets", SCRIPT)
# Reuse the reviewed generated BE16-checksum pattern, not original firmware bytes.
fixtures = load_module("poolrad_package_fixtures", Path(__file__).with_name("test-personal-package.py"))
builder = fixtures.builder


class Response(io.BytesIO):
    def __init__(self, data, status=200, content_length=None):
        super().__init__(data)
        self.status = status
        self.headers = {"Content-Length": str(len(data) if content_length is None else content_length)}

    def read1(self, length=-1):
        return self.read(length)


class FetchPersonalAssetsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="poolrad-fetch-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.private = self.root / "private-assets"
        self.private.mkdir()
        self.output = self.private / "bundle"
        self.config_path = self.root / "fetch.json"
        self.rom = fixtures.synthetic_rom()
        self.disks = [b"BOOT" * 128, b"GAME" * 256]
        self.urls = ["https://raw.githubusercontent.com/example/assets/" + "a" * 40 + "/MacII.ROM",
                     "https://assets.example.test/boot.dsk", "https://assets.example.test/game.dsk"]
        self.payloads = dict(zip(self.urls, [self.rom, *self.disks]))
        self.config = {"format": 1, "rom": self.metadata(self.urls[0], self.rom),
                       "disks": [self.metadata(url, data) for url, data in zip(self.urls[1:], self.disks)]}
        self.write_config()
        self.responses = []
        self.root_patch = patch.object(fetcher, "PRIVATE_ROOT", self.private)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.real_download = fetcher.open_download
        self.network_patch = patch.object(fetcher, "open_download", side_effect=self.download)
        self.network = self.network_patch.start()
        self.addCleanup(self.network_patch.stop)

    @staticmethod
    def metadata(url, data):
        return {"url": url, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}

    def write_config(self, config=None):
        self.config_path.write_text(json.dumps(self.config if config is None else config), encoding="utf-8")

    def download(self, url):
        response = Response(self.payloads[url])
        self.responses.append(response)
        return response

    def fetch(self, **kwargs):
        return fetcher.fetch(self.config_path, kwargs.pop("output", self.output),
                             **{"acknowledge": True, **kwargs})

    def rejected(self, operation, *args, **kwargs):
        with self.assertRaises((ValueError, OSError)) as caught:
            operation(*args, **kwargs)
        return caught.exception

    def snapshot(self):
        # Include receipt/temp siblings too: failure must not replace or mutate any cache.
        return {str(path.relative_to(self.private)): ("dir",) if path.is_dir() else
                ("file", path.read_bytes(), path.stat().st_mtime_ns)
                for path in self.private.rglob("*")}

    def unpublished(self):
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.private.iterdir()), "Temporary download/package data was left visible")
        self.assertTrue(all(response.closed for response in self.responses))

    def test_successful_multiple_disks_keep_order_and_pass_independent_b1_verifier(self):
        result = self.fetch()
        self.assertEqual(self.output, result)
        raw, snapshots = builder.verify(result)
        manifest = builder.parse_manifest(raw)
        self.assertEqual("2", manifest["disk.count"])
        self.assertEqual(self.rom, (result / "MacII.ROM").read_bytes())
        for n, disk in enumerate(self.disks, 1):
            self.assertEqual(disk, (result / f"disk{n}.dsk").read_bytes())
        self.assertEqual({"bundle.properties", "MacII.ROM", "disk1.dsk", "disk2.dsk"}, set(snapshots))
        self.assertEqual(self.urls, [call.args[0] for call in self.network.call_args_list])
        self.assertTrue(all(response.closed for response in self.responses))
        self.assertEqual([self.output], list(self.private.iterdir()))

    def test_valid_cache_is_read_only_and_makes_zero_network_requests(self):
        self.fetch()
        before = self.snapshot()
        self.network.reset_mock()
        self.network.side_effect = AssertionError("A verified cache must never download again")
        self.assertEqual(self.output, self.fetch())
        self.assertEqual(before, self.snapshot())
        self.network.assert_not_called()

    def test_offline_mode_rejects_missing_cache_and_reuses_complete_cache(self):
        self.rejected(self.fetch, offline=True)
        self.network.assert_not_called()
        self.unpublished()
        self.fetch()
        self.network.reset_mock()
        before = self.snapshot()
        self.assertEqual(self.output, self.fetch(offline=True))
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())

    def test_corrupt_cache_fails_without_repair_redownload_or_overwrite(self):
        self.fetch()
        (self.output / "disk2.dsk").write_bytes(b"changed user data")
        before = self.snapshot()
        self.network.reset_mock()
        self.rejected(self.fetch)
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())

    def test_missing_cache_file_is_not_silently_recreated(self):
        self.fetch()
        (self.output / "disk1.dsk").unlink()
        before = self.snapshot()
        self.network.reset_mock()
        self.rejected(self.fetch)
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())

    def test_changed_expected_payload_or_disk_order_refuses_existing_cache(self):
        self.fetch()
        before = self.snapshot()
        for field, value in (("sha256", "0" * 64), ("bytes", 1024)):
            changed = copy.deepcopy(self.config)
            changed["disks"][0][field] = value
            self.write_config(changed)
            self.network.reset_mock()
            self.rejected(self.fetch)
            self.network.assert_not_called()
            self.assertEqual(before, self.snapshot())
        changed = copy.deepcopy(self.config)
        changed["disks"].reverse()
        self.write_config(changed)
        self.rejected(self.fetch)
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())

    def test_config_sha_pin_checks_exact_bytes_before_cache_or_network(self):
        raw = self.config_path.read_bytes()
        pin = hashlib.sha256(raw).hexdigest()
        self.assertEqual(self.output, self.fetch(config_sha256=pin))
        before = self.snapshot()
        self.network.reset_mock()
        self.config_path.write_bytes(raw + b"\n") # Same parsed config, different reviewed bytes.
        self.rejected(self.fetch, config_sha256=pin)
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())
        for invalid in ("wrong", "A" * 64, "0" * 64):
            self.rejected(self.fetch, config_sha256=invalid)
            self.network.assert_not_called()
            self.assertEqual(before, self.snapshot())

    def test_url_only_change_can_reuse_the_same_exact_pinned_payload_cache(self):
        self.fetch()
        before = self.snapshot()
        changed = copy.deepcopy(self.config)
        changed["disks"][0]["url"] = "https://mirror.example.test/same-boot.dsk"
        self.write_config(changed)
        self.network.reset_mock()
        self.assertEqual(self.output, self.fetch())
        self.network.assert_not_called()
        self.assertEqual(before, self.snapshot())

    def test_acknowledgement_and_config_are_required_before_network_access(self):
        self.rejected(self.fetch, acknowledge=False)
        self.config_path.unlink()
        self.rejected(self.fetch)
        self.network.assert_not_called()
        self.unpublished()

    def test_json_types_duplicate_unknown_and_missing_fields_are_rejected(self):
        good = json.dumps(self.config)
        bad_configs = ["", "[]", "null", "{broken", good + " " * 65536,
                       good.replace('"format": 1', '"format": 1, "format": 1'),
                       good.replace('"format": 1', '"format": true')]
        for mutation in (lambda c: c.update(format=2), lambda c: c.update(extra="ignored?"),
                         lambda c: c["rom"].update(extra=1), lambda c: c["disks"][0].update(extra=1),
                         lambda c: c["rom"].pop("sha256"), lambda c: c["rom"].update(bytes=True),
                         lambda c: c["disks"][0].update(bytes="512"), lambda c: c.update(disks="not a list")):
            changed = copy.deepcopy(self.config)
            mutation(changed)
            bad_configs.append(json.dumps(changed))
        for raw in bad_configs:
            with self.subTest(raw=raw[:80]):
                self.config_path.write_text(raw, encoding="utf-8")
                self.rejected(self.fetch)
                self.network.assert_not_called()
                self.unpublished()

    def test_rom_metadata_disk_count_alignment_and_size_limits_precede_requests(self):
        bad_configs = []
        for update in ({"bytes": 131072}, {"sha256": "not-a-digest"}, {"sha256": "0" * 63}):
            config = copy.deepcopy(self.config)
            config["rom"].update(update)
            bad_configs.append(config)
        for disks in ([], self.config["disks"] * 5):
            config = copy.deepcopy(self.config)
            config["disks"] = disks
            bad_configs.append(config)
        for length in (0, 513, 128 * 1024 * 1024 + 512, -512, True):
            config = copy.deepcopy(self.config)
            config["disks"][0]["bytes"] = length
            bad_configs.append(config)
        config = copy.deepcopy(self.config)
        disk = copy.deepcopy(config["disks"][0])
        disk["bytes"] = 128 * 1024 * 1024
        config["disks"] = [disk, disk, self.config["disks"][1]]
        bad_configs.append(config)
        for config in bad_configs:
            self.write_config(config)
            self.rejected(self.fetch)
            self.network.assert_not_called()
            self.unpublished()

    def test_urls_require_https_no_credentials_query_fragment_controls_and_pinned_github(self):
        for url in ("http://assets.example.test/rom", "file:///tmp/rom", "https://user:password@assets.example.test/rom",
                    "https://assets.example.test/rom?secret=abc", "https://assets.example.test/rom#part",
                    "https://assets.example.test/ro\nm", "https://assets.example.test/ro\x00m",
                    " https://assets.example.test/rom", "https://raw.githubusercontent.com/owner/repo/main/rom",
                    "https://raw.githubusercontent.com/owner/repo/1234/rom"):
            changed = copy.deepcopy(self.config)
            changed["rom"]["url"] = url
            self.write_config(changed)
            self.rejected(self.fetch)
            self.network.assert_not_called()
            self.unpublished()

    def test_output_outside_private_root_or_through_parent_traversal_is_rejected(self):
        for target in (self.root / "public-bundle", self.private / ".." / "escaped-bundle"):
            self.rejected(self.fetch, output=target)
            self.network.assert_not_called()
            self.assertFalse(target.exists())
        self.unpublished()

    def test_output_and_parent_symlinks_are_never_followed_or_overwritten(self):
        external = self.root / "external"
        external.mkdir()
        (external / "keep").write_bytes(b"untouched")
        self.output.symlink_to(external, target_is_directory=True)
        self.rejected(self.fetch)
        self.assertTrue(self.output.is_symlink())
        self.output.unlink()
        self.output.symlink_to(self.root / "missing", target_is_directory=True)
        self.rejected(self.fetch)
        self.assertTrue(self.output.is_symlink())
        self.output.unlink()
        alias = self.private / "alias"
        alias.symlink_to(external, target_is_directory=True)
        self.rejected(self.fetch, output=alias / "new-bundle")
        self.assertFalse((external / "new-bundle").exists())
        self.assertEqual(b"untouched", (external / "keep").read_bytes())
        self.network.assert_not_called()

    def test_truncated_streams_fail_and_close_without_publishing(self):
        for bad_url in (self.urls[0], self.urls[1]):
            def truncated(url):
                if url != bad_url:
                    return self.download(url)
                original = self.payloads[url]
                response = Response(original[:-1], content_length=len(original))
                self.responses.append(response)
                return response
            self.network.side_effect = truncated
            self.rejected(self.fetch)
            self.unpublished()

    def test_oversized_streams_are_rejected_even_when_header_claims_expected_length(self):
        original_download = self.download
        for bad_url in (self.urls[0], self.urls[1]):
            def oversized(url):
                if url != bad_url:
                    return original_download(url)
                response = Response(self.payloads[url] + b"!", content_length=len(self.payloads[url]))
                self.responses.append(response)
                return response
            self.network.side_effect = oversized
            self.rejected(self.fetch)
            self.unpublished()

    def test_wrong_hash_fails_even_with_correct_response_size(self):
        self.payloads[self.urls[1]] = b"X" * len(self.disks[0])
        error = self.rejected(self.fetch)
        self.assertTrue(str(error).startswith("Disk 1:"))
        self.unpublished()

    def test_bad_rom_with_matching_pinned_sha_still_fails_original_word_checksum(self):
        malformed = self.rom[:-2] + b"\x00\x01"
        self.payloads[self.urls[0]] = malformed
        self.config["rom"] = self.metadata(self.urls[0], malformed)
        self.write_config()
        self.rejected(self.fetch)
        self.unpublished()

    def test_non200_response_and_transport_errors_are_sanitized(self):
        for status in (206, 302, 404):
            response = Response(b"PRIVATE_RESPONSE_BODY", status=status)
            self.responses.append(response)
            self.network.side_effect = lambda url: response
            error = self.rejected(self.fetch)
            self.assertTrue(str(error).startswith("ROM:"))
            self.assertNotIn("PRIVATE_RESPONSE_BODY", str(error))
            self.unpublished()
        error_body = io.BytesIO(b"PRIVATE_TOKEN response body")
        for error in (urllib.error.URLError("PRIVATE_TOKEN do not echo"),
                      TimeoutError("PRIVATE_TOKEN timed out"),
                      urllib.error.HTTPError(self.urls[0], 403, "PRIVATE_TOKEN", {}, error_body)):
            self.network.side_effect = error
            caught = self.rejected(self.fetch)
            self.assertTrue(str(caught).startswith("ROM:"))
            self.assertNotIn("PRIVATE_TOKEN", str(caught))
            self.unpublished()
        self.assertTrue(error_body.closed, "urllib HTTPError response must be closed on failure")

    def test_midstream_failure_cleans_stage_and_retry_works_without_partial_cache(self):
        class Interrupted(Response):
            def read(self, length=-1):
                if self.tell() > 0:
                    raise OSError("PRIVATE_TOKEN network disconnected")
                return super().read(min(length, 32))
        def interrupt_last(url):
            if url != self.urls[2]:
                return self.download(url)
            response = Interrupted(self.payloads[url])
            self.responses.append(response)
            return response
        self.network.side_effect = interrupt_last
        error = self.rejected(self.fetch)
        self.assertTrue(str(error).startswith("Disk 2:"))
        self.assertNotIn("PRIVATE_TOKEN", str(error))
        self.unpublished()
        self.network.side_effect = self.download
        self.assertEqual(self.output, self.fetch())
        builder.verify(self.output)

    def test_existing_unrecognized_output_is_never_replaced(self):
        self.output.mkdir()
        sentinel = self.output / "personal-note"
        sentinel.write_bytes(b"keep this")
        before = self.snapshot()
        self.rejected(self.fetch)
        self.assertEqual(before, self.snapshot())
        self.network.assert_not_called()
        raced = self.private / "appeared-during-download"
        def create_before_publication(url):
            if url == self.urls[-1]:
                raced.mkdir()
                (raced / "user-owned").write_bytes(b"also keep this")
            return self.download(url)
        self.network.side_effect = create_before_publication
        self.rejected(self.fetch, output=raced)
        self.assertEqual(b"also keep this", (raced / "user-owned").read_bytes())
        self.assertEqual(b"keep this", sentinel.read_bytes())
        self.assertEqual({self.output, raced}, set(self.private.iterdir()))
        self.assertTrue(all(response.closed for response in self.responses))

    def test_real_redirect_handler_never_requests_the_redirect_destination(self):
        requests = []
        class Redirect(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                self.send_response(302 if self.path == "/start" else 200)
                self.send_header("Location", "/followed")
                self.end_headers()
            def log_message(self, *_args):
                pass
        server = HTTPServer(("127.0.0.1", 0), Redirect)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            # Local HTTP exercises the handler itself; configured fetch URLs remain HTTPS-only.
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), fetcher.NoRedirect())
            with self.assertRaises(urllib.error.HTTPError) as caught:
                opener.open(f"http://127.0.0.1:{server.server_port}/start", timeout=2)
            self.assertEqual(302, caught.exception.code)
            caught.exception.close()
            self.assertEqual(["/start"], requests)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_default_transport_installs_no_redirect_and_uses_a_finite_socket_timeout(self):
        # Use the real transport function, not the per-test fake download hook.
        response = Response(b"synthetic")
        opener = Mock()
        opener.open.return_value = response
        with patch.object(urllib.request, "build_opener", return_value=opener) as make_opener:
            with self.real_download(self.urls[0]) as opened:
                self.assertIs(response, opened)
            self.assertTrue(any(isinstance(handler, fetcher.NoRedirect) for handler in make_opener.call_args.args))
            request = opener.open.call_args.args[0]
            self.assertEqual(self.urls[0], request.full_url)
            timeout = opener.open.call_args.kwargs["timeout"]
            self.assertGreater(timeout, 0)
            self.assertLessEqual(timeout, 60)
        self.assertTrue(response.closed)

    def test_chunked_responses_work_but_the_total_download_time_budget_is_bounded(self):
        def chunked(url):
            response = self.download(url)
            response.headers = {}
            return response
        self.network.side_effect = chunked
        self.assertEqual(self.output, self.fetch())
        builder.verify(self.output)
        before = self.snapshot()
        read1_calls = []
        class ReadOneOnly(Response):
            def read(self, _length=-1):
                raise AssertionError("A real buffered HTTP response must prefer read1")
            def read1(self, length=-1):
                read1_calls.append(length)
                return io.BytesIO.read(self, min(length, 17))
        def one_read(url):
            response = ReadOneOnly(self.payloads[url])
            self.responses.append(response)
            return response
        self.network.side_effect = one_read
        calls = []
        def elapsed():
            calls.append(1)
            # Permit exactly one read1, then exceed the budget before another read.
            return 0 if len(calls) <= 3 else fetcher.DOWNLOAD_TIMEOUT + 1
        with patch.object(fetcher.time, "monotonic", side_effect=elapsed):
            error = self.rejected(self.fetch, output=self.private / "slow-bundle")
        self.assertIn("time limit", str(error))
        self.assertTrue(str(error).startswith("ROM:"))
        self.assertEqual(1, len(read1_calls))
        self.assertEqual(before, self.snapshot())
        self.assertTrue(all(response.closed for response in self.responses))

    def test_cli_requires_private_acknowledgement_and_documents_offline_mode(self):
        help_result = subprocess.run([sys.executable, str(SCRIPT), "--help"], capture_output=True, text=True, timeout=5)
        self.assertEqual(0, help_result.returncode, help_result.stderr)
        self.assertIn("--acknowledge-private-assets", help_result.stdout)
        self.assertIn("--offline", help_result.stdout)
        result = subprocess.run([sys.executable, str(SCRIPT), "--config", str(self.config_path),
                                 "--output", str(self.output)], capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("acknowledge", result.stderr.lower())
        self.unpublished()


if __name__ == "__main__":
    unittest.main()
