package name.osher.gil.minivmac;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Where whole-machine save states live on disk, and how they are packed.
 *
 * A raw save state is the ~8.9 MB machine image the emulator hands over. Most of
 * it -- the Mac OS, the game code, untouched heap -- is identical between one
 * save and the next, so we do not store a whole image every time. The owner's
 * design: keep the first save as a single reference template, and store every
 * later save as only its difference from that reference.
 *
 * The difference is a byte-wise XOR against the reference, gzipped. Where a save
 * matches the reference the XOR is zero, and long runs of zero compress to
 * almost nothing, so a typical save is tens of KB instead of a megabyte. The
 * reference itself is written once (gzipped, ~1.3 MB) and kept; it is not baked
 * into the app, because a machine image depends on the player's own ROM, system
 * and game, which the app never ships.
 *
 * XOR reconstruction is exact regardless of how similar the reference is: the
 * image is always {@code reference XOR (reference XOR image)}. The only
 * requirement for correctness is that the same reference bytes are present at
 * save and at load, which a persistent file and a CRC check guarantee.
 *
 * Nothing here touches a guest disk or the game's own saves; these are the
 * companion's own files in its private storage.
 */
public final class SaveStateStore {
    public static final String EXTENSION = ".prqs";  /* PoolRad quick state */
    public static final String QUICK_NAME = "Quick save";
    /** The reference template every diff is measured against; kept, never listed. */
    private static final String REFERENCE_NAME = "reference.prqref";

    /** Save-file tags. v2 carries a mode byte and may be a diff; v1 was always a full image. */
    private static final byte[] MAGIC2 = {'P', 'R', 'Q', 'S', '2', '\n'};
    private static final byte[] MAGIC1 = {'P', 'R', 'Q', 'S', '1', '\n'};
    /** Reference-file tag. */
    private static final byte[] REFERENCE_MAGIC = {'P', 'R', 'Q', 'R', '1', '\n'};

    private static final int MODE_FULL = 0;   /* payload is the raw image */
    private static final int MODE_DIFF = 1;   /* payload is raw XOR reference */

    /** Nothing this holds is anywhere near this; a sanity bound on a read. */
    private static final long MAX_FILE = 32L * 1024 * 1024;

    private final File directory;
    /** The reference image, decompressed and held once so save and load do not re-read it. */
    private byte[] cachedReference;

    public SaveStateStore(File directory) {
        this.directory = directory;
    }

    /** The fixed file the quick slot uses, so quick-load always finds the last quick-save. */
    public File quickFile() { return new File(directory, "quick" + EXTENSION); }

    /** Every saved state, newest first. The reference template is not one of these. */
    public List<File> saves() {
        File[] found = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (found == null) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        Collections.addAll(files, found);
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Compress a machine image to `target` as a diff against the reference, never overwriting in place. */
    public void write(File target, byte[] rawState) throws IOException {
        if (rawState == null || rawState.length < 16) throw new IOException("Empty save state");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not make the save-state folder");

        byte[] reference = ensureReference(rawState);
        int mode;
        byte[] payload;
        long refCrc = 0;
        if (reference != null && reference.length == rawState.length) {
            mode = MODE_DIFF;
            payload = xor(rawState, reference);
            refCrc = crc(reference);
        } else {
            // No usable reference (a different image size, e.g. an app update
            // that changed the layout): store this one whole.
            mode = MODE_FULL;
            payload = rawState;
        }

        File partial = new File(target.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(MAGIC2);
            out.write(mode);
            if (mode == MODE_DIFF) {
                writeInt(out, rawState.length);
                writeInt(out, (int) refCrc);
            }
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(payload);
            }
            // Best-effort durability; some filesystems refuse sync, which is
            // not a failure to write.
            try { out.getFD().sync(); } catch (IOException ignored) { }
        }
        // Named only once whole, so a listing never shows a half-written state.
        if (!partial.renameTo(target)) {
            partial.delete();
            throw new IOException("Could not finish writing " + target.getName());
        }
    }

    /** A new named save gets its own file; existing names are never clobbered. */
    public File write(String label, byte[] rawState) throws IOException {
        String base = safe(label);
        File target = new File(directory, base + EXTENSION);
        for (int n = 2; target.exists(); n++) target = new File(directory, base + " " + n + EXTENSION);
        write(target, rawState);
        return target;
    }

    /** Read a save file back to its raw machine image. */
    public byte[] read(File file) throws IOException {
        long length = file.length();
        if (length <= MAGIC2.length || length > MAX_FILE) throw new IOException("Not a usable save state");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[MAGIC2.length];
            readFully(in, header);
            if (matches(header, MAGIC1)) {
                // A whole image from an earlier version; just inflate it.
                return inflate(in, MAX_FILE);
            }
            if (!matches(header, MAGIC2)) throw new IOException("Not a PoolRad save state");
            int mode = readByte(in);
            if (mode == MODE_FULL) {
                return inflate(in, MAX_FILE);
            }
            if (mode == MODE_DIFF) {
                int rawLen = readInt(in);
                int refCrc = readInt(in);
                if (rawLen <= 0 || rawLen > MAX_FILE) throw new IOException("Save state is implausibly large");
                byte[] reference = loadReference();
                if (reference == null) throw new IOException("The reference save state is missing");
                if (reference.length != rawLen || (int) crc(reference) != refCrc)
                    throw new IOException("This save state does not match the reference");
                byte[] diff = inflate(in, MAX_FILE);
                if (diff.length != rawLen) throw new IOException("Save state ended early");
                return xor(diff, reference);
            }
            throw new IOException("Unknown save-state format");
        }
    }

    /* --- pairing a save with its notebook (F93) --- */

    /** The sidecar that records which notebook a save belongs with, referenced not copied. */
    private static File bindingFile(File save) { return new File(save.getPath() + ".notebook"); }

    /**
     * Record the notebook a save belongs with, alongside the save. Referenced by
     * id, not copied: the notes and journal stay in the notebook store. A null
     * or blank id clears any existing pairing.
     */
    public void writeBinding(File save, String notebookId) {
        File sidecar = bindingFile(save);
        if (notebookId == null || notebookId.trim().isEmpty()) { sidecar.delete(); return; }
        File partial = new File(sidecar.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(notebookId.trim().getBytes("UTF-8"));
        } catch (IOException failure) {
            partial.delete();
            return;   // a lost pairing is not worth failing the save over
        }
        if (!partial.renameTo(sidecar)) partial.delete();
    }

    /** The notebook id paired with a save, or null if none was recorded. */
    public String readBinding(File save) {
        File sidecar = bindingFile(save);
        if (!sidecar.isFile() || sidecar.length() == 0 || sidecar.length() > 4096) return null;
        try (FileInputStream in = new FileInputStream(sidecar)) {
            byte[] all = new byte[(int) sidecar.length()];
            readFully(in, all);
            String id = new String(all, "UTF-8").trim();
            return id.isEmpty() ? null : id;
        } catch (IOException failure) {
            return null;
        }
    }

    /** Remove a save and any sidecar that referenced its notebook. */
    public boolean delete(File save) {
        bindingFile(save).delete();
        return save.delete();
    }

    /** A readable label for a save file, without its extension. */
    public static String label(File file) {
        String name = file.getName();
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }

    /* --- the reference template --- */

    private File referenceFile() { return new File(directory, REFERENCE_NAME); }

    /**
     * Return the reference image, writing this one as the reference if none
     * exists yet. Returns null only if the reference cannot be established.
     */
    private byte[] ensureReference(byte[] rawState) throws IOException {
        byte[] existing = loadReference();
        if (existing != null) return existing;
        writeReference(rawState);
        cachedReference = rawState;
        return cachedReference;
    }

    /** The reference image, decompressed, or null if there is no reference yet. */
    private byte[] loadReference() throws IOException {
        if (cachedReference != null) return cachedReference;
        File ref = referenceFile();
        if (!ref.isFile()) return null;
        try (FileInputStream in = new FileInputStream(ref)) {
            byte[] header = new byte[REFERENCE_MAGIC.length];
            readFully(in, header);
            if (!matches(header, REFERENCE_MAGIC)) throw new IOException("The reference save state is corrupt");
            int rawLen = readInt(in);
            int storedCrc = readInt(in);
            if (rawLen <= 0 || rawLen > MAX_FILE) throw new IOException("The reference save state is corrupt");
            byte[] raw = inflate(in, MAX_FILE);
            if (raw.length != rawLen || (int) crc(raw) != storedCrc)
                throw new IOException("The reference save state is corrupt");
            cachedReference = raw;
            return cachedReference;
        }
    }

    private void writeReference(byte[] rawState) throws IOException {
        File ref = referenceFile();
        File partial = new File(ref.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(REFERENCE_MAGIC);
            writeInt(out, rawState.length);
            writeInt(out, (int) crc(rawState));
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(rawState);
            }
            try { out.getFD().sync(); } catch (IOException ignored) { }
        }
        if (!partial.renameTo(ref)) {
            partial.delete();
            throw new IOException("Could not write the reference save state");
        }
    }

    /* --- helpers --- */

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static long crc(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data);
        return c.getValue();
    }

    private static byte[] inflate(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8 * 1024 * 1024);
        try (GZIPInputStream gz = new GZIPInputStream(in)) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = gz.read(chunk)) >= 0) {
                raw.write(chunk, 0, read);
                if (raw.size() > limit) throw new IOException("Save state is implausibly large");
            }
        }
        return raw.toByteArray();
    }

    private static void writeInt(FileOutputStream out, int v) throws IOException {
        out.write((v >> 24) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static int readInt(InputStream in) throws IOException {
        int a = readByte(in), b = readByte(in), c = readByte(in), d = readByte(in);
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    private static int readByte(InputStream in) throws IOException {
        int v = in.read();
        if (v < 0) throw new IOException("Save state ended early");
        return v;
    }

    private static boolean matches(byte[] header, byte[] magic) {
        if (header.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) if (header[i] != magic[i]) return false;
        return true;
    }

    private static void readFully(InputStream in, byte[] into) throws IOException {
        int at = 0;
        while (at < into.length) {
            int read = in.read(into, at, into.length - at);
            if (read < 0) throw new IOException("Save state ended early");
            at += read;
        }
    }

    /** Anything a filesystem might object to becomes a space. */
    private static String safe(String name) {
        if (name == null) return "Save state";
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(c >= ' ' && c < 127 && "/\\:*?\"<>|".indexOf(c) < 0 ? c : ' ');
        }
        String trimmed = out.toString().trim();
        return trimmed.isEmpty() ? "Save state" : trimmed;
    }
}
