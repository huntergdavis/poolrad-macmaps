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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Where whole-machine save states live on disk, and how they are packed.
 *
 * A raw save state is the ~8 MB machine image the emulator hands over. It
 * compresses well -- most of RAM is unchanged OS and game code -- so each file
 * is a gzip of the raw bytes, which brings a save down to a couple of megabytes
 * without needing the baked reference yet (that is F91, a further shrink to
 * tens of KB). A magic word guards against reading something that is not one of
 * ours.
 *
 * Nothing here touches a guest disk or the game's own saves; these are the
 * companion's own files in its private storage.
 */
public final class SaveStateStore {
    public static final String EXTENSION = ".prqs";  /* PoolRad quick state */
    public static final String QUICK_NAME = "Quick save";
    /** "PRQS1\n" -- format tag, so a truncated or foreign file is refused. */
    private static final byte[] MAGIC = {'P', 'R', 'Q', 'S', '1', '\n'};
    /** Nothing this holds is anywhere near this; a sanity bound on a read. */
    private static final long MAX_FILE = 32L * 1024 * 1024;

    private final File directory;

    public SaveStateStore(File directory) {
        this.directory = directory;
    }

    /** The fixed file the quick slot uses, so quick-load always finds the last quick-save. */
    public File quickFile() { return new File(directory, "quick" + EXTENSION); }

    /** Every saved state, newest first. */
    public List<File> saves() {
        File[] found = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (found == null) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        Collections.addAll(files, found);
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Compress a raw machine image to `target`, never overwriting a good file in place. */
    public void write(File target, byte[] rawState) throws IOException {
        if (rawState == null || rawState.length < 16) throw new IOException("Empty save state");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not make the save-state folder");
        File partial = new File(target.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(MAGIC);
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(rawState);
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
        if (length <= MAGIC.length || length > MAX_FILE) throw new IOException("Not a usable save state");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[MAGIC.length];
            readFully(in, header);
            for (int i = 0; i < MAGIC.length; i++)
                if (header[i] != MAGIC[i]) throw new IOException("Not a PoolRad save state");
            ByteArrayOutputStream raw = new ByteArrayOutputStream(8 * 1024 * 1024);
            try (GZIPInputStream gz = new GZIPInputStream(in)) {
                byte[] chunk = new byte[64 * 1024];
                int read;
                while ((read = gz.read(chunk)) >= 0) {
                    raw.write(chunk, 0, read);
                    if (raw.size() > MAX_FILE) throw new IOException("Save state is implausibly large");
                }
            }
            return raw.toByteArray();
        }
    }

    /** A readable label for a save file, without its extension. */
    public static String label(File file) {
        String name = file.getName();
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
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
