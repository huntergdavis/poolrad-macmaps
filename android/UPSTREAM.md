# Android emulator base

Imported from [Gil Osher's Mini vMac Android port](https://github.com/dolfin/minivmac4android)
at revision `b6ff92ce04a006e642ebceadf90b73d9f0b9e454`.
Original source, copyright notices, and GPL version 2 license are preserved;
see [COPYING](COPYING) and [README](README).

PoolRad Mac Maps adds read-only mapping diagnostics and a separate code-wheel
helper that sends ordinary keyboard input only on explicit request. The
separate `com.hunterdavis.poolradmacmaps.plus` and `.ii` application IDs do not
replace the original Play Store apps. No ROM, game data, or disk images belong
in this source tree or its APKs.

## Local build

Use JDK 17, Android SDK platform 34/build-tools 34.0.0, and NDK 27.0.12077973.
Set `ANDROID_HOME` to your installed SDK and run from this directory:

```sh
./gradlew :minivmac:assembleMacPlusDebug :minivmac:testMacPlusDebugUnitTest
```

The `macII` flavor is also available for local tests with a Macintosh II ROM.
It does not establish which machine the user's installed app emulates.
Development builds use Android's normal debug signing. Release signing is
deliberately unconfigured; no upstream developer keys are required.

ROMs, disks, and diagnostics use private internal app storage; cloud backup is
disabled. The app's own file provider does not expose the snapshot directory.
See [local testing](../docs/LOCAL_TESTING.md) for the proven setup, test-only APK
distinction, and current map-tracking limitations.
