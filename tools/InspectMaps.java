import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import name.osher.gil.minivmac.mapper.DaxReader;
import name.osher.gil.minivmac.mapper.GeoMap;

/** Read-only local validation; no game data is copied into source or APK assets. */
public class InspectMaps {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the extracted game folder");
        Set<Integer> ids = new HashSet<>();
        int files = 0;
        long open = 0, walls = 0, doorways = 0, ignoredBits = 0;
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("(?i)GEO[0-9]+\\.DAX")).sorted().toList()) {
                var maps = DaxReader.readMaps(Files.readAllBytes(path));
                for (GeoMap map : maps) {
                    if (!ids.add(map.id)) throw new IllegalStateException("Duplicate area across files: " + map.id);
                    for (int y=0; y<16; y++) for (int x=0; x<16; x++) for (int d=0; d<4; d++) {
                        int surface = map.wall(x,y,d), door = map.door(x,y,d);
                        GeoMap.EdgeKind expected;
                        if (surface == 0) {
                            expected = GeoMap.EdgeKind.OPEN; open++;
                            if (door != 0) ignoredBits++;
                        } else if (door == 0) {
                            expected = GeoMap.EdgeKind.WALL; walls++;
                        } else {
                            expected = GeoMap.EdgeKind.DOORWAY; doorways++;
                        }
                        if (map.edgeKind(x,y,d) != expected)
                            throw new IllegalStateException("Edge classification disagrees with Mac accessor: " + map.id);
                    }
                }
                System.out.println(path.getFileName() + ": " + maps.size() + " maps OK");
                files++;
            }
        }
        if (files == 0) throw new IllegalStateException("No GEO files found");
        System.out.println(files + " files, " + ids.size() + " unique maps validated");
        System.out.printf("Directional edges: %d open, %d wall, %d neutral doorway; %d surface-free edges ignore door bits%n",
                open, walls, doorways, ignoredBits);
        System.out.println("Source-data audit only; not a gameplay or secret/lock-state acceptance claim.");
    }
}
