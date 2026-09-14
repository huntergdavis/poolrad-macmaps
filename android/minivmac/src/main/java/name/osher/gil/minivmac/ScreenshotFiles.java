package name.osher.gil.minivmac;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Private, short-lived screenshot files; no external storage access. */
final class ScreenshotFiles {
    static final long RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private ScreenshotFiles() {}

    static File create(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create screenshot cache");
        }
        String date = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return File.createTempFile("PoolRad-" + date + "-", ".png", directory);
    }

    static File restore(File directory, String name) throws IOException {
        if (name == null || !name.matches("PoolRad-[A-Za-z0-9-]+\\.png")) return null;
        File candidate = new File(directory, name);
        if (!candidate.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())
                || !candidate.isFile()) return null;
        try (FileInputStream in = new FileInputStream(candidate)) {
            for (byte expected : PNG_HEADER) {
                if (in.read() != (expected & 255)) return null;
            }
            if (in.read() < 0) return null;
        }
        return candidate;
    }

    static void copy(File source, OutputStream destination) throws IOException {
        if (destination == null) throw new IOException("Save destination is unavailable");
        try (FileInputStream in = new FileInputStream(source)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) destination.write(buffer, 0, count);
        }
        destination.flush();
    }

    static void discardExpired(File directory, File protectedFile, long now) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.equals(protectedFile) || !file.isFile()
                    || !file.getName().matches("PoolRad-[A-Za-z0-9-]+\\.png")) continue;
            if (file.lastModified() > 0 && now - file.lastModified() > RETENTION_MILLIS) {
                // Cache cleanup is best effort. Shared captures remain available for seven days.
                file.delete();
            }
        }
    }
}
