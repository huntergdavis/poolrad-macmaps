package name.osher.gil.minivmac.notebook;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * One backup file for the whole companion (F63): every notebook — which already
 * carries its own areas, trails and notes — plus the fog-of-war switches, in a
 * single ZIP. Broader than the per-notebook export (F53 is for reading; this is
 * for restore). Restoring keeps each notebook's original id, so save states that
 * were paired with a notebook (F93) find it again; a notebook that already
 * exists is left untouched rather than duplicated or overwritten.
 */
public final class CompanionBackup {
    private static final String NOTEBOOK_DIR = "notebooks/";
    private static final String FOG_ENTRY = "fog.properties";

    private CompanionBackup() {}

    /** What a restore did, so the caller can say so plainly. */
    public static final class Restored {
        public final int notebooks, skipped;
        public final Map<String, Boolean> fog;
        Restored(int notebooks, int skipped, Map<String, Boolean> fog) {
            this.notebooks = notebooks; this.skipped = skipped; this.fog = fog;
        }
    }

    /** Write every notebook and the given fog switches to one ZIP. Does not close `out`. */
    public static void write(OutputStream out, NotebookStore store, Map<String, Boolean> fog) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        for (NotebookStore.Notebook book : store.listNotebooks()) {
            zip.putNextEntry(new ZipEntry(NOTEBOOK_DIR + book.id() + ".prnb"));
            store.exportNotebook(book.id(), shield(zip));
            zip.closeEntry();
        }
        Properties properties = new Properties();
        if (fog != null) for (Map.Entry<String, Boolean> entry : fog.entrySet())
            if (entry.getKey() != null && entry.getValue() != null)
                properties.setProperty(entry.getKey(), entry.getValue() ? "true" : "false");
        zip.putNextEntry(new ZipEntry(FOG_ENTRY));
        properties.store(shield(zip), "PoolRad fog and footprint switches");
        zip.closeEntry();
        zip.finish();
    }

    /** Restore notebooks (skipping any that already exist) and read back the fog switches. Does not close `in`. */
    public static Restored read(InputStream in, NotebookStore store) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        int restored = 0, skipped = 0;
        Map<String, Boolean> fog = new LinkedHashMap<>();
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.startsWith(NOTEBOOK_DIR) && name.endsWith(".prnb")) {
                try { store.importNotebook(shield(zip)); restored++; }
                catch (IOException alreadyThereOrInvalid) { skipped++; }
            } else if (name.equals(FOG_ENTRY)) {
                Properties properties = new Properties();
                properties.load(shield(zip));
                for (String key : properties.stringPropertyNames())
                    fog.put(key, Boolean.parseBoolean(properties.getProperty(key)));
            }
            zip.closeEntry();
        }
        return new Restored(restored, skipped, fog);
    }

    /** A stream wrapper whose close() does nothing, so a helper cannot close the ZIP. */
    private static OutputStream shield(OutputStream out) {
        return new FilterOutputStream(out) {
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
            @Override public void close() { /* keep the ZIP open */ }
        };
    }

    private static InputStream shield(InputStream in) {
        return new FilterInputStream(in) {
            @Override public void close() { /* keep the ZIP open */ }
        };
    }
}
