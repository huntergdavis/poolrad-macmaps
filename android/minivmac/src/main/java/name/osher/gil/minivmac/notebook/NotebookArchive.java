package name.osher.gil.minivmac.notebook;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** PRNA v1: UUID, counted raw files, SHA-256 trailer. No compressed or executable content. */
final class NotebookArchive {
    static final long MAX_BYTES = 64L * 1024 * 1024;
    static final int MAX_ENTRIES = 1 + 33 * (256 * 2 + 1);
    static final int MAX_ENTRY_BYTES = InkNote.MAX_TOTAL_POINTS * 8
            + InkNote.MAX_STROKES * 9 + 1024 + 20;
    private static final int MAGIC = 0x50524e41; // PRNA
    private static final int VERSION = 1;

    static final class Entry {
        final String path;
        final File file;
        final int length;
        Entry(String path, File file) throws IOException {
            validatePath(path);
            long bytes = file.length();
            if (!file.isFile() || bytes > maximumLength(path)) throw new IOException("Notebook entry is missing or too large");
            this.path = path; this.file = file; this.length = (int) bytes;
        }
    }

    static boolean area(String value) {
        return value.matches("por-mac-v11-geo-(0|[1-9]|[12][0-9]|3[0-2])");
    }

    static void validatePath(String path) throws IOException {
        if (path.equals("notebook.bin")) return;
        String[] pieces = path.split("/", -1);
        if (pieces.length != 2 || !area(pieces[0])) throw new IOException("Invalid notebook archive path");
        String name = pieces[1];
        if (name.equals("map.ink")) return; // Preserve unshipped prototype bytes without interpreting them.
        if (!name.matches("(0|[1-9][0-9]{0,2})\\.ink(\\.v1)?")
                || Integer.parseInt(name.substring(0, name.indexOf('.'))) > 255) {
            throw new IOException("Unrecognized notebook archive entry");
        }
    }

    private static int maximumLength(String path) { return path.equals("notebook.bin") ? 1044 : MAX_ENTRY_BYTES; }

    static void write(String id, List<Entry> entries, OutputStream destination) throws IOException {
        if (destination == null) throw new IllegalArgumentException("Missing backup destination");
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES) throw new IOException("Too many notebook entries");
        long size = 4 + 4 + 2 + id.length() + 4 + 32;
        for (Entry entry : entries) size += 2 + entry.path.length() + 4L + entry.length;
        if (size > MAX_BYTES) throw new IOException("Notebook backup exceeds 64 MiB");
        MessageDigest checksum = digest();
        DigestOutputStream hashed = new DigestOutputStream(new LimitedOutput(destination), checksum);
        DataOutputStream out = new DataOutputStream(hashed);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(id); out.writeInt(entries.size());
        byte[] buffer = new byte[8192];
        for (Entry entry : entries) {
            out.writeUTF(entry.path); out.writeInt(entry.length);
            try (InputStream in = new FileInputStream(entry.file)) {
                int remaining = entry.length;
                while (remaining > 0) {
                    int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0) throw new IOException("Notebook changed while being exported");
                    if (read == 0) continue;
                    out.write(buffer, 0, read); remaining -= read;
                }
                if (in.read() != -1) throw new IOException("Notebook changed while being exported");
            }
        }
        hashed.on(false); out.write(checksum.digest()); out.flush();
        // The document owner closes its stream and reports any final provider error.
    }

    /** Writes only into the caller's fresh invisible staging folder, never the live store. */
    static String read(InputStream source, File staging) throws IOException {
        if (source == null) throw new IllegalArgumentException("Missing backup source");
        MessageDigest checksum = digest();
        DigestInputStream hashed = new DigestInputStream(new LimitedInput(source), checksum);
        DataInputStream in = new DataInputStream(hashed);
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported notebook backup format");
        String id = in.readUTF();
        if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("Invalid backup notebook identity");
        }
        int count = in.readInt();
        if (count < 1 || count > MAX_ENTRIES) throw new IOException("Invalid notebook backup entry count");
        File book = new File(staging, id);
        if (!book.mkdir()) throw new IOException("Cannot stage notebook backup");
        Set<String> paths = new HashSet<>();
        byte[] buffer = new byte[8192];
        for (int i = 0; i < count; i++) {
            String path = in.readUTF();
            validatePath(path);
            if (!paths.add(path)) throw new IOException("Duplicate notebook backup entry");
            int length = in.readInt();
            if (length < 0 || length > maximumLength(path)) throw new IOException("Invalid notebook backup entry size");
            File file = new File(book, path), parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdir()) throw new IOException("Cannot stage notebook area");
            if (file.exists()) throw new IOException("Duplicate staged notebook entry");
            try (FileOutputStream out = new FileOutputStream(file)) {
                int remaining = length;
                while (remaining > 0) {
                    int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0) throw new IOException("Truncated notebook backup");
                    if (read == 0) continue;
                    out.write(buffer, 0, read); remaining -= read;
                }
                out.getFD().sync();
            }
        }
        if (!paths.contains("notebook.bin")) throw new IOException("Notebook backup has no identity record");
        byte[] expected = checksum.digest(), actual = new byte[32];
        hashed.on(false); in.readFully(actual);
        if (!MessageDigest.isEqual(expected, actual) || in.read() != -1) {
            throw new IOException("Notebook backup checksum or length is invalid");
        }
        return id;
    }

    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IOException("Backup checksum unavailable", unavailable); }
    }

    private static final class LimitedInput extends FilterInputStream {
        private long count;
        LimitedInput(InputStream in) { super(in); }
        @Override public int read() throws IOException {
            int result = in.read();
            if (result != -1 && ++count > MAX_BYTES) throw new IOException("Notebook backup exceeds 64 MiB");
            return result;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int result = in.read(bytes, offset, (int) Math.min(length, MAX_BYTES - count + 1));
            if (result > 0 && (count += result) > MAX_BYTES) throw new IOException("Notebook backup exceeds 64 MiB");
            return result;
        }
    }

    private static final class LimitedOutput extends FilterOutputStream {
        private long count;
        LimitedOutput(OutputStream out) { super(out); }
        @Override public void write(int value) throws IOException {
            if (++count > MAX_BYTES) throw new IOException("Notebook backup exceeds 64 MiB");
            out.write(value);
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if ((count += length) > MAX_BYTES) throw new IOException("Notebook backup exceeds 64 MiB");
            out.write(bytes, offset, length);
        }
    }
}
