package name.osher.gil.minivmac.mapper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-requested reference cache. The reference's artwork is NOT distributed in the APK. */
public final class RuneArtwork {
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean();
    private final File directory;
    public RuneArtwork(File files) { directory = new File(files, "codewheel"); }
    private String name(boolean espruar, int id) {
        if (id < 1 || id > 36) throw new IllegalArgumentException("Rune ID outside 1–36");
        return String.format(Locale.ROOT, "%s%02d.gif", espruar ? "esp" : "det", id);
    }
    public Bitmap read(boolean espruar, int id) {
        File file = new File(directory, name(espruar, id));
        if (!file.isFile() || file.length() > 32768) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth < 1 || bounds.outWidth > 256 || bounds.outHeight < 1 || bounds.outHeight > 256) return null;
        return BitmapFactory.decodeFile(file.getPath());
    }
    public boolean complete() {
        for (boolean e : new boolean[]{true, false}) for (int id = 1; id <= 36; id++)
            if (read(e, id) == null) return false;
        return true;
    }
    public void download() throws IOException {
        if (!DOWNLOADING.compareAndSet(false, true)) throw new IOException("Rune download already running. Reopen the lookup shortly.");
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private rune cache");
            for (boolean e : new boolean[]{true, false}) for (int id = 1; id <= 36; id++) {
                if (read(e, id) != null) continue;
                String name = name(e, id);
                HttpURLConnection connection = (HttpURLConnection) new URL("https://dkennedy.io/por-code-wheel/img/" + name).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                try {
                    if (connection.getResponseCode() != 200) throw new IOException("Reference returned HTTP " + connection.getResponseCode());
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream in = connection.getInputStream()) {
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (bytes.size() + count > 32768) throw new IOException("Rune image exceeds size limit");
                            bytes.write(buffer, 0, count);
                        }
                    }
                    byte[] data = bytes.toByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                    if (bounds.outWidth < 1 || bounds.outWidth > 256 || bounds.outHeight < 1 || bounds.outHeight > 256)
                        throw new IOException("Invalid rune illustration");
                    File temp = new File(directory, name + ".part");
                    try {
                        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(data); }
                        if (!temp.renameTo(new File(directory, name))) throw new IOException("Cannot store rune illustration");
                    } finally { if (temp.exists()) temp.delete(); }
                } finally { connection.disconnect(); }
            }
        } finally { DOWNLOADING.set(false); }
    }
}
