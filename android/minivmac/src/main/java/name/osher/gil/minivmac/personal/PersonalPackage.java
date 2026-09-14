package name.osher.gil.minivmac.personal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Personal-use first installation only. An APK update never replaces mutable user media. */
public final class PersonalPackage {
    public static final int ROM_BYTES = 262144;
    public static final int MAX_MANIFEST_BYTES = 8192;
    public static final long MAX_DISK_BYTES = 128L * 1024 * 1024;
    public static final long MAX_TOTAL_DISK_BYTES = 256L * 1024 * 1024;
    private static final String DIRECTORY = "personal-package";
    private static final int RECEIPT_MAGIC = 0x50525049; // PRPI

    private PersonalPackage() { }

    public interface Source { InputStream open(String relative) throws IOException; }
    public enum Status { INSTALLED, ALREADY_INSTALLED, EXISTING_USER_FILES }

    public static final class Result {
        private final Status status;
        private final File dataRoot;
        private final long romChecksum;
        private Result(Status status, File dataRoot, long romChecksum) {
            this.status = status; this.dataRoot = dataRoot; this.romChecksum = romChecksum;
        }
        public Status status() { return status; }
        public File dataRoot() { return dataRoot; }
        /** Original verified unsigned checksum; -1 when existing legacy media prevented setup. */
        public long romChecksum() { return romChecksum; }
    }

    public static synchronized Result install(File filesRoot, Source source) throws IOException {
        requireRoot(filesRoot);
        File destination = new File(filesRoot, DIRECTORY);
        if (entryExists(filesRoot, DIRECTORY)) {
            return new Result(Status.ALREADY_INSTALLED, destination, receipt(filesRoot, destination));
        }
        if (legacyOccupied(filesRoot)) return new Result(Status.EXISTING_USER_FILES, filesRoot, -1);
        byte[] manifestBytes;
        try (InputStream in = open(source, "bundle.properties")) { manifestBytes = boundedManifest(in); }
        Manifest manifest = parse(manifestBytes);
        File staging = new File(filesRoot, ".personal-stage-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("Cannot create personal package staging folder");
        try {
            File rom = new File(staging, "rom"), disks = new File(staging, "disks");
            if (!rom.mkdir() || !disks.mkdir()) throw new IOException("Cannot create personal media folders");
            File romFile = new File(rom, manifest.rom.file);
            copy(source, manifest.rom, romFile);
            long romChecksum = validateRom(romFile);
            for (Payload disk : manifest.disks) copy(source, disk, new File(disks, disk.file));
            writeSynced(new File(staging, "bundle.properties"), manifestBytes);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(RECEIPT_MAGIC); out.writeInt(1); out.writeLong(romChecksum);
            out.write(digest().digest(manifestBytes));
            writeSynced(new File(staging, "install.receipt"), bytes.toByteArray());
            // Normal imports may have arrived while a slow asset source was being copied.
            if (legacyOccupied(filesRoot)) return new Result(Status.EXISTING_USER_FILES, filesRoot, -1);
            if (entryExists(filesRoot, DIRECTORY) || !staging.renameTo(destination)) {
                throw new IOException("Cannot publish personal package atomically; existing files were not replaced");
            }
            return new Result(Status.INSTALLED, destination, romChecksum);
        } finally {
            cleanOwnStage(filesRoot, staging);
        }
    }

    /**
     * Public APK updates use the installed data root too. An invalid existing
     * package is an explicit recovery error, never a silent switch to another save.
     * Removed/changed ROMs and disks are intentional user state, not missing assets.
     * Do not take the installer monitor: Activity recreation calls this on the UI
     * thread while another Activity may still be copying large assets. The one
     * final directory rename makes either the legacy root or the complete receipt
     * visible; private staging is never selected.
     */
    public static File dataRoot(File filesRoot) throws IOException {
        requireRoot(filesRoot);
        File installed = new File(filesRoot, DIRECTORY);
        if (!entryExists(filesRoot, DIRECTORY)) return filesRoot;
        receipt(filesRoot, installed);
        return installed;
    }

    private static long receipt(File filesRoot, File installed) throws IOException {
        requireDirectory(filesRoot, installed);
        requireDirectory(installed, new File(installed, "rom"));
        requireDirectory(installed, new File(installed, "disks"));
        File manifest = new File(installed, "bundle.properties"), receipt = new File(installed, "install.receipt");
        requireFile(installed, manifest); requireFile(installed, receipt);
        if (receipt.length() != 48) throw new IOException("Personal package installation receipt is invalid");
        byte[] raw;
        try (InputStream in = new FileInputStream(manifest)) { raw = boundedManifest(in); }
        parse(raw); // Future or damaged formats must never trigger a reinstall.
        try (DataInputStream in = new DataInputStream(new FileInputStream(receipt))) {
            if (in.readInt() != RECEIPT_MAGIC || in.readInt() != 1) throw new IOException("Unsupported personal installation receipt");
            long checksum = in.readLong();
            byte[] expected = new byte[32]; in.readFully(expected);
            if (!compatibleChecksum(checksum) || !MessageDigest.isEqual(expected, digest().digest(raw)) || in.read() != -1) {
                throw new IOException("Personal package installation receipt does not match its manifest");
            }
            return checksum;
        }
    }

    private static final class Payload {
        final String file, sha256;
        final long bytes;
        Payload(String file, long bytes, String sha256) { this.file = file; this.bytes = bytes; this.sha256 = sha256; }
    }
    private static final class Manifest {
        final Payload rom;
        final List<Payload> disks;
        Manifest(Payload rom, List<Payload> disks) { this.rom = rom; this.disks = disks; }
    }

    private static Manifest parse(byte[] raw) throws IOException {
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate manifest key");
                return super.put(key, value);
            }
        };
        try { values.load(new ByteArrayInputStream(raw)); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed personal package manifest", malformed); }
        if (!"1".equals(values.getProperty("format")) || !"macII".equals(values.getProperty("machine"))
                || !"MacII.ROM".equals(values.getProperty("rom.file"))
                || !Integer.toString(ROM_BYTES).equals(values.getProperty("rom.bytes"))) {
            throw new IOException("Personal package must contain a supported Macintosh II ROM");
        }
        String countValue = values.getProperty("disk.count", "");
        if (!countValue.matches("[1-8]")) throw new IOException("Personal package needs one to eight disks");
        int count = Integer.parseInt(countValue);
        if (values.size() != 6 + count * 3) throw new IOException("Unknown or missing personal package manifest keys");
        Payload rom = new Payload("MacII.ROM", ROM_BYTES, sha(values, "rom.sha256"));
        List<Payload> disks = new ArrayList<>();
        long total = 0;
        for (int n = 1; n <= count; n++) {
            String prefix = "disk." + n, filename = "disk" + n + ".dsk";
            if (!filename.equals(values.getProperty(prefix + ".file"))) throw new IOException("Invalid personal disk filename");
            String byteValue = values.getProperty(prefix + ".bytes", "");
            if (!byteValue.matches("[1-9][0-9]{0,9}")) throw new IOException("Invalid personal disk length");
            long length = Long.parseLong(byteValue);
            if (length > MAX_DISK_BYTES || length % 512 != 0 || (total += length) > MAX_TOTAL_DISK_BYTES) {
                throw new IOException("Personal disks must be 512-byte aligned, at most 128 MiB each and 256 MiB combined");
            }
            disks.add(new Payload(filename, length, sha(values, prefix + ".sha256")));
        }
        return new Manifest(rom, disks);
    }

    private static String sha(Properties values, String key) throws IOException {
        String value = values.getProperty(key, "");
        if (!value.matches("[0-9a-fA-F]{64}")) throw new IOException("Invalid SHA-256 in personal manifest");
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static byte[] boundedManifest(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int count; (count = readSome(in, buffer, buffer.length)) != -1;) {
            if (out.size() + count > MAX_MANIFEST_BYTES) throw new IOException("Personal package manifest exceeds 8192 bytes");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static InputStream open(Source source, String path) throws IOException {
        if (source == null) throw new IOException("Personal package assets are unavailable");
        InputStream in = source.open(path);
        if (in == null) throw new IOException("Missing personal package asset: " + path);
        return in;
    }

    private static void copy(Source source, Payload payload, File target) throws IOException {
        MessageDigest checksum = digest();
        try (InputStream in = open(source, payload.file); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long remaining = payload.bytes;
            while (remaining > 0) {
                int count = readSome(in, buffer, (int) Math.min(buffer.length, remaining));
                if (count == -1) throw new IOException("Truncated personal package asset: " + payload.file);
                checksum.update(buffer, 0, count); out.write(buffer, 0, count); remaining -= count;
            }
            if (in.read() != -1) throw new IOException("Oversized personal package asset: " + payload.file);
            if (!hex(checksum.digest()).equals(payload.sha256)) throw new IOException("SHA-256 mismatch for personal asset: " + payload.file);
            out.getFD().sync();
        }
    }

    private static int readSome(InputStream in, byte[] buffer, int length) throws IOException {
        int count = in.read(buffer, 0, length);
        if (count != 0) return count;
        int single = in.read();
        if (single == -1) return -1;
        buffer[0] = (byte) single; return 1;
    }

    /** Same BE16 word addition as RomManager, checked through the complete declared ROM. */
    private static long validateRom(File rom) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(rom)))) {
            long expected = in.readInt() & 0xffffffffL, calculated = 0;
            if (!compatibleChecksum(expected)) throw new IOException("Unsupported Macintosh II ROM signature");
            for (int offset = 4; offset < ROM_BYTES; offset += 2) calculated += in.readUnsignedShort();
            if (calculated != expected || in.read() != -1) throw new IOException("Macintosh II ROM word checksum failed");
            return expected;
        }
    }

    private static boolean compatibleChecksum(long checksum) { return checksum == 0x97851db6L || checksum == 0x9779d2c4L; }
    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IOException("SHA-256 is unavailable", unavailable); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(64);
        for (byte b : bytes) { value.append(Character.forDigit((b >>> 4) & 15, 16)); value.append(Character.forDigit(b & 15, 16)); }
        return value.toString();
    }
    private static void writeSynced(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
    }

    private static void requireRoot(File root) throws IOException {
        if (root == null) throw new IllegalArgumentException("Missing application files folder");
        if (!root.isDirectory()) throw new IOException("Application files folder is unavailable");
    }
    private static File[] children(File folder) throws IOException {
        File[] children = folder.listFiles();
        if (children == null) throw new IOException("Cannot inspect existing application files");
        return children;
    }
    /** Directory listings also detect dangling symlinks, for which File.exists() is false. */
    private static boolean entryExists(File parent, String name) throws IOException {
        for (File entry : children(parent)) if (entry.getName().equals(name)) return true;
        return false;
    }
    private static boolean directChild(File parent, File child) throws IOException {
        return child.getCanonicalFile().equals(new File(parent.getCanonicalFile(), child.getName()));
    }
    private static void requireDirectory(File parent, File child) throws IOException {
        if (!directChild(parent, child) || !child.isDirectory()) throw new IOException("Personal package folder layout is invalid or linked");
    }
    private static void requireFile(File parent, File child) throws IOException {
        if (!directChild(parent, child) || !child.isFile()) throw new IOException("Personal package receipt is missing or linked");
    }
    private static boolean legacyOccupied(File root) throws IOException {
        for (String name : new String[]{"rom", "disks"}) {
            if (!entryExists(root, name)) continue;
            File folder = new File(root, name);
            if (!directChild(root, folder) || !folder.isDirectory() || children(folder).length != 0) return true;
        }
        return false;
    }

    /** Fixed filenames in our own UUID stage only. Unknown/linked entries are left untouched. */
    private static void cleanOwnStage(File filesRoot, File staging) {
        try {
            if (!entryExists(filesRoot, staging.getName()) || !directChild(filesRoot, staging) || !staging.isDirectory()) return;
            for (String name : new String[]{"rom", "disks"}) {
                File folder = new File(staging, name);
                if (!directChild(staging, folder) || !folder.isDirectory()) continue;
                for (File child : children(folder)) {
                    boolean known = name.equals("rom") ? child.getName().equals("MacII.ROM") : child.getName().matches("disk[1-8]\\.dsk");
                    if (known && directChild(folder, child) && child.isFile()) child.delete();
                }
                folder.delete();
            }
            for (String name : new String[]{"bundle.properties", "install.receipt"}) {
                File child = new File(staging, name);
                if (directChild(staging, child) && child.isFile()) child.delete();
            }
            staging.delete();
        } catch (IOException | SecurityException ignored) {
            // Incomplete private staging can remain, but it is never selected or published.
        }
    }
}
