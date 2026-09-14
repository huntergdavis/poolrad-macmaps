import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import name.osher.gil.minivmac.mapper.*;

/** Emits identifiers/digests only; never emits game geometry. */
public final class AreaFingerprints {
    private static String digest(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: AreaFingerprints <extracted-game-directory> [PRM1-probe ...]");
        }
        Map<String, Integer> known = new TreeMap<>();
        Set<Integer> ids = new HashSet<>();
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (Path file : paths.filter(p -> p.getFileName().toString().matches("(?i)GEO[0-9]+\\.DAX")).sorted().toList()) {
                for (GeoMap map : DaxReader.readMaps(Files.readAllBytes(file))) {
                    byte[] geometry = Arrays.copyOfRange(map.copyData(), 2, 1026);
                    String hash = digest(geometry);
                    if (known.putIfAbsent(hash, map.id) != null) throw new IllegalStateException("Ambiguous geometry");
                    if (!ids.add(map.id)) throw new IllegalStateException("Duplicate record ID " + map.id);
                    AreaIdentity identity = AreaIdentity.resolve(map);
                    if (identity == null || !identity.id().equals("por-mac-v11-geo-" + map.id)) {
                        throw new IllegalStateException("Source record does not match the production identity catalog: " + map.id);
                    }
                    System.out.println(hash + "=por-mac-v11-geo-" + map.id);
                }
            }
        }
        for (int i = 1; i < args.length; i++) {
            byte[] probe = Files.readAllBytes(Path.of(args[i]));
            if (probe.length != 1200 || probe[0] != 'P' || probe[1] != 'R' || probe[2] != 'M' || probe[3] != '1') {
                throw new IllegalArgumentException("Expected a 1200-byte PRM1 probe: " + args[i]);
            }
            String hash = digest(Arrays.copyOfRange(probe, 176, 1200));
            PoolRadState state = PoolRadState.parse(probe);
            AreaIdentity identity = state == null ? null : AreaIdentity.resolve(state.map);
            System.err.println(args[i] + " -> " + (identity == null ? "unknown; notes disabled" : identity.id())
                    + " (source record " + known.get(hash) + ", " + hash + ")");
        }
        if (known.isEmpty()) throw new IllegalArgumentException("No GEO maps found in the supplied directory");
        System.err.println(known.size() + " unique full-geometry fingerprints verified against the production catalog");
    }
}
