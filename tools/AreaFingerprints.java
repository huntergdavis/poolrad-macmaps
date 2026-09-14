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
            throw new IllegalArgumentException("Usage: AreaFingerprints <extracted-game-directory> [PRM-probe ...]");
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
                    byte[] sample=new byte[1200];sample[0]='P';sample[1]='R';sample[2]='M';sample[3]='2';
                    sample[32]=1;sample[33]=1;sample[34]=(byte)(map.id>>>8);sample[35]=(byte)map.id;
                    System.arraycopy(geometry,0,sample,176,1024);
                    PoolRadState before=PoolRadState.parse(sample);
                    if(before==null || !identity.equals(before.area)) throw new IllegalStateException("Source prefix mismatch: "+map.id);
                    for(int door=944;door<1200;door++) sample[door]^=(byte)0xff;
                    PoolRadState changed=PoolRadState.parse(sample);
                    if(changed==null || !identity.equals(changed.area)) throw new IllegalStateException("Door changes lost identity: "+map.id);
                    System.out.println(hash + "=por-mac-v11-geo-" + map.id
                            + " prefix768=" + digest(Arrays.copyOf(geometry,768)));
                }
            }
        }
        for (int i = 1; i < args.length; i++) {
            byte[] probe = Files.readAllBytes(Path.of(args[i]));
            if(probe.length!=1200 || probe[0]!='P' || probe[1]!='R' || probe[2]!='M' || (probe[3]!='1' && probe[3]!='2'))
                throw new IllegalArgumentException("Expected a supported 1200-byte PRM1/PRM2 envelope: "+args[i]);
            PoolRadState state = PoolRadState.parse(probe);
            String hash = digest(Arrays.copyOfRange(probe, 176, 1200));
            // Use the packet decoder's authority decision. Re-resolving geometry
            // here would conceal a new packet's explicit stale/unknown-ID refusal.
            AreaIdentity identity = state == null ? null : state.area;
            System.err.println(args[i] + " -> " + (identity == null ? "unknown; notes disabled" : identity.id())
                    + " (PRM" + (char)probe[3] + ", source record " + known.get(hash) + ", " + hash + ")");
        }
        if (known.isEmpty()) throw new IllegalArgumentException("No GEO maps found in the supplied directory");
        System.err.println(known.size() + " unique full-geometry and immutable-prefix records verified; source-derived PRM2 door changes preserve IDs");
    }
}
