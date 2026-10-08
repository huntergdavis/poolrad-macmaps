package name.osher.gil.minivmac;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps recoverable disk bytes for new saves. One compressed base is shared
 * by saves of a disk; each save carries only its changed 4 KiB blocks.
 * Capture still obeys DiskSnapshotGuard's revision ticket, and a reconstructed
 * disk is SHA-256 checked against the machine snapshot before it can be used.
 */
final class DiskCheckpointStore {
    static final String SUFFIX = ".disks";
    private static final byte[] MAGIC = {'P','R','D','L','1','\n'};
    private static final int BLOCK = 4096;
    private static final int MAX_DRIVES = 32;
    private static final long MAX_DISK = Integer.MAX_VALUE;
    private final File bases;
    private final File[] roots;

    DiskCheckpointStore(File saveDirectory, File... allowedRoots) {
        bases = new File(saveDirectory, "disk-bases");
        roots = allowedRoots.clone();
    }

    static final class Prepared implements AutoCloseable {
        static final class Disk {
            final int slot;
            final boolean writable;
            final File target;
            final File staged;
            Disk(int slot, boolean writable, File target, File staged) {
                this.slot = slot; this.writable = writable;
                this.target = target; this.staged = staged;
            }
        }
        final List<Disk> disks;
        private boolean closed;
        Prepared(List<Disk> disks) { this.disks = disks; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Disk disk : disks) disk.staged.delete();
        }
    }

    private static final class Location {
        final int root;
        final String name;
        final File file;
        Location(int root, String name, File file) {
            this.root = root; this.name = name; this.file = file;
        }
    }

    private static final class Base {
        final long length;
        final byte[] hash;
        final byte[] blocks;
        Base(long length, byte[] hash, byte[] blocks) {
            this.length = length; this.hash = hash; this.blocks = blocks;
        }
    }

    private static final class Capture {
        final DiskSnapshotGuard.Drive drive;
        final Location location;
        final long length;
        final byte[] hash;
        final byte[] base;
        final File delta;
        Capture(DiskSnapshotGuard.Drive drive, Location location, long length,
                byte[] hash, byte[] base, File delta) {
            this.drive = drive; this.location = location; this.length = length;
            this.hash = hash; this.base = base; this.delta = delta;
        }
    }

    File sidecar(File save) { return new File(save.getPath() + SUFFIX); }
    boolean hasCheckpoint(File save) { return sidecar(save).isFile(); }

    DiskSnapshotGuard.Fingerprint capture(DiskSnapshotGuard.Ticket ticket, File save) throws IOException {
        DiskSnapshotGuard.Drive[] drives = ticket.drives();
        if (drives.length > MAX_DRIVES) throw new IOException("Too many mounted disks");
        if (!bases.isDirectory() && !bases.mkdirs()) throw new IOException("Could not make disk-base folder");
        List<Capture> captures = new ArrayList<>();
        Set<File> destinations = new HashSet<>();
        File target = sidecar(save), partial = new File(target.getPath() + ".part");
        try {
            for (DiskSnapshotGuard.Drive drive : drives) {
                ticket.requireCurrent();
                Capture capture = captureDrive(ticket, drive, save.getParentFile());
                captures.add(capture);
                if (!destinations.add(capture.location.file))
                    throw new IOException("The same disk is mounted in more than one slot");
            }
            int[] slots = new int[captures.size()];
            boolean[] writable = new boolean[captures.size()];
            long[] lengths = new long[captures.size()];
            byte[][] hashes = new byte[captures.size()][];
            try (FileOutputStream file = new FileOutputStream(partial);
                 DataOutputStream out = new DataOutputStream(file)) {
                out.write(MAGIC);
                out.writeByte(captures.size());
                for (int i = 0; i < captures.size(); i++) {
                    Capture c = captures.get(i);
                    slots[i] = c.drive.slot; writable[i] = c.drive.writable;
                    lengths[i] = c.length; hashes[i] = c.hash;
                    out.writeByte(slots[i]);
                    out.writeBoolean(writable[i]);
                    out.writeByte(c.location.root);
                    out.writeUTF(c.location.name);
                    out.writeLong(c.length);
                    out.write(c.hash);
                    out.write(c.base);
                    if (c.delta.length() > Integer.MAX_VALUE)
                        throw new IOException("Disk change set is too large");
                    out.writeInt((int)c.delta.length());
                    try (FileInputStream in = new FileInputStream(c.delta)) { copy(in, out); }
                }
                out.flush();
                file.getFD().sync();
            }
            ticket.requireCurrent();
            if (!partial.renameTo(target)) throw new IOException("Could not publish disk changes");
            return DiskSnapshotGuard.Fingerprint.of(slots, writable, lengths, hashes);
        } finally {
            partial.delete();
            for (Capture c : captures) c.delta.delete();
        }
    }

    private Capture captureDrive(DiskSnapshotGuard.Ticket ticket, DiskSnapshotGuard.Drive drive,
                                 File temporaryDirectory) throws IOException {
        Location location = locate(drive.path);
        long length = drive.file.getChannel().size();
        if (length < 0 || length > MAX_DISK) throw new IOException("Disk image is too large");
        File index = indexFile(location);
        Base base = readBase(index);
        boolean newBase = base == null || base.length != length;
        if (newBase) {
            base = writeBase(ticket, drive, length);
            writeBaseIndex(index, base);
        }
        File delta = File.createTempFile("poolrad-delta-", ".part", temporaryDirectory);
        try {
            byte[] current;
            if (newBase) {
                writeEmptyDelta(delta);
                ticket.requireCurrent();
                current = base.hash;
            } else current = writeDelta(ticket, drive, length, base, delta);
            return new Capture(drive, location, length, current, base.hash, delta);
        } catch (IOException | RuntimeException failure) {
            delta.delete();
            throw failure;
        }
    }

    private static void writeEmptyDelta(File delta) throws IOException {
        try (FileOutputStream file = new FileOutputStream(delta);
             GZIPOutputStream gzip = new GZIPOutputStream(file);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(-1);
            out.flush();
            gzip.finish();
            file.getFD().sync();
        }
    }

    private Base writeBase(DiskSnapshotGuard.Ticket ticket, DiskSnapshotGuard.Drive drive,
                             long length) throws IOException {
        if (drive.writable) drive.file.getFD().sync();
        File partial = File.createTempFile("poolrad-base-", ".part", bases);
        MessageDigest digest = sha256(), blockDigest = sha256();
        byte[] block = new byte[BLOCK];
        byte[] blockHashes = new byte[blockCount(length) * 32];
        try {
            try (FileOutputStream file = new FileOutputStream(partial);
                 GZIPOutputStream gzip = new GZIPOutputStream(file)) {
                for (long offset = 0; offset < length; offset += BLOCK) {
                    int count = (int)Math.min(BLOCK, length - offset);
                    readBlock(drive.file.getChannel(), block, count, offset);
                    digest.update(block, 0, count);
                    blockDigest.update(block, 0, count);
                    System.arraycopy(blockDigest.digest(), 0, blockHashes,
                            (int)(offset / BLOCK) * 32, 32);
                    gzip.write(block, 0, count);
                    ticket.requireCurrent();
                }
                gzip.finish();
                file.getFD().sync();
            }
            ticket.requireCurrent();
            byte[] hash = digest.digest();
            File base = baseFile(hash);
            if (!base.isFile() && !partial.renameTo(base))
                throw new IOException("Could not publish disk base");
            return new Base(length, hash, blockHashes);
        } finally { partial.delete(); }
    }

    private byte[] writeDelta(DiskSnapshotGuard.Ticket ticket, DiskSnapshotGuard.Drive drive,
                              long length, Base base, File delta) throws IOException {
        if (drive.writable) drive.file.getFD().sync();
        File source = baseFile(base.hash);
        if (!source.isFile()) throw new IOException("The shared disk base is missing");
        MessageDigest current = sha256(), blockDigest = sha256();
        byte[] now = new byte[BLOCK];
        try (FileOutputStream file = new FileOutputStream(delta);
             GZIPOutputStream gzip = new GZIPOutputStream(file);
             DataOutputStream out = new DataOutputStream(gzip)) {
            for (long offset = 0; offset < length; offset += BLOCK) {
                int count = (int)Math.min(BLOCK, length - offset);
                readBlock(drive.file.getChannel(), now, count, offset);
                current.update(now, 0, count);
                blockDigest.update(now, 0, count);
                if (!sameAt(blockDigest.digest(), base.blocks, (int)(offset / BLOCK) * 32)) {
                    out.writeInt((int)(offset / BLOCK));
                    out.writeShort(count);
                    out.write(now, 0, count);
                }
                ticket.requireCurrent();
            }
            out.writeInt(-1);
            out.flush();
            gzip.finish();
            file.getFD().sync();
        }
        ticket.requireCurrent();
        return current.digest();
    }

    Prepared prepare(File save, DiskSnapshotGuard.Fingerprint expected) throws IOException {
        File sidecar = sidecar(save);
        List<Prepared.Disk> stages = new ArrayList<>();
        Set<File> destinations = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new FileInputStream(sidecar))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Invalid disk changes");
            int count = in.readUnsignedByte();
            if (count > MAX_DRIVES || count != expected.size())
                throw new IOException("Disk changes do not match the snapshot");
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int slot = in.readUnsignedByte();
                int writableFlag = in.readUnsignedByte();
                boolean writable = writableFlag == 1;
                int root = in.readUnsignedByte();
                String name = in.readUTF();
                long length = in.readLong();
                byte[] hash = new byte[32], base = new byte[32];
                in.readFully(hash); in.readFully(base);
                int deltaLength = in.readInt();
                if (slot <= previous || writableFlag > 1 || length < 0 || length > MAX_DISK || deltaLength < 0
                        || !expected.matches(i, slot, writable, length, hash))
                    throw new IOException("Disk changes do not match the snapshot");
                Location location = resolve(root, name);
                if (!destinations.add(location.file))
                    throw new IOException("Duplicate saved disk path");
                LimitedInputStream delta = new LimitedInputStream(in, deltaLength);
                File staged = reconstruct(location.file, length, hash, base, delta);
                stages.add(new Prepared.Disk(slot, writable, location.file, staged));
                if (delta.remaining != 0) throw new IOException("Disk changes have trailing bytes");
                previous = slot;
            }
            if (in.read() != -1) throw new IOException("Disk changes have trailing entries");
            return new Prepared(stages);
        } catch (IOException | RuntimeException failure) {
            new Prepared(stages).close();
            throw failure;
        }
    }

    private File reconstruct(File target, long length, byte[] hash, byte[] base,
                             InputStream delta) throws IOException {
        File staged = File.createTempFile(".poolrad-restore-", ".part", target.getParentFile());
        try {
            MessageDigest baseDigest = sha256();
            byte[] block = new byte[BLOCK];
            try (GZIPInputStream reference = new GZIPInputStream(new FileInputStream(baseFile(base)));
                 FileOutputStream file = new FileOutputStream(staged)) {
                for (long remaining = length; remaining > 0; ) {
                    int count = (int)Math.min(BLOCK, remaining);
                    readFully(reference, block, count);
                    baseDigest.update(block, 0, count);
                    file.write(block, 0, count);
                    remaining -= count;
                }
                if (reference.read() != -1 || !Arrays.equals(baseDigest.digest(), base))
                    throw new IOException("The shared disk base is corrupt");
                file.getFD().sync();
            }
            try (RandomAccessFile file = new RandomAccessFile(staged, "rw");
                 GZIPInputStream gzip = new GZIPInputStream(delta);
                 DataInputStream changes = new DataInputStream(gzip)) {
                int previous = -1;
                while (true) {
                    int blockIndex = changes.readInt();
                    if (blockIndex == -1) break;
                    long offset = (long)blockIndex * BLOCK;
                    int count = changes.readUnsignedShort();
                    if (blockIndex <= previous || offset >= length
                            || count != Math.min(BLOCK, length - offset))
                        throw new IOException("Invalid changed disk block");
                    changes.readFully(block, 0, count);
                    file.seek(offset);
                    file.write(block, 0, count);
                    previous = blockIndex;
                }
                if (changes.read() != -1) throw new IOException("Disk changes have trailing blocks");
                file.getFD().sync();
            }
            if (!Arrays.equals(hashFile(staged), hash))
                throw new IOException("Recovered disk bytes do not match the snapshot");
            return staged;
        } catch (IOException | RuntimeException failure) {
            staged.delete();
            throw failure;
        }
    }

    private Base readBase(File index) throws IOException {
        if (!index.isFile()) return null;
        if (index.length() < 72) throw new IOException("Invalid disk-base index");
        try (DataInputStream in = new DataInputStream(new FileInputStream(index))) {
            long length = in.readLong();
            byte[] hash = new byte[32]; in.readFully(hash);
            if (length < 0 || length > MAX_DISK) throw new IOException("Invalid disk-base index");
            int blocks = blockCount(length);
            if (index.length() != 72L + (long)blocks * 32)
                throw new IOException("Invalid disk-base index");
            byte[] blockHashes = new byte[blocks * 32], checksum = new byte[32];
            in.readFully(blockHashes); in.readFully(checksum);
            if (!Arrays.equals(checksum, indexChecksum(length, hash, blockHashes)))
                throw new IOException("Corrupt disk-base index");
            return new Base(length, hash, blockHashes);
        }
    }

    private void writeBaseIndex(File index, Base base) throws IOException {
        File partial = new File(index.getPath() + ".part");
        try {
            try (FileOutputStream file = new FileOutputStream(partial);
                 DataOutputStream out = new DataOutputStream(file)) {
                out.writeLong(base.length); out.write(base.hash); out.write(base.blocks);
                out.write(indexChecksum(base.length, base.hash, base.blocks)); out.flush();
                file.getFD().sync();
            }
            if (!partial.renameTo(index)) throw new IOException("Could not publish disk-base index");
        } finally { partial.delete(); }
    }

    private File indexFile(Location location) throws IOException {
        MessageDigest digest = sha256();
        digest.update(location.file.getPath().getBytes(StandardCharsets.UTF_8));
        return new File(bases, hex(digest.digest()) + ".ref");
    }
    private File baseFile(byte[] hash) { return new File(bases, hex(hash) + ".gz"); }

    private Location locate(File path) throws IOException {
        if (path == null) throw new IOException("A mounted disk has no private file path");
        File actual = path.getCanonicalFile();
        for (int i = 0; i < roots.length; i++) {
            if (actual.getParentFile().equals(roots[i].getCanonicalFile()))
                return new Location(i, actual.getName(), actual);
        }
        throw new IOException("A mounted disk is outside private disk storage");
    }

    private Location resolve(int root, String name) throws IOException {
        if (root < 0 || root >= roots.length || name.isEmpty() || name.length() > 255
                || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0)
            throw new IOException("Invalid saved disk path");
        File actual = new File(roots[root], name).getCanonicalFile();
        if (!actual.getParentFile().equals(roots[root].getCanonicalFile()))
            throw new IOException("Saved disk path leaves private storage");
        return new Location(root, name, actual);
    }

    private static final class LimitedInputStream extends FilterInputStream {
        long remaining;
        LimitedInputStream(InputStream input, long length) { super(input); remaining = length; }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = in.read();
            if (value >= 0) remaining--;
            return value;
        }
        @Override public int read(byte[] bytes, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (remaining == 0) return -1;
            int count = in.read(bytes, off, (int)Math.min(len, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
        @Override public void close() { /* The surrounding bundle owns the stream. */ }
    }

    private static byte[] hashFile(File file) throws IOException {
        MessageDigest digest = sha256();
        byte[] block = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(file)) {
            int count;
            while ((count = in.read(block)) != -1) digest.update(block, 0, count);
        }
        return digest.digest();
    }
    private static MessageDigest sha256() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] text = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            text[i * 2] = digits[(bytes[i] >>> 4) & 15];
            text[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(text);
    }
    private static int blockCount(long length) { return (int)((length + BLOCK - 1) / BLOCK); }
    private static byte[] indexChecksum(long length, byte[] hash, byte[] blocks) throws IOException {
        MessageDigest digest = sha256();
        digest.update(ByteBuffer.allocate(8).putLong(length).array());
        digest.update(hash); digest.update(blocks);
        return digest.digest();
    }
    private static boolean sameAt(byte[] hash, byte[] blocks, int offset) {
        for (int i = 0; i < hash.length; i++) if (hash[i] != blocks[offset + i]) return false;
        return true;
    }
    private static void readBlock(FileChannel channel, byte[] into, int count, long offset)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(into, 0, count);
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, offset + buffer.position());
            if (read <= 0) throw new IOException("Could not read a mounted disk completely");
        }
    }
    private static void readFully(InputStream in, byte[] into, int count) throws IOException {
        int done = 0;
        while (done < count) {
            int read = in.read(into, done, count - done);
            if (read <= 0) throw new IOException("Disk checkpoint ended early");
            done += read;
        }
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] block = new byte[64 * 1024];
        int count;
        while ((count = in.read(block)) != -1) out.write(block, 0, count);
    }
}
