package name.osher.gil.minivmac.mapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Conservative identity for the observed Macintosh v1.1 GEO records.
 * Only exact, known full-geometry matches are suitable for a notebook key.
 * These are one-way checksums, not bundled game maps. See docs/AREA_IDENTITY.md.
 */
public final class AreaIdentity {
    private static final String PREFIX = "por-mac-v11-geo-";
    private static final Catalog KNOWN = new Catalog(new String[]{
            "0 4d2541a2db2e3c3af90a9f1de437e2483146be72a91a766f7b970aec9d2db326",
            "1 426e4d2072b7877e395b73ca00e66031e3d74146f11e766af8ed2d58b04cd1d5",
            "2 049b3e50028c5ba0558449cb64bc494b6c2facb3d104f39584adea2e34081ed2",
            "3 e43075f80b6ec69c5ceaff25949781470852f0a3c00c5efd4b78eb5bb0966dd2",
            "4 695f9c6e5862ae426ea3a9f4153c2b0bf96ed1fc25d9531651883052ad601124",
            "5 48418dba6c4bf0c8143c12a10641f97cabfc32b51ee6281d61970e39ad19a6b3",
            "6 72306cc2ded8ce47ac50b17d8a731d7abb8ae7d87bc52643b93e94fee868122c",
            "7 66b0e9ec4f3bb0371bd4b0720b71d0d713f7e5505569f1b63ee9567c9fec9a5a",
            "9 8c53a204869ee27bceb259bb41b14a6c73f6a7fbd5b37b97b7781618be07812f",
            "10 a52271fd24dc4b3edba8c61d30b812cb5bb86ba201320e6682d108f5d28d9a1d",
            "13 deec6950c261bd4bbcf0ebddb00e230d0f8d00e7383057edbdd9e65d3a333326",
            "14 bab54eddd12f518aa1e0e3977c54a574a0e9eade82771a8f340193db743fdb50",
            "15 33776e5c061220ca25a8211020558e7606da5fe457898826c2764559af7ca5b8",
            "16 b7ec854a0e97f8a65ef37658daa7d45b7dda3796ebee44c17f70af86c155db07",
            "17 ccdb750f32b71e41372dccf4d1fabb118f51dad5f9abe90bc315e05957e5aa44",
            "18 e07a83b85aace02f183156a54841fa1d131a525912b838109ae719ad909822b6",
            "20 ad16708446ea94cff037db17fd342e222a9166a4eae413c8dba4da94336b39f8",
            "21 d1164aefb958eb1dbd7f9d561deb117d8ceaceaea0e46b73be051f9e4db05808",
            "22 26de29faff07d7bb838103d52c535051589422bcb2c4a59d730544e7264a76a1",
            "23 a03bc43b605540e49092b35dafce74ee5c1f6e02043bb8ff39715d309bb8dae8",
            "24 1f12feda9163a26e7e55a9a8dbc9dbcfb0c148ddd2be3ef2474ffe16c59088f3",
            "25 ced0ef7f4e151b0ac05ade25602c179e14f2b84bb5612c132b62f4f4b70295a4",
            "26 3a0260f56c53f069d02256031a077d424d6f4cea9965547a1c6e29aa76276d32",
            "27 ad6eb53b630d133addfd086c9c29f3045aaa8c4b8301576f258b7a6667eb71a6",
            "28 2261b1578c6f6553353b47bfadead2192b80c7554ea4529f6ba85050c4719dfa",
            "29 9100d3e882bb4ae8a20ef9b2467ac918e9b02e398bac4a78673ce9202b630a0e",
            "30 959a6873313cb7b4120d3d4179c3d6f639463d01e9835d9b4a437f7ad8edc8c5",
            "31 c50949d6a7f80578940222825d852cd5050d65060d49fd0babbdc0c522aba15a",
            "32 d50ae52621114d264567cd8ee26b119d23cd95ad852d2fee54dcd5fb81654d8f"
    });

    private final int recordId;

    private AreaIdentity(int recordId) { this.recordId = recordId; }

    public String id() { return PREFIX + recordId; }

    /** Only New Phlan's name has been independently paired with the live guest. */
    public String label() { return recordId == 0 ? "New Phlan" : "Area " + recordId; }

    /** Null means notes must be unavailable, not attached to a shared unknown area. */
    public static AreaIdentity resolve(GeoMap map) { return KNOWN.resolve(map); }

    @Override public boolean equals(Object other) {
        return other instanceof AreaIdentity && recordId == ((AreaIdentity) other).recordId;
    }

    @Override public int hashCode() { return recordId; }

    @Override public String toString() { return id(); }

    // Package-private hooks keep synthetic catalog tests independent of copyrighted map fixtures.
    static AreaIdentity resolveFingerprint(String digest) { return KNOWN.resolveFingerprint(digest); }

    static int catalogSize() { return KNOWN.byDigest.size(); }

    static String fingerprint(GeoMap map) {
        if (map == null) return null;
        byte[] record = map.copyData();
        boolean hasWalls = false;
        for (int i = 2; i < 514; i++) hasWalls |= record[i] != 0;
        if (!hasWalls) return null; // Empty startup payloads do not describe an area.
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            // The two-byte DAX prefix is absent from live RAM. Include all four planes.
            sha256.update(record, 2, 1024);
            char[] chars = new char[64];
            final char[] hex = "0123456789abcdef".toCharArray();
            byte[] digest = sha256.digest();
            for (int i = 0; i < digest.length; i++) {
                chars[2 * i] = hex[(digest[i] & 255) >>> 4];
                chars[2 * i + 1] = hex[digest[i] & 15];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException unavailable) {
            return null; // Fail closed, including on a broken device provider.
        }
    }

    /** Any malformed or duplicate row invalidates the whole catalog, never first-match wins. */
    static final class Catalog {
        private final Map<String, AreaIdentity> byDigest;

        Catalog(String[] rows) {
            Map<String, AreaIdentity> entries = new HashMap<>();
            Set<Integer> ids = new HashSet<>();
            boolean valid = rows != null && rows.length > 0;
            if (valid) {
                for (String row : rows) {
                    if (row == null || !row.matches("(?:0|[1-9][0-9]{0,2}) [0-9a-f]{64}")) {
                        valid = false;
                        break;
                    }
                    int separator = row.indexOf(' ');
                    int id = Integer.parseInt(row.substring(0, separator));
                    String digest = row.substring(separator + 1);
                    if (id > 255 || !ids.add(id) || entries.containsKey(digest)) {
                        valid = false;
                        break;
                    }
                    entries.put(digest, new AreaIdentity(id));
                }
            }
            byDigest = valid ? Collections.unmodifiableMap(entries) : Collections.emptyMap();
        }

        AreaIdentity resolve(GeoMap map) { return resolveFingerprint(fingerprint(map)); }

        AreaIdentity resolveFingerprint(String digest) {
            return digest == null ? null : byDigest.get(digest);
        }
    }
}
