# Optional private asset fetching

Already have local ROM/disks? Keep using [personal packaging](PERSONAL_PACKAGE.md).
This alternative fetches **only sources you explicitly configure**, verifies the
exact bytes, then feeds the same private APK builder. Cloning the repository,
ordinary public builds, and the installed app never invoke this downloader.

No sources or private images are supplied. This is a personal build workflow,
not a public ready-to-play download. A source URL does not establish permission
to redistribute its contents; keep the fetched files and resulting APK private.

## Configure once

Use Python 3.10+ and the normal [Android prerequisites](INSTALL.md#build-an-apk-from-source).
Create `android/minivmac/private-assets/sources.json` locally; that entire directory
is ignored by Git. Fill in your own source URLs, exact sizes and SHA-256 hashes:

```json
{
  "format": 1,
  "rom": {
    "url": "https://example.invalid/your-pinned-source/MacII.ROM",
    "bytes": 262144,
    "sha256": "<64 lowercase hexadecimal characters>"
  },
  "disks": [
    {
      "url": "https://example.invalid/your-pinned-source/combined-boot-game.dsk",
      "bytes": 33554432,
      "sha256": "<64 lowercase hexadecimal characters>"
    }
  ]
}
```

These placeholders intentionally cannot download anything. Determine hashes
and sizes from trusted, known-good copies, for example `sha256sum MacII.ROM`
and `wc -c MacII.ROM`. Do not accept a changed download by blindly replacing its
expected hash. Review the new input deliberately and keep campaign backups.

- URLs must be direct HTTPS file URLs. Redirects, embedded credentials, query
  strings and fragments are rejected. Configure the final URL instead.
- For `raw.githubusercontent.com`, use the full 40-character commit ID in
  `/OWNER/REPO/COMMIT/path`, never a moving branch or tag. Other HTTPS sources
  are content-pinned by the mandatory size and SHA-256. No repository is cloned.
- The manifest is local and bounded to 64 KiB, with exact keys and no duplicates.
  It is configuration, not a downloaded script or remote manifest.
- Supply a supported Mac II ROM and one to eight **raw disks**, first disk first
  in boot order. Disks must be 512-byte aligned, at most 128 MiB each / 256 MiB
  together. No archive extraction or automatic boot-disk conversion occurs.
- Authentication tokens/signed URLs are not supported. For authenticated
  repositories, retrieve files yourself and use the existing local builder.

## Fetch, verify, build

From `android/`, using the absolute path to your private manifest:

```sh
./gradlew :minivmac:assembleMacIIPersonal \
  -PpoolradPersonalSources=/absolute/path/to/private-assets/sources.json \
  -PpoolradAllowAssetFetch=true
```

Choose **either** `poolradPersonalSources` or the earlier `poolradPersonalBundle`,
not both. The explicit acknowledgement is required even when reusing cached
files. Fetching alone is available as `:minivmac:fetchPersonalAssets` with those
same two properties. There are no checkout hooks, default download sources,
automatic release uploads or new cloud CI jobs.

Downloads become a verified B1 bundle beneath
`android/minivmac/private-assets/fetched/<manifest-sha256>/`. Temporary downloads
are not build inputs. Sizes/hashes and the full supported-ROM checksum must pass
before publication; the manifest is tied to the exact bytes used for its cache key.
Only the personal build type receives those assets. The usual
`assembleMacIIDebug` / public release variants remain bring-your-own-files even
when private properties and cached files are present.

The output and installation behavior are unchanged:

```text
android/minivmac/build/outputs/apk/macII/personal/minivmac-macII-universal-personal.apk
```

Use the [same-signing-key, first-use and save-preservation instructions](PERSONAL_PACKAGE.md).
This pipeline does not update a campaign already installed on your tablet.

## Reuse and failures

Every personal invocation re-verifies cached files, including when Gradle would
otherwise consider asset preparation up to date. A complete matching cache
causes **zero download requests** and survives `gradlew clean`. Add `--offline`
to Gradle to require cached inputs; a missing cache then fails without networking.
Gradle's own dependencies must also already be cached for an offline APK build.

Missing sources, rejected redirects, timeouts, truncated/oversized files,
changed checksums, invalid ROMs, and corrupt caches stop the build. There is no
fallback to stale generated assets or silent acceptance of different bytes.
Errors identify the affected ROM/disk without printing its private URL.

An existing cache is never overwritten or automatically repaired. If corrupted,
move that **specific cache directory** aside yourself, then retry; inspect it
first if it might contain anything you need. A deliberately edited manifest
selects a new cache key and leaves the old bundle intact. Symlinked outputs and
destinations outside the ignored private-assets directory are rejected.

For direct use from the repository root:

```sh
python3 tools/fetch-personal-assets.py \
  --config android/minivmac/private-assets/sources.json \
  --output android/minivmac/private-assets/my-fetched-bundle \
  --acknowledge-private-assets
```

Add `--offline` for cache-only verification. The result also works with the
original `-PpoolradPersonalBundle=/absolute/path/to/my-fetched-bundle` workflow.
The helper has no publish command. Never upload a personal APK with the public
release assets; the [public artifact check](../tools/check-wheel-apk.mjs) rejects
private payloads and metadata.

## Checks

```sh
python3 tools/test-fetch-personal-assets.py -v
python3 tools/test-personal-package.py -v
```

Tests use synthetic bytes, not proprietary firmware or game data.
[Local evidence](LOCAL_TESTING.md) records the actual build checks; this
build-tool feature adds no physical tablet, stylus or e-ink acceptance claim.
