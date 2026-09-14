package name.osher.gil.minivmac.desktop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/** Changes only desktop resources in an inactive, imported disk; never restores an old disk image. */
public final class DesktopAppearanceStore {
    private static final long MAX_DISK_BYTES = 128L * 1024 * 1024;
    private static final int BACKUP_MAGIC = 0x50524441; // PRDA
    private static final int MAX_BACKUP_BYTES = 8192;
    private static final int PAT_BYTES = 8, PPAT_BYTES = 182;
    private final File disksRoot, stateRoot;
    private final DiskAccessGate gate;
    private final BeforePublish beforePublish;

    /** A snapshot owns its arrays; modifying one cannot change the saved original setting. */
    public static final class Snapshot {
        public final DesktopDisk.Inspection disk;
        public final byte[] originalPat, originalPpat;

        private Snapshot(DesktopDisk.Inspection disk, Original original) {
            this.disk = disk;
            originalPat = original == null ? null : original.pat.clone();
            originalPpat = original == null ? null : original.ppat.clone();
        }
    }

    public DesktopAppearanceStore(File disksRoot, File stateRoot) {
        this(disksRoot, stateRoot, DiskAccessGate.GLOBAL);
    }

    public DesktopAppearanceStore(File disksRoot, File stateRoot, DiskAccessGate gate) {
        this(disksRoot, stateRoot, gate, (disk, stage) -> { });
    }

    /** Small test seam for interruptions or external source changes before atomic publication. */
    interface BeforePublish { void run(File disk, File stage) throws IOException; }

    DesktopAppearanceStore(File disksRoot, File stateRoot, DiskAccessGate gate, BeforePublish hook) {
        if (disksRoot == null || stateRoot == null || gate == null || hook == null)
            throw new IllegalArgumentException("Desktop appearance storage is incomplete");
        this.disksRoot = disksRoot.getAbsoluteFile();
        this.stateRoot = stateRoot.getAbsoluteFile();
        this.gate = gate;
        this.beforePublish = hook;
    }

    public Snapshot inspect(File disk) throws IOException {
        try (DiskAccessGate.Lease ignored = lease()) {
            File target = target(disk);
            DesktopDisk.Inspection inspection = DesktopDisk.inspect(target);
            return new Snapshot(inspection, readOriginal(target, inspection));
        }
    }

    public Snapshot apply(File disk, DesktopDisk.Style style) throws IOException {
        if (style == null) throw new IOException("Choose a desktop appearance");
        try (DiskAccessGate.Lease ignored = lease()) {
            File target = target(disk);
            DesktopDisk.Inspection inspection = DesktopDisk.inspect(target);
            Original original = readOriginal(target, inspection);
            byte[] pat = DesktopDisk.patternBits(style);
            byte[] ppat = DesktopDisk.colorPattern(inspection.ppat(), pat);
            if (original == null) original = new Original(inspection.pat(), inspection.ppat());
            return replace(target, inspection, original, pat, ppat);
        }
    }

    public Snapshot restore(File disk) throws IOException {
        try (DiskAccessGate.Lease ignored = lease()) {
            File target = target(disk);
            DesktopDisk.Inspection inspection = DesktopDisk.inspect(target);
            Original original = readOriginal(target, inspection);
            if (original == null) throw new IOException("No original desktop setting is saved for this disk");
            return replace(target, inspection, original, original.pat, original.ppat);
        }
    }

    private Snapshot replace(File target, DesktopDisk.Inspection inspection, Original original,
                             byte[] pat, byte[] ppat) throws IOException {
        File staging = new File(disksRoot, ".poolrad-appearance-" + UUID.randomUUID() + ".tmp");
        if (!staging.createNewFile()) throw new IOException("Cannot create a temporary disk copy");
        try {
            Fingerprint source = copy(target, staging);
            inspection.patch(staging, pat, ppat);
            DesktopDisk.Inspection patched = DesktopDisk.inspect(staging);
            if (!inspection.identity().equals(patched.identity()) || inspection.length() != patched.length()
                    || !Arrays.equals(pat, patched.pat()) || !Arrays.equals(ppat, patched.ppat()))
                throw new IOException("The staged disk does not contain the requested desktop setting");
            // Persist only the original resources, never a stale copy of a campaign disk.
            saveOriginal(target, inspection, original);
            beforePublish.run(target, staging);
            target(target);
            if (!source.matches(target))
                throw new IOException("The disk changed while preparing its appearance; nothing was replaced");
            if (!staging.getCanonicalFile().equals(staging.getAbsoluteFile()) || !staging.isFile())
                throw new IOException("The temporary disk copy changed before publication");
            // Android's same-filesystem rename replaces atomically. Never delete the destination first.
            if (!staging.renameTo(target))
                throw new IOException("Cannot replace the disk atomically; the original disk was kept");
            return new Snapshot(DesktopDisk.inspect(target), original);
        } finally {
            // This UUID path is ours; never recurse or follow a redirected directory.
            if (staging.exists() && staging.getCanonicalFile().equals(staging.getAbsoluteFile())
                    && staging.isFile()) staging.delete();
        }
    }

    private DiskAccessGate.Lease lease() throws IOException {
        DiskAccessGate.Lease lease = gate.tryBeginMaintenance();
        if (lease == null) throw new IOException("Shut down the Mac and wait for disk operations before changing the desktop");
        return lease;
    }

    private File target(File disk) throws IOException {
        directory(disksRoot, true);
        directory(stateRoot, false);
        if (within(stateRoot, disksRoot) || within(disksRoot, stateRoot))
            throw new IOException("Desktop setting backups must be separate from imported disks");
        if (disk == null) throw new IOException("Choose an imported disk");
        File target = disk.getAbsoluteFile();
        if (!target.equals(target.getCanonicalFile()) || !disksRoot.equals(target.getParentFile())
                || !target.isFile() || target.length() <= 0 || target.length() > MAX_DISK_BYTES)
            throw new IOException("Desktop appearance only supports a regular imported disk in the current disk folder");
        return target;
    }

    private static void directory(File path, boolean required) throws IOException {
        if (!path.equals(path.getCanonicalFile()) || (path.exists() && !path.isDirectory())
                || (required && !path.isDirectory()))
            throw new IOException("Desktop storage must use real, unredirected directories");
        for (File parent = path.getParentFile(); parent != null; parent = parent.getParentFile()) {
            if (parent.exists() && !parent.isDirectory())
                throw new IOException("Desktop storage has an invalid parent directory");
        }
    }

    private static boolean within(File child, File parent) {
        return child.equals(parent) || child.getPath().startsWith(parent.getPath() + File.separator);
    }

    private static final class Original {
        final byte[] pat, ppat;
        Original(byte[] pat, byte[] ppat) throws IOException {
            if (pat == null || pat.length != PAT_BYTES || ppat == null || ppat.length != PPAT_BYTES)
                throw new IOException("Unsupported original desktop resource size");
            this.pat = pat.clone(); this.ppat = ppat.clone();
        }
    }

    private File backup(File target, DesktopDisk.Inspection inspection) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(target.getPath()); out.writeUTF(inspection.identity());
        }
        return new File(stateRoot, hex(digest().digest(bytes.toByteArray())) + ".prda");
    }

    private Original readOriginal(File target, DesktopDisk.Inspection inspection) throws IOException {
        File file = backup(target, inspection);
        if (!entryExists(stateRoot, file.getName())) return null;
        return readRecord(file, target, inspection);
    }

    private Original readRecord(File file, File target, DesktopDisk.Inspection inspection) throws IOException {
        if (!file.getAbsoluteFile().equals(file.getCanonicalFile()) || !file.isFile()
                || file.length() < 32 || file.length() > MAX_BACKUP_BYTES)
            throw new IOException("Original desktop backup is invalid; it was not overwritten");
        byte[] raw;
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[1024];
            for (int count; (count = input.read(block)) != -1;) {
                if (bytes.size() + count > MAX_BACKUP_BYTES) throw new IOException("Original desktop backup is too large");
                bytes.write(block, 0, count);
            }
            raw = bytes.toByteArray();
        }
        if (raw.length < 32) throw new IOException("Original desktop backup is truncated");
        int payloadLength = raw.length - 32;
        byte[] checksum = digest().digest(Arrays.copyOf(raw, payloadLength));
        if (!MessageDigest.isEqual(checksum, Arrays.copyOfRange(raw, payloadLength, raw.length)))
            throw new IOException("Original desktop backup checksum failed; it was not overwritten");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw, 0, payloadLength))) {
            if (in.readInt() != BACKUP_MAGIC || in.readInt() != 1 || !target.getPath().equals(in.readUTF())
                    || !inspection.identity().equals(in.readUTF()) || in.readInt() != PAT_BYTES)
                throw new IOException("Original desktop backup does not match this disk");
            byte[] pat = new byte[PAT_BYTES]; in.readFully(pat);
            if (in.readInt() != PPAT_BYTES) throw new IOException("Unsupported original desktop backup resource");
            byte[] ppat = new byte[PPAT_BYTES]; in.readFully(ppat);
            if (in.read() != -1) throw new IOException("Original desktop backup has unexpected trailing content");
            return new Original(pat, ppat);
        }
    }

    private void saveOriginal(File target, DesktopDisk.Inspection inspection, Original original) throws IOException {
        if (!stateRoot.exists() && !stateRoot.mkdirs()) throw new IOException("Cannot create the desktop settings backup folder");
        directory(stateRoot, true);
        File file = backup(target, inspection);
        if (entryExists(stateRoot, file.getName())) {
            Original saved = readRecord(file, target, inspection);
            if (!Arrays.equals(saved.pat, original.pat) || !Arrays.equals(saved.ppat, original.ppat))
                throw new IOException("The original desktop backup changed; it was not overwritten");
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(BACKUP_MAGIC); out.writeInt(1); out.writeUTF(target.getPath());
            out.writeUTF(inspection.identity()); out.writeInt(PAT_BYTES); out.write(original.pat);
            out.writeInt(PPAT_BYTES); out.write(original.ppat);
        }
        byte[] body = bytes.toByteArray();
        if (body.length + 32 > MAX_BACKUP_BYTES) throw new IOException("Desktop backup identity is too long");
        File temporary = new File(stateRoot, ".original-" + UUID.randomUUID() + ".tmp");
        if (!temporary.createNewFile()) throw new IOException("Cannot create the original desktop backup");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(body); out.write(digest().digest(body)); out.getFD().sync();
            }
            readRecord(temporary, target, inspection);
            if (entryExists(stateRoot, file.getName()) || !temporary.renameTo(file))
                throw new IOException("Cannot preserve the original desktop setting atomically");
        } finally {
            if (temporary.exists() && temporary.getCanonicalFile().equals(temporary.getAbsoluteFile())
                    && temporary.isFile()) temporary.delete();
        }
    }

    private static boolean entryExists(File directory, String name) throws IOException {
        if (!directory.exists()) return false;
        String[] names = directory.list();
        if (names == null) throw new IOException("Cannot inspect the original desktop backup folder");
        for (String entry : names) if (entry.equals(name)) return true;
        return false;
    }

    private static final class Fingerprint {
        final long length, modified;
        final byte[] sha;
        Fingerprint(long length, long modified, byte[] sha) {
            this.length = length; this.modified = modified; this.sha = sha;
        }
        boolean matches(File file) throws IOException {
            if (file.length() != length || file.lastModified() != modified) return false;
            MessageDigest checksum = digest();
            long read = stream(file, null, checksum, length);
            return read == length && file.length() == length && file.lastModified() == modified
                    && MessageDigest.isEqual(sha, checksum.digest());
        }
    }

    private static Fingerprint copy(File source, File staging) throws IOException {
        long length = source.length(), modified = source.lastModified();
        if (length < 1 || length > MAX_DISK_BYTES) throw new IOException("Unsupported disk copy size");
        MessageDigest checksum = digest();
        long copied;
        try (FileOutputStream out = new FileOutputStream(staging)) {
            copied = stream(source, out, checksum, length); out.getFD().sync();
        }
        if (copied != length || source.length() != length || source.lastModified() != modified)
            throw new IOException("The disk changed while it was being copied");
        return new Fingerprint(length, modified, checksum.digest());
    }

    private static long stream(File file, FileOutputStream out, MessageDigest digest, long limit) throws IOException {
        long length = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] block = new byte[65536];
            for (int count; (count = in.read(block)) != -1;) {
                length += count;
                if (length > limit || length > MAX_DISK_BYTES) throw new IOException("Disk grew during appearance preparation");
                digest.update(block, 0, count);
                if (out != null) out.write(block, 0, count);
            }
        }
        return length;
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new AssertionError(unavailable); }
    }

    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray(), text = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            text[i * 2] = alphabet[(bytes[i] & 255) >>> 4]; text[i * 2 + 1] = alphabet[bytes[i] & 15];
        }
        return new String(text);
    }
}
