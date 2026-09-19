package name.osher.gil.minivmac;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Short-lived, app-private notebook export files. Reuses ScreenshotFiles' small
 * create/restore/copy/expiry pattern, with PRNA framing and tighter file checks.
 * Header sniffing is not full archive validation; NotebookStore owns that step.
 */
final class NotebookTransferFiles {
    static final long RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    static final long MAX_BYTES = 72L * 1024 * 1024;
    private static final String PREFIX = "PoolRad-notes-";
    private static final String NAME = "PoolRad-notes-[A-Za-z0-9-]+\\.(prnb|png|pdf|prcb)";
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] ARCHIVE_HEADER = {'P', 'R', 'N', 'A', 0, 0, 0, 1};
    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_HEADER = {'P', 'K', 3, 4};   // whole-companion backup (.prcb)

    private NotebookTransferFiles() {}

    /** Creates an empty unique file. It is restorable only once fully written. */
    static File create(File directory, String extension) throws IOException {
        if (!"prnb".equals(extension) && !"png".equals(extension)
                && !"pdf".equals(extension) && !"prcb".equals(extension))
            throw new IOException("Unsupported notebook export extension");
        if (directory == null || symlink(directory)) throw new IOException("Unsafe notebook export cache");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create notebook export cache");
        if (symlink(directory)) throw new IOException("Unsafe notebook export cache");
        String date = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return File.createTempFile(PREFIX + date + "-", "." + extension, directory);
    }

    /** Restores only a confined regular file with a matching PNG or PRNA-v1 header. */
    static File restore(File directory, String name) throws IOException {
        if (directory == null || name == null || !name.matches(NAME)
                || symlink(directory) || !directory.isDirectory()) return null;
        File candidate = new File(directory, name);
        if (symlink(candidate) || !candidate.isFile()
                || !candidate.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())) return null;
        long size = candidate.length();
        if (size <= 8 || size > MAX_BYTES) return null;
        byte[] header = name.endsWith(".png") ? PNG_HEADER
                : name.endsWith(".pdf") ? PDF_HEADER
                : name.endsWith(".prcb") ? ZIP_HEADER : ARCHIVE_HEADER;
        try (FileInputStream in = new FileInputStream(candidate)) {
            for (byte expected : header) if (in.read() != (expected & 255)) return null;
            if (in.read() < 0) return null;
        }
        return candidate;
    }

    /** Copies a completed private export; does not close the caller's destination. */
    static void copy(File source, OutputStream destination) throws IOException {
        if (destination == null) throw new IOException("Save destination is unavailable");
        if (source == null || restore(source.getParentFile(), source.getName()) == null)
            throw new IOException("Notebook export source is missing or invalid");
        long expectedSize = source.length();
        if (expectedSize <= 8 || expectedSize > MAX_BYTES) throw new IOException("Notebook export size is invalid");
        long copied = 0;
        try (FileInputStream in = new FileInputStream(source)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) {
                copied += count;
                if (copied > expectedSize || copied > MAX_BYTES) throw new IOException("Notebook export changed during copying");
                destination.write(buffer, 0, count);
            }
        }
        if (copied != expectedSize || source.length() != expectedSize)
            throw new IOException("Notebook export changed during copying");
        destination.flush();
    }

    /** Best-effort cleanup of owned cache files only; never descends into folders. */
    static void discardExpired(File directory, File protectedFile, long now) {
        try {
            if (directory == null || symlink(directory) || !directory.isDirectory()) return;
            File protectedPath = protectedFile == null ? null : protectedFile.getCanonicalFile();
            File[] files = directory.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (!file.getName().matches(NAME) || symlink(file) || !file.isFile()
                        || file.getCanonicalFile().equals(protectedPath)) continue;
                long modified = file.lastModified();
                if (modified > 0 && now > modified && now - modified > RETENTION_MILLIS) file.delete();
            }
        } catch (IOException | SecurityException ignored) {
            // Cache cleanup must never prevent saving a notebook or export.
        }
    }

    /** API-21-compatible leaf-symlink check; legitimate Android parent aliases are allowed. */
    private static boolean symlink(File file) throws IOException {
        File absolute = file.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent == null) return false;
        File expected = new File(parent.getCanonicalFile(), absolute.getName());
        return !absolute.getCanonicalFile().equals(expected);
    }
}
