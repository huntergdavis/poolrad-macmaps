package name.osher.gil.minivmac.hfs;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * One saved game, both forks and the Finder type that identifies it, in a
 * single file that can live in Android storage.
 *
 * A Macintosh file is two forks and a four-letter type, and Android storage has
 * no idea what any of that is. Writing only the data fork would produce a
 * backup the game cannot open, which is the kind of backup that is discovered
 * to be useless at the exact moment it is needed.
 *
 * So the format is explicit and self-checking: a magic word, a version, the
 * name, the type and creator, both forks, and a CRC over the lot. A backup that
 * has been truncated or altered is refused rather than restored, because
 * writing half a saved game over a good one is worse than not restoring at all.
 */
public final class SaveArchive {
    /** "PRSV": PoolRad SaVe. */
    public static final int MAGIC = 0x50525356;
    public static final int VERSION = 1;
    /** Nothing this is for is anywhere near this; it is a sanity bound. */
    public static final int MAX_FORK = 4 << 20;

    public final String name, type, creator;
    public final byte[] data, resource;

    public SaveArchive(String name, String type, String creator, byte[] data, byte[] resource) {
        if (name == null || name.isEmpty() || name.length() > 31)
            throw new IllegalArgumentException("A Macintosh file name is 1 to 31 characters");
        if (type == null || type.length() != 4 || creator == null || creator.length() != 4)
            throw new IllegalArgumentException("Type and creator are four characters each");
        if (data == null || resource == null) throw new IllegalArgumentException("Both forks are required");
        if (data.length > MAX_FORK || resource.length > MAX_FORK)
            throw new IllegalArgumentException("Improbably large fork");
        this.name = name; this.type = type; this.creator = creator;
        this.data = data.clone(); this.resource = resource.clone();
    }

    public int totalBytes() { return data.length + resource.length; }

    public byte[] pack() {
        byte[] nameBytes = latin(name);
        int length = 4 + 2 + 2 + nameBytes.length + 4 + 4 + 4 + data.length + 4 + resource.length + 4;
        byte[] out = new byte[length];
        int at = 0;
        at = put32(out, at, MAGIC);
        at = put16(out, at, VERSION);
        at = put16(out, at, nameBytes.length);
        System.arraycopy(nameBytes, 0, out, at, nameBytes.length); at += nameBytes.length;
        System.arraycopy(latin(type), 0, out, at, 4); at += 4;
        System.arraycopy(latin(creator), 0, out, at, 4); at += 4;
        at = put32(out, at, data.length);
        System.arraycopy(data, 0, out, at, data.length); at += data.length;
        at = put32(out, at, resource.length);
        System.arraycopy(resource, 0, out, at, resource.length); at += resource.length;
        CRC32 crc = new CRC32();
        crc.update(out, 0, at);
        put32(out, at, (int) crc.getValue());
        return out;
    }

    public static SaveArchive unpack(byte[] packed) throws IOException {
        if (packed == null || packed.length < 24) throw new IOException("Not a saved-game backup");
        if (u32(packed, 0) != MAGIC) throw new IOException("Not a saved-game backup");
        int version = u16(packed, 4);
        if (version != VERSION) throw new IOException("Backup version " + version + " is not understood");

        CRC32 crc = new CRC32();
        crc.update(packed, 0, packed.length - 4);
        if ((int) crc.getValue() != u32(packed, packed.length - 4))
            throw new IOException("This backup is damaged; it will not be restored");

        int at = 6;
        int nameLength = u16(packed, at); at += 2;
        if (nameLength < 1 || nameLength > 31 || at + nameLength > packed.length)
            throw new IOException("Backup has an improbable file name");
        String name = new String(packed, at, nameLength, java.nio.charset.StandardCharsets.ISO_8859_1);
        at += nameLength;
        if (at + 8 > packed.length) throw new IOException("Backup is truncated");
        String type = new String(packed, at, 4, java.nio.charset.StandardCharsets.ISO_8859_1); at += 4;
        String creator = new String(packed, at, 4, java.nio.charset.StandardCharsets.ISO_8859_1); at += 4;

        byte[] data = readFork(packed, at); at += 4 + data.length;
        byte[] resource = readFork(packed, at); at += 4 + resource.length;
        if (at != packed.length - 4) throw new IOException("Backup has unexpected trailing bytes");
        return new SaveArchive(name, type, creator, data, resource);
    }

    private static byte[] readFork(byte[] packed, int at) throws IOException {
        if (at + 4 > packed.length) throw new IOException("Backup is truncated");
        long length = u32(packed, at) & 0xffffffffL;
        if (length > MAX_FORK || at + 4 + length > packed.length)
            throw new IOException("Backup claims a fork it does not contain");
        return Arrays.copyOfRange(packed, at + 4, at + 4 + (int) length);
    }

    private static byte[] latin(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }
    private static int put16(byte[] out, int at, int value) {
        out[at] = (byte) (value >>> 8); out[at + 1] = (byte) value; return at + 2;
    }
    private static int put32(byte[] out, int at, int value) {
        out[at] = (byte) (value >>> 24); out[at + 1] = (byte) (value >>> 16);
        out[at + 2] = (byte) (value >>> 8); out[at + 3] = (byte) value; return at + 4;
    }
    private static int u16(byte[] data, int at) { return ((data[at] & 255) << 8) | (data[at + 1] & 255); }
    private static int u32(byte[] data, int at) {
        return ((data[at] & 255) << 24) | ((data[at + 1] & 255) << 16)
                | ((data[at + 2] & 255) << 8) | (data[at + 3] & 255);
    }
}
