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
 * Legacy packets require exact full geometry. Verified PRM2 IDs additionally
 * support an exact immutable-prefix match, with only the door plane excluded.
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
    }, new String[]{
            "0 56cb941735e8960c4359cd8b3d3479e5bfddcad98e0bac534ec42ba2b8b018da",
            "1 a9fc013905fa93b8ab5f1fcf3e9be8c656976c88e463ac53c946a2c7a482e517",
            "2 ae1f719816db51a6be45e69accb07820a432fed2c578ea2131774c45131be61b",
            "3 148af6a6500c8753846798c79b355058d5863555619286ad649373844dffcdae",
            "4 715692c0104186a5240401e870fc1cf097022cf6eff027f3f55f63bdd85e7c91",
            "5 87ddebcd5250c24d2a1d560de5bf4f8b51d3d3a600c37d02eb853434234dd2e9",
            "6 149618524390eb7631e6755d554115fc2ba852036dc0036633efc6f9864f57e9",
            "7 050efc154ed2471a76239446e14e4f85c2c1d223fad854c78da89e98537ae5f9",
            "9 c57b4720d3c965838b7441a2fb0befab07ed8fdfc9fb4e9ec03866578f92e653",
            "10 8dbb0e7f943e2ade4a007836b12fffb4380d9988385152ec3f94f098b8476526",
            "13 ba2f46c45739c9681d112902fb99b25c68c14a9e1610d4eca112b0ef8ae28101",
            "14 d5297f77ab2d9156aaeb0385ebac2f22098751d1c19b2d295d48153c4a2407c5",
            "15 e433cf4b1cf6f48a2dc73212b006fa49d8fd0c57fc4f8d129a872b0a43b6c126",
            "16 2153b3c4d8ec2f856a0d76841ff2480c52fe77066706bd4ca0ece79c80e59e0b",
            "17 1b08a4770209ebdd8f2a3318c046a486b00a3d44c5c2aac82bc005bdaa9981bf",
            "18 0eebcc27225c5190ee896a220cc07dc961e9715a0253d4794c90ac5a0bd7ef32",
            "20 501a9003e58ce9759d82311d8188b67ffb0ab885e272c5f41aa1e43967cc8749",
            "21 5e44eed73f0ef523782a0ee580ea83f37955c63cc4597c800501e3f3e4efb0db",
            "22 70b32530ccdcb3865fca91e66416da97c79569a671c4087b37ecddd75a2ccfda",
            "23 ac9374e02c7ca13a729d05c84277c8221d8b51422d0c7e6ad7b87e8786004e0d",
            "24 a96f1e2c379a4cf4a9eb0a9986047b4dac38eaeef7ef9d612bfae03fda1bc833",
            "25 8566d1c98bcd6c93e58a79939012ff117660794be32b1938f959afb6b6827345",
            "26 f8f15867072c3ffab3191ffa980dfa358ac2b15df38343158e410e5dfc709956",
            "27 2d1fe450ae9582be37451d42bc8d318b463e28d62f5f1204d790e3238d7563b3",
            "28 9e48caf04cdefb2f9ad5959b781216ece622274c2ec45d120f6fcad42c14fe2f",
            "29 bbdbc10513bfb668a022ff7cb2d51eecd7a2e2ce7e275584d1c5d557aa56f0d4",
            "30 f12c2192b28bb04a3fd5eb298e8301c5bde207501296049db0f06033aab3fdbe",
            "31 f8d085adcf53b5f1f6b9796d9d6cb1e345df400dba3304c96ca6eb3a3d43c173",
            "32 05ee1697d4b99791b0bbdc068c05d92ca22c092e5dcaa240ebd368f56c8a6ece"
    });

    private final int recordId;

    private AreaIdentity(int recordId) { this.recordId = recordId; }

    public String id() { return PREFIX + recordId; }

    /** Names are tied to original Macintosh GEO loads, not a similarly numbered DOS map. */
    public String label() {
        switch (recordId) {
            case 0: return "New Phlan";
            case 1: return "Buccaneer Base";
            case 2: return "Cadorna Textile House";
            case 3: return "Valjevo Castle — Northwest";
            case 4: return "Valjevo Castle — Northeast";
            case 5: return "Valjevo Castle — Southeast";
            case 6: return "Valjevo Castle — Southwest";
            case 7: return "Valjevo Castle — Inner Tower";
            case 9: return "Stojanow Gate";
            case 10: return "Valhingen Graveyard";
            case 13: return "Kobold Caves";
            case 14: return "Kovel Mansion";
            case 15: return "Mendor's Library";
            case 16: return "Lizardmen Keep";
            case 17: return "Nomad Camp";
            case 18: return "Podal Plaza";
            case 20: return "Slums of Phlan";
            case 21: return "Sokal Keep";
            case 22: return "Sorcerer's Pyramid — Entrance";
            case 23: return "Sorcerer's Pyramid — Inner Chambers";
            case 24: return "Temple of Bane";
            case 25: return "Dark Cave (25)";
            case 26: return "Grove and Ruined Huts";
            case 27: return "Dark Cave (27)";
            case 28: return "Zhentil Outpost";
            case 29: return "Kuto's Well";
            case 30: return "Lizardmen Catacombs";
            case 31: return "Mansion District";
            case 32: return "Kuto's Well Catacombs";
            default: return "Area " + recordId;
        }
    }

    /** Null means notes must be unavailable, not attached to a shared unknown area. */
    public static AreaIdentity resolve(GeoMap map) { return KNOWN.resolve(map); }

    /** A reported record number alone cannot authenticate the currently loaded geometry. */
    static AreaIdentity resolve(int recordId, GeoMap map) { return KNOWN.resolve(recordId, map); }

    /** Only the validated native PRM2 path may use the independently verified prefix. */
    static AreaIdentity resolveMutable(int recordId, GeoMap map) { return KNOWN.resolveMutable(recordId, map); }

    @Override public boolean equals(Object other) {
        return other instanceof AreaIdentity && recordId == ((AreaIdentity) other).recordId;
    }

    @Override public int hashCode() { return recordId; }

    @Override public String toString() { return id(); }

    // Package-private hooks keep synthetic catalog tests independent of copyrighted map fixtures.
    static AreaIdentity resolveFingerprint(String digest) { return KNOWN.resolveFingerprint(digest); }

    static int catalogSize() { return KNOWN.byDigest.size(); }

    static int prefixCatalogSize() { return KNOWN.byPrefix.size(); }

    static String fingerprint(GeoMap map) {
        return fingerprint(map, 1024);
    }

    static String prefixFingerprint(GeoMap map) { return fingerprint(map, 768); }

    private static String fingerprint(GeoMap map, int length) {
        if (map == null) return null;
        byte[] record = map.copyData();
        boolean hasWalls = false;
        for (int i = 2; i < 514; i++) hasWalls |= record[i] != 0;
        if (!hasWalls) return null; // Empty startup payloads do not describe an area.
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            // The two-byte DAX prefix is absent from live RAM.
            sha256.update(record, 2, length);
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
        private final Map<String, AreaIdentity> byPrefix;

        Catalog(String[] rows) { this(rows, null); }

        Catalog(String[] rows, String[] prefixRows) {
            Map<String,AreaIdentity> exact = readRows(rows);
            Map<String,AreaIdentity> prefix = prefixRows == null ? Collections.emptyMap() : readRows(prefixRows);
            boolean valid = exact != null && prefix != null;
            if(valid && prefixRows != null) {
                Set<AreaIdentity> ids = new HashSet<>(exact.values());
                valid = ids.equals(new HashSet<>(prefix.values()));
            }
            byDigest = valid ? Collections.unmodifiableMap(exact) : Collections.emptyMap();
            byPrefix = valid ? Collections.unmodifiableMap(prefix) : Collections.emptyMap();
        }

        private static Map<String,AreaIdentity> readRows(String[] rows) {
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
            return valid ? entries : null;
        }

        AreaIdentity resolve(GeoMap map) { return resolveFingerprint(fingerprint(map)); }

        AreaIdentity resolve(int recordId, GeoMap map) {
            AreaIdentity exact = resolve(map);
            return exact != null && exact.recordId == recordId ? exact : null;
        }

        AreaIdentity resolveMutable(int recordId, GeoMap map) {
            String digest = prefixFingerprint(map);
            AreaIdentity matched = digest == null ? null : byPrefix.get(digest);
            return matched != null && matched.recordId == recordId ? matched : null;
        }

        AreaIdentity resolveFingerprint(String digest) {
            return digest == null ? null : byDigest.get(digest);
        }
    }
}
