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
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
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
    public static final int QUICK_KEEP = 10;
    public static final int MAX_PREVIEW_BYTES = 512 * 1024;
    /** Auto-saves are named with this prefix so they can be listed and rotated apart from the player's own. */
    public static final String AUTO_PREFIX = "Auto ";
    /** The reference template every diff is measured against; kept, never listed. */
    private static final String REFERENCE_NAME = "reference.prqref";

    /** v4 requires complete launch-resumable machine state plus disk verification. */
    private static final byte[] MAGIC4 = {'P', 'R', 'Q', 'S', '4', '\n'};
    private static final byte[] MAGIC3 = {'P', 'R', 'Q', 'S', '3', '\n'};
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

    @FunctionalInterface interface Sync { void sync(FileOutputStream out) throws IOException; }
    private final Sync sync;
    public SaveStateStore(File directory) { this(directory, out -> out.getFD().sync()); }
    SaveStateStore(File directory, Sync sync) { this.directory = directory; this.sync = sync; }

    public static final class Snapshot {
        public final byte[] state;
        public final DiskSnapshotGuard.Fingerprint disks;
        private Snapshot(byte[] state, DiskSnapshotGuard.Fingerprint disks) {
            this.state = state; this.disks = disks;
        }
    }

    /** Legacy slot, retained until it ages out of the ten-entry quick history. */
    public File quickFile() { return new File(directory, "quick" + EXTENSION); }

    private File quickDirectory() { return new File(directory, "quick-history"); }

    /** Dedicated namespace: even a named save called "Quick" cannot be rotated. */
    public List<File> quickSaves() {
        List<File> files = new ArrayList<>();
        File[] found = quickDirectory().listFiles((dir, name) -> quickParts(name) != null);
        if (found != null) for (File f : found) if (f.isFile()) files.add(f);
        Collections.sort(files, (a, b) -> Long.compare(quickParts(b.getName())[0], quickParts(a.getName())[0]));
        if (quickFile().isFile()) files.add(quickFile());
        return files;
    }

    /** Publish a new quick save before retiring any previous one. Clock rollback is harmless. */
    public File writeQuick(byte[] rawState, long capturedAt, DiskSnapshotGuard.Fingerprint disks) throws IOException {
        if (rawState == null || rawState.length < 16) throw new IOException("Empty save state");
        File dir = quickDirectory();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not make quick-save history");
        List<File> existing = quickSaves();
        long sequence = 1;
        if (!existing.isEmpty()) {
            long[] parts = quickParts(existing.get(0).getName());
            if (parts != null) {
                if (parts[0] == Long.MAX_VALUE) throw new IOException("Quick-save sequence exhausted");
                sequence = parts[0] + 1;
            }
        }
        File target = new File(dir, String.format(Locale.US, "q%020d_%013d.prqs", sequence, Math.max(0, capturedAt)));
        write(target, rawState, disks);
        target.setLastModified(Math.max(0, capturedAt));
        List<File> quick = quickSaves();
        for (int i = QUICK_KEEP; i < quick.size(); i++) delete(quick.get(i));
        return target;
    }

    private static long[] quickParts(String name) {
        if (!name.matches("q[0-9]{20}_[0-9]{13}\\.prqs")) return null;
        try { return new long[]{Long.parseLong(name.substring(1, 21)), Long.parseLong(name.substring(22, 35))}; }
        catch (NumberFormatException invalid) { return null; }
    }

    public static long capturedAt(File save) {
        long[] parts = quickMetadata(save);
        return parts == null ? save.lastModified() : parts[1];
    }

    private static long[] quickMetadata(File save) {
        return save.getParentFile() != null && save.getParentFile().getName().equals("quick-history")
                ? quickParts(save.getName()) : null;
    }

    /** Local date, seconds and zone distinguish rapid saves and daylight-saving repeats. */
    public static String timestamp(File save) {
        return new SimpleDateFormat("MMM d ''yy · h:mm:ss a z", Locale.US).format(new Date(capturedAt(save)));
    }

    public static String displayLabel(File save) {
        return (quickMetadata(save) != null || save.getName().equals("quick.prqs") ? QUICK_NAME : label(save))
                + "\n" + timestamp(save);
    }

    /** Every saved state, newest first. The reference template is not one of these. */
    public List<File> saves() {
        File[] found = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        List<File> files = new ArrayList<>();
        if (found != null) for (File f : found) if (f.isFile() && !f.equals(quickFile())) files.add(f);
        files.addAll(quickSaves());
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Remember successful publication independently of the device's wall clock. Worker only. */
    public void rememberLatest(File save) throws IOException {
        File actual = save.getCanonicalFile();
        File root = directory.getCanonicalFile();
        File parent = actual.getParentFile();
        if (!actual.isFile() || !actual.getName().endsWith(EXTENSION)
                || !(root.equals(parent) || quickDirectory().getCanonicalFile().equals(parent)))
            throw new IOException("Latest snapshot must be in the save folder");
        String relative = root.equals(parent) ? actual.getName() : "quick-history/" + actual.getName();
        File partial = new File(directory, "latest-snapshot.part");
        File target = new File(directory, "latest-snapshot");
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!partial.renameTo(target)) throw new IOException("Could not remember latest snapshot");
        } finally { partial.delete(); }
    }

    /** First-use fallback uses the existing browser order; never skips a corrupt newest save. */
    public File latestSave() throws IOException {
        File index = new File(directory, "latest-snapshot");
        if (index.isFile()) {
            if (index.length() < 1 || index.length() > 1024)
                throw new IOException("Latest snapshot record is invalid");
            byte[] bytes = new byte[(int)index.length()];
            try (FileInputStream in = new FileInputStream(index)) { readFully(in, bytes); }
            String relative = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            File candidate = new File(directory, relative).getCanonicalFile();
            File root = directory.getCanonicalFile(), parent = candidate.getParentFile();
            if (!candidate.getName().endsWith(EXTENSION)
                    || !(root.equals(parent) || quickDirectory().getCanonicalFile().equals(parent))
                    || !(relative.equals(candidate.getName())
                         || relative.equals("quick-history/" + candidate.getName())))
                throw new IOException("Latest snapshot record points outside the save folder");
            if (candidate.isFile()) return candidate;
        }
        List<File> all = saves();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Compress a machine image to `target` as a diff against the reference, never overwriting in place. */
    public void write(File target, byte[] rawState, DiskSnapshotGuard.Fingerprint disks) throws IOException {
        if (disks == null) throw new IOException("Snapshot has no disk verification");
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
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(MAGIC4);
                disks.writeTo(out);
                out.write(mode);
                if (mode == MODE_DIFF) {
                    writeInt(out, rawState.length);
                    writeInt(out, (int) refCrc);
                }
                writeCompressed(out, payload);
            }
            if (!partial.renameTo(target))
                throw new IOException("Could not finish writing " + target.getName());
        } finally { partial.delete(); }
    }

    /** A new named save gets its own file; existing names are never clobbered. */
    public File write(String label, byte[] rawState, DiskSnapshotGuard.Fingerprint disks) throws IOException {
        String base = safe(label);
        // Legacy quick/auto names are reserved; a manually named save never rotates.
        if (base.equals("quick") || base.startsWith(AUTO_PREFIX)) base = "Named " + base;
        File target = new File(directory, base + EXTENSION);
        for (int n = 2; target.exists(); n++) target = new File(directory, base + " " + n + EXTENSION);
        write(target, rawState, disks);
        return target;
    }

    /**
     * Write an automatic save and rotate the oldest out, keeping the newest
     * `keep`. Auto-saves are their own set (the "Auto " prefix) so this never
     * touches the quick slot or a save the player named.
     */
    public File writeAuto(String label, byte[] rawState, int keep, DiskSnapshotGuard.Fingerprint disks) throws IOException {
        String base = safe(label);
        if (!base.startsWith(AUTO_PREFIX)) base = AUTO_PREFIX + base;
        File target = new File(directory, base + EXTENSION);
        for (int n = 2; target.exists(); n++) target = new File(directory, base + " " + n + EXTENSION);
        write(target, rawState, disks);
        pruneAuto(keep);
        return target;
    }

    /** Every automatic save, newest first. */
    public List<File> autoSaves() {
        List<File> autos = new ArrayList<>();
        for (File f : saves()) if (label(f).startsWith(AUTO_PREFIX)) autos.add(f);
        return autos;   // saves() is already newest-first
    }

    /** Delete automatic saves beyond the newest `keep`, sidecars and all. */
    private void pruneAuto(int keep) {
        if (keep < 0) keep = 0;
        List<File> autos = autoSaves();
        for (int i = keep; i < autos.size(); i++) delete(autos.get(i));
    }

    /** Read a save file back to its raw machine image. */
    public byte[] read(File file) throws IOException { return readSnapshot(file).state; }

    public Snapshot readSnapshot(File file) throws IOException {
        long length = file.length();
        if (length <= MAGIC2.length || length > MAX_FILE) throw new IOException("Not a usable save state");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[MAGIC2.length];
            readFully(in, header);
            if (matches(header, MAGIC1) || matches(header, MAGIC2) || matches(header, MAGIC3))
                throw new IOException("Unsupported older snapshot format: create a new snapshot with this version.");
            if (!matches(header, MAGIC4)) throw new IOException("Not a PoolRad save state");
            DiskSnapshotGuard.Fingerprint disks = DiskSnapshotGuard.Fingerprint.readFrom(in);
            int mode = readByte(in);
            if (mode == MODE_FULL) {
                return new Snapshot(inflate(in, MAX_FILE), disks);
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
                return new Snapshot(xor(diff, reference), disks);
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
    public boolean writeBinding(File save, String notebookId) {
        File sidecar = bindingFile(save);
        if (notebookId == null || notebookId.trim().isEmpty()) return !sidecar.exists() || sidecar.delete();
        File partial = new File(sidecar.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(notebookId.trim().getBytes("UTF-8"));
            out.getFD().sync();
        } catch (IOException failure) {
            partial.delete();
            return false;   // the save itself remains available
        }
        if (!partial.renameTo(sidecar)) { partial.delete(); return false; }
        return true;
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

    public static File previewFile(File save) { return new File(save.getPath() + ".png"); }

    /** Optional small PNG; failure never invalidates a successfully written machine image. */
    public void writePreview(File save, byte[] png) throws IOException {
        if (png == null) return;
        if (png.length < 8 || png.length > MAX_PREVIEW_BYTES
                || png[0] != (byte)137 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G')
            throw new IOException("Invalid save preview");
        File target = previewFile(save), partial = new File(target.getPath() + ".part");
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) { out.write(png); }
            if (!partial.renameTo(target)) throw new IOException("Could not finish save preview");
        } finally { partial.delete(); }
    }

    /** Remove a save and its notebook/preview sidecars, never the shared reference. */
    public boolean delete(File save) {
        if (!save.delete()) return false;
        bindingFile(save).delete();
        previewFile(save).delete();
        return true;
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
        cachedReference = rawState.clone();
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
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(REFERENCE_MAGIC);
                writeInt(out, rawState.length);
                writeInt(out, (int) crc(rawState));
                writeCompressed(out, rawState);
            }
            if (!partial.renameTo(ref))
                throw new IOException("Could not write the reference save state");
        } finally { partial.delete(); }
    }

    /** Finish the trailer, flush, and require fsync while the descriptor is still open. */
    private void writeCompressed(FileOutputStream out, byte[] bytes) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
            gzip.finish();
            gzip.flush();
            sync.sync(out);
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
