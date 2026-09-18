package name.osher.gil.minivmac.hfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Macintosh resource fork, read far enough to get at named resources of one
 * type.
 *
 * The game keeps a saved party here, one resource per character, each named
 * with the character's own name. That is why this exists: the data fork holds
 * where the party is and what the world looks like, and the resource fork holds
 * who they are.
 *
 * Read-only, and deliberately shallow. It does not follow resource attributes,
 * does not decompress, and does not write.
 */
public final class ResourceFork {
    /** One resource: its type, id, optional name, and bytes. */
    public static final class Resource {
        public final String type, name;
        public final int id;
        public final byte[] data;
        Resource(String type, int id, String name, byte[] data) {
            this.type = type; this.id = id; this.name = name; this.data = data;
        }
        @Override public String toString() { return type + " " + id + " \"" + name + "\" (" + data.length + ")"; }
    }

    private final List<Resource> resources;

    private ResourceFork(List<Resource> resources) { this.resources = resources; }

    public List<Resource> all() { return new ArrayList<>(resources); }

    /** Every resource of one four-letter type, in the order the fork lists them. */
    public List<Resource> ofType(String type) {
        List<Resource> found = new ArrayList<>();
        for (Resource resource : resources) if (resource.type.equals(type)) found.add(resource);
        return found;
    }

    public static ResourceFork parse(byte[] fork) throws IOException {
        if (fork == null || fork.length < 16) throw new IOException("Not a resource fork");
        long dataOffset = u32(fork, 0), mapOffset = u32(fork, 4);
        long dataLength = u32(fork, 8), mapLength = u32(fork, 12);
        if (dataOffset < 0 || mapOffset < 0 || dataOffset + dataLength > fork.length
                || mapOffset + mapLength > fork.length || mapLength < 30)
            throw new IOException("Resource fork header does not fit the fork");

        int map = (int) mapOffset;
        int typeListAt = map + u16(fork, map + 24);
        int nameListAt = map + u16(fork, map + 26);
        int typeCount = (u16(fork, map + 28) + 1) & 0xffff;
        if (typeListAt + 2 + typeCount * 8 > fork.length) throw new IOException("Resource type list is truncated");

        List<Resource> resources = new ArrayList<>();
        for (int t = 0; t < typeCount; t++) {
            int entry = typeListAt + 2 + t * 8;
            String type = ascii(fork, entry, 4);
            int count = (u16(fork, entry + 4) + 1) & 0xffff;
            int referenceAt = typeListAt + u16(fork, entry + 6);
            for (int r = 0; r < count; r++) {
                int reference = referenceAt + r * 12;
                if (reference + 12 > fork.length) throw new IOException("Resource reference list is truncated");
                int id = (short) u16(fork, reference);
                int nameOffset = (short) u16(fork, reference + 2);
                long at = ((long) (fork[reference + 5] & 255) << 16)
                        | ((fork[reference + 6] & 255) << 8) | (fork[reference + 7] & 255);
                long start = dataOffset + at;
                if (start + 4 > fork.length) throw new IOException("Resource data is outside the fork");
                long length = u32(fork, (int) start);
                if (length < 0 || start + 4 + length > fork.length)
                    throw new IOException("Resource " + type + " " + id + " claims " + length + " bytes it does not have");
                String name = "";
                if (nameOffset >= 0 && nameListAt + nameOffset < fork.length) {
                    int n = nameListAt + nameOffset;
                    int nameLength = fork[n] & 255;
                    if (n + 1 + nameLength <= fork.length) name = ascii(fork, n + 1, nameLength);
                }
                resources.add(new Resource(type, id, name,
                        Arrays.copyOfRange(fork, (int) start + 4, (int) (start + 4 + length))));
            }
        }
        return new ResourceFork(resources);
    }

    private static String ascii(byte[] data, int at, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length && at + i < data.length; i++) {
            int value = data[at + i] & 255;
            text.append(value >= 0x20 && value < 0x7f ? (char) value : '?');
        }
        return text.toString();
    }

    private static int u16(byte[] data, int at) { return ((data[at] & 255) << 8) | (data[at + 1] & 255); }
    private static long u32(byte[] data, int at) {
        return ((long) (data[at] & 255) << 24) | ((data[at + 1] & 255) << 16)
                | ((data[at + 2] & 255) << 8) | (data[at + 3] & 255);
    }
}
