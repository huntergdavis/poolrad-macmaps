package name.osher.gil.minivmac.mapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checked GEO/DAX reader. Format and RLE adapted from Gold Box Explorer,
 * Copyright (c) 2018 Bil Simser (MIT); see licenses/GoldBoxExplorer-MIT.txt.
 * No game data is embedded. Unpacked GEO records are exactly 1,026 bytes.
 */
public final class DaxReader {
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private DaxReader() {}

    private static int word(byte[] bytes, int at) {
        return (bytes[at] & 255) | ((bytes[at + 1] & 255) << 8);
    }

    private static long dword(byte[] bytes, int at) {
        return word(bytes, at) | ((long) word(bytes, at + 2) << 16);
    }

    public static List<GeoMap> readMaps(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 11 || bytes.length > MAX_FILE_BYTES)
            throw new IOException("GEO file size is invalid");
        int headerSize = word(bytes, 0), dataStart = headerSize + 2;
        if (headerSize == 0 || headerSize % 9 != 0 || headerSize / 9 > 256 || dataStart > bytes.length)
            throw new IOException("Invalid DAX index");
        List<GeoMap> maps = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (int p = 2; p < dataStart; p += 9) {
            int id = bytes[p] & 255;
            long start = dataStart + dword(bytes, p + 1);
            int unpacked = word(bytes, p + 5), stored = word(bytes, p + 7);
            if (!ids.add(id)) throw new IOException("Duplicate map ID " + id);
            if (start > bytes.length || start + stored > bytes.length || stored == 0)
                throw new IOException("Truncated map " + id);
            if (unpacked != 1026 && !(unpacked == 0 && stored == 1026))
                throw new IOException("Not a supported 16x16 GEO record: " + id);
            byte[] block = unpacked == 0 ? Arrays.copyOfRange(bytes, (int) start, (int) start + stored)
                    : decode(bytes, (int) start, stored, unpacked);
            maps.add(new GeoMap(id, block));
        }
        return Collections.unmodifiableList(maps);
    }

    static byte[] decode(byte[] bytes, int start, int length, int expected) throws IOException {
        byte[] result = new byte[expected];
        int source = start, end = start + length, target = 0;
        while (source < end) {
            int control = bytes[source++];
            int count = control >= 0 ? control + 1 : -control;
            if (count > expected - target) throw new IOException("RLE exceeds unpacked size");
            if (control >= 0) {
                if (count > end - source) throw new IOException("Truncated RLE literal");
                System.arraycopy(bytes, source, result, target, count);
                source += count;
            } else {
                if (source >= end) throw new IOException("Truncated RLE repeat");
                Arrays.fill(result, target, target + count, bytes[source++]);
            }
            target += count;
        }
        if (target != expected) throw new IOException("RLE unpacked size mismatch");
        return result;
    }
}
