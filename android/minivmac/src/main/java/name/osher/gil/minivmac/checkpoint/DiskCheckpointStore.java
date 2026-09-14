package name.osher.gil.minivmac.checkpoint;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
import name.osher.gil.minivmac.desktop.DiskAccessGate;

/**
 * Explicit, player-requested copies of a writable emulator disk, taken only
 * while the guest is shut down.
 *
 * A checkpoint is a plain byte-for-byte copy of the disk file, verified by
 * SHA-256 after it is written. It is <b>not</b> an emulator save state: it
 * cannot capture RAM, an in-flight write or a running game, which is exactly
 * why every operation here demands the maintenance lease and refuses to run
 * while emulation holds the disk. The recorded area label and thumbnail
 * describe the last map the companion displayed, not a position read out of
 * the copied disk.
 */
public final class DiskCheckpointStore {
    public static final int MAX_CHECKPOINTS = 6;
    public static final long MAX_DISK_BYTES = 512L * 1024 * 1024;
    public static final int MAX_THUMBNAIL_BYTES = 256 * 1024;
    public static final int MAX_LABEL_CHARS = 120;
    private static final int MAGIC = 0x5052434b; // PRCK
    private static final int VERSION = 1;
    private static final String META = "checkpoint.bin", PAYLOAD = "disk.img";

    private final File root;
    private final DiskAccessGate gate;

    public DiskCheckpointStore(File root, DiskAccessGate gate) {
        if (root == null || gate == null) throw new IllegalArgumentException("Missing checkpoint storage");
        this.root = root; this.gate = gate;
    }

    /** Metadata only; the disk copy itself is never held in memory. */
    public static final class Checkpoint {
        public final String id, diskName, areaLabel, notebookLabel;
        public final long createdAt, diskBytes;
        public final byte[] digest;
        private final byte[] thumbnail;
        Checkpoint(String id, String diskName, long createdAt, long diskBytes, byte[] digest,
                String areaLabel, String notebookLabel, byte[] thumbnail) {
            this.id = id; this.diskName = diskName; this.createdAt = createdAt; this.diskBytes = diskBytes;
            this.digest = digest; this.areaLabel = areaLabel; this.notebookLabel = notebookLabel;
            this.thumbnail = thumbnail;
        }
        /** PNG bytes as supplied when the checkpoint was taken, or null. */
        public byte[] thumbnail() { return thumbnail == null ? null : thumbnail.clone(); }
        public boolean hasThumbnail() { return thumbnail != null; }
        public String shortDigest() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 4; i++) out.append(String.format("%02x", digest[i]));
            return out.toString();
        }
    }

    // ---- reading ----------------------------------------------------------

    /** Newest first. A directory we cannot read is reported, never silently skipped. */
    public synchronized List<Checkpoint> list() throws IOException {
        List<Checkpoint> result = new ArrayList<>();
        if (!root.isDirectory()) return result;
        for (File child : children(root)) {
            requireDirectChild(root, child);
            String name = child.getName();
            if (name.startsWith(".pending-")) continue;
            if (!validId(name) || !child.isDirectory()) throw new IOException("Unrecognized checkpoint storage entry");
            result.add(readMeta(child));
        }
        Collections.sort(result, new Comparator<Checkpoint>() {
            @Override public int compare(Checkpoint a, Checkpoint b) {
                int byTime = Long.compare(b.createdAt, a.createdAt);
                return byTime != 0 ? byTime : a.id.compareTo(b.id);
            }
        });
        return result;
    }

    /** Confirms the stored copy still matches the digest recorded when it was taken. */
    public synchronized boolean verify(String id) throws IOException {
        Checkpoint checkpoint = readMeta(directory(id));
        File payload = new File(directory(id), PAYLOAD);
        return payload.isFile() && payload.length() == checkpoint.diskBytes
                && java.util.Arrays.equals(checkpoint.digest, digestOf(payload));
    }

    // ---- writing ----------------------------------------------------------

    /**
     * Copies {@code disk} while holding the maintenance lease, then re-reads the
     * copy and refuses to publish it unless the digest matches. Nothing is
     * written into the emulator's disk folder.
     */
    public Checkpoint create(File disk, String areaLabel, String notebookLabel, byte[] thumbnail)
            throws IOException {
        File source = regularDisk(disk);
        String area = label(areaLabel), notebook = label(notebookLabel);
        byte[] preview = thumbnail(thumbnail);
        try (DiskAccessGate.Lease ignored = lease()) {
            synchronized (this) {
                if (list().size() >= MAX_CHECKPOINTS)
                    throw new IOException("Checkpoint limit reached. Delete one before saving another.");
                long bytes = source.length();
                if (root.isDirectory() && root.getUsableSpace() < bytes + (bytes / 4))
                    throw new IOException("Not enough free space for a complete checkpoint; nothing was saved.");
                String id = UUID.randomUUID().toString();
                File staging = staging(id);
                try {
                    byte[] digest = copy(source, new File(staging, PAYLOAD));
                    if (source.length() != bytes)
                        throw new IOException("The disk changed while copying; the checkpoint was discarded.");
                    File copied = new File(staging, PAYLOAD);
                    if (!java.util.Arrays.equals(digest, digestOf(copied)))
                        throw new IOException("The checkpoint copy did not verify; it was discarded.");
                    Checkpoint checkpoint = new Checkpoint(id, source.getName(),
                            System.currentTimeMillis(), bytes, digest, area, notebook, preview);
                    writeMeta(staging, checkpoint);
                    File destination = new File(root, id);
                    if (destination.exists() || !staging.renameTo(destination))
                        throw new IOException("Cannot publish the checkpoint atomically; nothing was saved.");
                    return checkpoint;
                } finally {
                    if (staging.isDirectory()) clean(staging);
                }
            }
        }
    }

    /**
     * Replaces {@code disk} with a verified checkpoint, after first checkpointing
     * the disk as it stands so the replacement can always be undone. Both the
     * safety copy and the restore are refused unless everything verifies.
     */
    public Checkpoint restore(String id, File disk) throws IOException {
        File target = regularDisk(disk);
        // Check the request before copying anything: a mistyped or damaged
        // checkpoint must not consume a slot with a pointless safety copy.
        try (DiskAccessGate.Lease ignored = lease()) {
            synchronized (this) {
                if (!verify(id))
                    throw new IOException("This checkpoint no longer verifies; the current disk was not replaced.");
                if (list().size() >= MAX_CHECKPOINTS)
                    throw new IOException("Delete a checkpoint first: restoring keeps a safety copy of the current disk.");
            }
        }
        Checkpoint safety = create(target, "Before restoring a checkpoint", "Automatic safety copy", null);
        try (DiskAccessGate.Lease ignored = lease()) {
            synchronized (this) {
                File folder = directory(id);
                Checkpoint checkpoint = readMeta(folder);
                File payload = new File(folder, PAYLOAD);
                if (!payload.isFile() || payload.length() != checkpoint.diskBytes
                        || !java.util.Arrays.equals(checkpoint.digest, digestOf(payload))) {
                    throw new IOException("This checkpoint no longer verifies; the current disk was not replaced.");
                }
                File staging = File.createTempFile(".pending-", ".img", target.getParentFile());
                try {
                    byte[] digest = copy(payload, staging);
                    if (!java.util.Arrays.equals(checkpoint.digest, digest)
                            || !java.util.Arrays.equals(digest, digestOf(staging))) {
                        throw new IOException("The restored copy did not verify; the current disk was not replaced.");
                    }
                    // Android's same-directory rename replaces atomically. Never
                    // delete the live disk first: a failed rename must leave it.
                    if (!staging.renameTo(target))
                        throw new IOException("Cannot replace the disk atomically; the current disk was kept.");
                    return safety;
                } finally {
                    if (staging.exists()) staging.delete();
                }
            }
        }
    }

    /** Call only after explicit confirmation; a removed checkpoint cannot be recovered. */
    public void delete(String id) throws IOException {
        try (DiskAccessGate.Lease ignored = lease()) {
            synchronized (this) {
                File folder = directory(id);
                readMeta(folder); // Refuse to remove something we cannot identify.
                File retired = File.createTempFile(".pending-", ".gone", root);
                if (!retired.delete() || !folder.renameTo(retired))
                    throw new IOException("Cannot remove the checkpoint atomically; nothing was removed.");
                clean(retired);
            }
        }
    }

    // ---- internals --------------------------------------------------------

    private DiskAccessGate.Lease lease() throws IOException {
        DiskAccessGate.Lease lease = gate.tryBeginMaintenance();
        if (lease == null) throw new IOException("Shut down the Mac and wait for disk operations before saving or restoring a checkpoint");
        return lease;
    }

    private File regularDisk(File disk) throws IOException {
        if (disk == null) throw new IOException("Choose a disk");
        File target = disk.getAbsoluteFile();
        if (!target.equals(target.getCanonicalFile()) || !target.isFile()
                || target.length() <= 0 || target.length() > MAX_DISK_BYTES) {
            throw new IOException("Checkpoints only support a regular imported disk file");
        }
        return target;
    }

    private static String label(String value) throws IOException {
        String text = value == null ? "" : value.trim();
        if (text.length() > MAX_LABEL_CHARS) throw new IOException("Checkpoint label is too long");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7f) throw new IOException("Checkpoint label contains control characters");
        }
        return text.isEmpty() ? "Not recorded" : text;
    }

    private static byte[] thumbnail(byte[] value) throws IOException {
        if (value == null || value.length == 0) return null;
        if (value.length > MAX_THUMBNAIL_BYTES) throw new IOException("Checkpoint thumbnail is too large");
        // Header-only sanity: this class never decodes an image.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        for (int i = 0; i < png.length; i++)
            if (value[i] != png[i]) throw new IOException("Checkpoint thumbnail is not a PNG");
        return value.clone();
    }

    /** Staged under a ".pending-" name so an interrupted copy is never listed. */
    private File staging(String id) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create checkpoint storage");
        if (!root.equals(root.getCanonicalFile())) throw new IOException("Checkpoint storage must be a real directory");
        File folder = new File(root, ".pending-" + id);
        if (folder.exists() || !folder.mkdir()) throw new IOException("Cannot create a checkpoint folder");
        return folder;
    }

    private File directory(String id) throws IOException {
        if (!validId(id)) throw new IOException("Unrecognized checkpoint");
        File folder = new File(root, id);
        requireDirectChild(root, folder);
        if (!folder.isDirectory()) throw new IOException("That checkpoint is missing");
        return folder;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static void requireDirectChild(File parent, File child) throws IOException {
        File canonical = child.getCanonicalFile();
        if (!canonical.equals(child.getAbsoluteFile()) || !parent.getCanonicalFile().equals(canonical.getParentFile()))
            throw new IOException("Checkpoint storage must not follow links or leave its folder");
    }

    private static File[] children(File directory) throws IOException {
        File[] found = directory.listFiles();
        if (found == null) throw new IOException("Cannot read checkpoint storage");
        return found;
    }

    private static void clean(File folder) {
        try {
            for (File child : children(folder)) {
                requireDirectChild(folder, child);
                if (child.isFile()) child.delete();
            }
            folder.delete();
        } catch (IOException | SecurityException ignored) {
            // Private, already-retired bytes. Never chase an unexpected path.
        }
    }

    private static byte[] copy(File source, File destination) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(source);
                FileOutputStream stream = new FileOutputStream(destination)) {
            OutputStream out = stream;
            for (int read; (read = in.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read); out.write(buffer, 0, read);
            }
            out.flush(); stream.getFD().sync();
        }
        return digest.digest();
    }

    private static byte[] digestOf(File file) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            for (int read; (read = in.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException missing) { throw new IOException("SHA-256 is unavailable", missing); }
    }

    private static void writeMeta(File folder, Checkpoint checkpoint) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(checkpoint.id); out.writeUTF(checkpoint.diskName);
            out.writeLong(checkpoint.createdAt); out.writeLong(checkpoint.diskBytes);
            out.write(checkpoint.digest);
            out.writeUTF(checkpoint.areaLabel); out.writeUTF(checkpoint.notebookLabel);
            byte[] thumbnail = checkpoint.thumbnail;
            out.writeInt(thumbnail == null ? 0 : thumbnail.length);
            if (thumbnail != null) out.write(thumbnail);
        }
        byte[] payload = bytes.toByteArray();
        File pending = File.createTempFile(".pending-", ".tmp", folder);
        try {
            try (FileOutputStream stream = new FileOutputStream(pending);
                    DataOutputStream out = new DataOutputStream(stream)) {
                CRC32 crc = new CRC32(); crc.update(payload);
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(payload.length);
                out.write(payload); out.writeLong(crc.getValue());
                out.flush(); stream.getFD().sync();
            }
            if (!pending.renameTo(new File(folder, META)))
                throw new IOException("Cannot save checkpoint details atomically");
        } finally {
            if (pending.exists()) pending.delete();
        }
    }

    private static Checkpoint readMeta(File folder) throws IOException {
        File file = new File(folder, META);
        long size = file.length();
        if (!file.isFile() || size < 20 || size > MAX_THUMBNAIL_BYTES + 4096L)
            throw new IOException("Missing, truncated or oversized checkpoint details");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != MAGIC) throw new IOException("Unrecognized checkpoint record");
            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("Unrecognized checkpoint version");
            int length = in.readInt();
            if (length < 0 || size != length + 20L) throw new IOException("Invalid checkpoint record length");
            byte[] payload = new byte[length];
            in.readFully(payload);
            CRC32 crc = new CRC32(); crc.update(payload);
            if (in.readLong() != crc.getValue() || in.read() != -1)
                throw new IOException("Checkpoint details failed their checksum");
            try (DataInputStream body = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
                String id = body.readUTF();
                if (!id.equals(folder.getName())) throw new IOException("Checkpoint identity does not match its folder");
                String diskName = body.readUTF();
                long createdAt = body.readLong(), diskBytes = body.readLong();
                if (createdAt < 0 || diskBytes <= 0 || diskBytes > MAX_DISK_BYTES)
                    throw new IOException("Invalid checkpoint size or timestamp");
                byte[] digest = new byte[32]; body.readFully(digest);
                String area = body.readUTF(), notebook = body.readUTF();
                int thumbnailBytes = body.readInt();
                if (thumbnailBytes < 0 || thumbnailBytes > MAX_THUMBNAIL_BYTES)
                    throw new IOException("Invalid checkpoint thumbnail size");
                byte[] thumbnail = null;
                if (thumbnailBytes > 0) { thumbnail = new byte[thumbnailBytes]; body.readFully(thumbnail); }
                if (body.read() != -1) throw new IOException("Trailing checkpoint data");
                return new Checkpoint(id, diskName, createdAt, diskBytes, digest, area, notebook, thumbnail);
            }
        }
    }
}
