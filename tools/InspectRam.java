import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import name.osher.gil.minivmac.mapper.DaxReader;
import name.osher.gil.minivmac.mapper.GeoMap;

/** Local discovery aid. Matches are candidates, not proof of the active map. */
public final class InspectRam {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: InspectRam RAM_FILE EXTRACTED_GAME_FOLDER");
        byte[] ram = Files.readAllBytes(Path.of(args[0]));
        if (ram.length > 16 * 1024 * 1024) throw new IllegalArgumentException("Unexpected RAM size");
        try (Stream<Path> paths = Files.walk(Path.of(args[1]))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.getFileName().toString().matches("(?i)GEO[0-9]+\\.DAX"))::iterator) {
                for (GeoMap map : DaxReader.readMaps(Files.readAllBytes(path))) {
                    byte[] data = map.copyData();
                    // The two leading bytes need not be copied into the game's wall buffer.
                    byte[] walls = Arrays.copyOfRange(data, 2, 514);
                    for (int at = 0; at <= ram.length - walls.length; at++) {
                        int i = 0;
                        while (i < walls.length && ram[at + i] == walls[i]) i++;
                        if (i == walls.length) System.out.printf("Map %d wall-buffer candidate at 0x%06x%n", map.id, at);
                    }
                }
            }
        }
    }
}
