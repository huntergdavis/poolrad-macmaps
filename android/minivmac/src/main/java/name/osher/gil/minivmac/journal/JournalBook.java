package name.osher.gil.minivmac.journal;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded private reference-book format. No code, links, HTML, or guest access. */
public final class JournalBook {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int[] PROCLAMATIONS = {59,64,78,101,109,110,114,120,126,129,134,154,156,170,190,201,204,214};
    public static final class Key {
        public final int kind, number;
        public Key(int kind, int number) {
            boolean valid = kind == 0 && number >= 1 && number <= 58
                    || kind == 2 && number >= 1 && number <= 23
                    || kind == 1 && Arrays.binarySearch(PROCLAMATIONS, number) >= 0;
            if (!valid) throw new IllegalArgumentException("That reference number does not exist in this journal.");
            this.kind = kind; this.number = number;
        }
        public String label() { return (kind == 0 ? "Journal " : kind == 1 ? "Proclamation " : "Tavern tale ") + number; }
        @Override public String toString() { return kind + ":" + number; }
        @Override public int hashCode() { return kind * 1000 + number; }
        @Override public boolean equals(Object other) { return other instanceof Key && toString().equals(other.toString()); }
        public static Key parse(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || !value.matches("[0-2]:[1-9][0-9]{0,2}")) throw new IllegalArgumentException("Invalid journal key");
            return new Key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }
    public static final class Block {
        public final String text;
        private final byte[] image;
        private Block(String text, byte[] image) { this.text = text; this.image = image; }
        public byte[] image() { return image == null ? null : image.clone(); }
        public boolean isImage() { return image != null; }
    }
    public final String source;
    private final Map<Key, List<Block>> entries;
    private JournalBook(String source, Map<Key, List<Block>> entries) { this.source = source; this.entries = entries; }
    public List<Block> entry(Key key) { return entries.get(key); }
    public Set<Key> keys() { return Collections.unmodifiableSet(entries.keySet()); }

    public static byte[] readBytes(InputStream in) throws IOException {
        if (in == null) throw new IOException("Journal file could not be opened");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IOException("Journal exceeds 16 MiB");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    private static byte[] blob(DataInputStream in, int max) throws IOException {
        int size = in.readInt();
        if (size < 1 || size > max || size > in.available()) throw new IOException("Invalid journal block size");
        byte[] bytes = new byte[size]; in.readFully(bytes); return bytes;
    }
    private static String text(byte[] value) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString(); }
        catch (Exception failure) { throw new IOException("Invalid journal text encoding", failure); }
    }
    public static JournalBook read(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Journal exceeds 16 MiB");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0x50524a52 || in.readUnsignedByte() != 1) throw new IOException("Not a supported .prjr journal book");
        String source = text(blob(in, 512));
        int count = in.readUnsignedShort();
        if (count != 99) throw new IOException("Expected the complete 99-entry journal");
        Map<Key, List<Block>> entries = new LinkedHashMap<>();
        for (int e = 0; e < count; e++) {
            Key key;
            try { key = new Key(in.readUnsignedByte(), in.readUnsignedShort()); }
            catch (IllegalArgumentException failure) { throw new IOException(failure.getMessage(), failure); }
            if (entries.containsKey(key)) throw new IOException("Duplicate journal entry");
            int blocks = in.readUnsignedShort();
            if (blocks < 1 || blocks > 32) throw new IOException("Invalid journal block count");
            List<Block> content = new ArrayList<>();
            long imagePixels = 0;
            for (int b = 0; b < blocks; b++) {
                int kind = in.readUnsignedByte();
                if (kind == 0) content.add(new Block(text(blob(in, 65536)), null));
                else if (kind == 1) {
                    byte[] png = blob(in, 2 * 1024 * 1024);
                    if (png.length < 33 || ByteBuffer.wrap(png).getLong() != 0x89504e470d0a1a0aL
                            || ByteBuffer.wrap(png, 8, 8).getLong() != 0x0000000d49484452L)
                        throw new IOException("Expected a PNG illustration");
                    int w = ByteBuffer.wrap(png,16,4).getInt(), h = ByteBuffer.wrap(png,20,4).getInt();
                    if (w < 1 || h < 1 || w > 2048 || h > 4096 || (long) w * h > 4_000_000)
                        throw new IOException("Illustration dimensions exceed safe limits");
                    imagePixels += (long) w * h;
                    if (imagePixels > 8_000_000) throw new IOException("Too much illustration memory in one entry");
                    content.add(new Block(null, png));
                } else throw new IOException("Unknown journal block type");
            }
            entries.put(key, Collections.unmodifiableList(content));
        }
        if (in.read() != -1) throw new IOException("Unexpected data after journal entries");
        return new JournalBook(source, Collections.unmodifiableMap(entries));
    }
}
