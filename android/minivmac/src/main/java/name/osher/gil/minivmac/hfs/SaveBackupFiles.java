package name.osher.gil.minivmac.hfs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where backed-up saved games live, and how a guest disk image is opened.
 *
 * Backups sit in the app's own storage beside the disks and notebooks, one file
 * per saved game, named for the game's own file and the moment it was taken.
 * Nothing is ever overwritten: a second backup of the same save is a second
 * file, because the whole point of the first one is that it is still there.
 */
public final class SaveBackupFiles {
    public static final String EXTENSION = ".prsv";
    /** Where the game keeps its own saved games on the guest disk. */
    public static final String SAVE_FOLDER = "Pool Of Radiance:PoolRadSave";

    private final File directory;

    public SaveBackupFiles(File directory) {
        this.directory = directory;
    }

    public File directory() { return directory; }

    /** Newest first, because the newest is the one anybody is looking for. */
    public List<File> backups() {
        File[] found = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (found == null) return Collections.emptyList();
        List<File> files = new ArrayList<>(Arrays.asList(found));
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /**
     * Write one backup. The name carries the save's own name and a stamp, so a
     * directory listing is readable without opening anything.
     */
    public File write(SaveArchive archive, String stamp) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not make the backup folder");
        File target = new File(directory, safe(archive.name) + " " + stamp + EXTENSION);
        for (int attempt = 2; target.exists(); attempt++)
            target = new File(directory, safe(archive.name) + " " + stamp + " (" + attempt + ")" + EXTENSION);
        File partial = new File(target.getPath() + ".part");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(partial)) {
            out.write(archive.pack());
            out.getFD().sync();
        }
        // Named only once it is whole, so a listing never shows a half backup.
        if (!partial.renameTo(target)) {
            partial.delete();
            throw new IOException("Could not finish writing " + target.getName());
        }
        return target;
    }

    public SaveArchive read(File backup) throws IOException {
        long length = backup.length();
        if (length <= 0 || length > 16L << 20) throw new IOException("Not a usable backup file");
        byte[] packed = new byte[(int) length];
        try (java.io.FileInputStream in = new java.io.FileInputStream(backup)) {
            int at = 0;
            while (at < packed.length) {
                int read = in.read(packed, at, packed.length - at);
                if (read < 0) throw new IOException("Backup file ended early");
                at += read;
            }
        }
        return SaveArchive.unpack(packed);
    }

    /** Anything a filesystem might object to becomes a space. */
    private static String safe(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char letter = name.charAt(i);
            out.append(letter >= ' ' && letter < 127 && "/\\:*?\"<>|".indexOf(letter) < 0 ? letter : ' ');
        }
        return out.toString().trim().isEmpty() ? "Saved game" : out.toString().trim();
    }

    /** Open a disk image, for reading only unless asked otherwise. */
    public static Blocks open(File image, boolean writable) throws IOException {
        final RandomAccessFile file = new RandomAccessFile(image, writable ? "rw" : "r");
        Blocks blocks = new Blocks() {
            @Override public long size() throws IOException { return file.length(); }
            @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
                file.seek(offset); file.readFully(into, at, length);
            }
            @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
                file.seek(offset); file.write(from, at, length);
            }
            @Override public void close() throws IOException { file.close(); }
        };
        return writable ? blocks : new Blocks.ReadOnly(blocks);
    }
}
