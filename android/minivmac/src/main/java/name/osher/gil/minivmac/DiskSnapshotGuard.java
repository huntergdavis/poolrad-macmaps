package name.osher.gil.minivmac;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.TreeMap;

/** Binds a stopped RAM capture to the exact mounted disk contents, without copying disks.
 * Core serializes mount/write/eject and the final native restore with its own monitor.
 * Hashing uses positional reads on the mounted handles on the save/load worker thread.
 */
public final class DiskSnapshotGuard {
    private static final int MAX_DRIVES = 32;
    private final TreeMap<Integer, Drive> drives = new TreeMap<>();
    private Object revision = new Object();
    private boolean stopped;

    private static final class Drive {
        final int slot;
        final RandomAccessFile file;
        final boolean writable;
        Drive(int slot, RandomAccessFile file, boolean writable) {
            this.slot = slot; this.file = file; this.writable = writable;
        }
    }

    public synchronized void mounted(int slot, RandomAccessFile file, boolean writable) {
        if (stopped || slot < 0 || slot >= MAX_DRIVES || file == null)
            throw new IllegalArgumentException("Invalid mounted disk");
        revision = new Object();
        drives.put(slot, new Drive(slot, file, writable));
    }

    /** Invalidate before attempting a write, including a write that fails part way. */
    public synchronized void beforeWrite() { revision = new Object(); }

    public synchronized void unmounted(int slot) {
        revision = new Object();
        drives.remove(slot);
    }

    public synchronized void stopped() {
        stopped = true; revision = new Object(); drives.clear();
    }

    public static final class Ticket {
        private final DiskSnapshotGuard owner;
        private final Object revision;
        private final Drive[] drives;
        private Ticket(DiskSnapshotGuard owner, Object revision, Drive[] drives) {
            this.owner = owner; this.revision = revision; this.drives = drives;
        }
        /** Worker only. Refuses a capture if any mount or write intervened. */
        public Fingerprint fingerprint() throws IOException { return owner.fingerprint(this); }
    }

    public synchronized Ticket capture() {
        if (stopped) throw new IllegalStateException("The emulator has stopped");
        return new Ticket(this, revision, drives.values().toArray(new Drive[0]));
    }

    private synchronized void requireCurrent(Ticket ticket) throws IOException {
        if (stopped || ticket.owner != this || ticket.revision != revision)
            throw new IOException("Disks changed during save/load. Try again when the game is idle.");
    }

    private Fingerprint fingerprint(Ticket ticket) throws IOException {
        requireCurrent(ticket);
        Entry[] entries = new Entry[ticket.drives.length];
        byte[] chunk = new byte[256 * 1024];
        for (int i = 0; i < entries.length; i++) {
            Drive drive = ticket.drives[i];
            if (drive.writable) drive.file.getFD().sync();
            FileChannel channel = drive.file.getChannel();
            long length = channel.size();
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
            long offset = 0;
            while (offset < length) {
                int count = channel.read(ByteBuffer.wrap(chunk, 0,
                        (int)Math.min(chunk.length, length - offset)), offset);
                if (count <= 0) throw new IOException("Could not read a mounted disk completely");
                digest.update(chunk, 0, count); offset += count;
                requireCurrent(ticket);
            }
            if (channel.size() != length) throw new IOException("Disk size changed during save/load");
            entries[i] = new Entry(drive.slot, drive.writable, length, digest.digest());
        }
        requireCurrent(ticket);
        return new Fingerprint(entries);
    }

    /** A proof is usable only while this instance's mounted disks remain unchanged. */
    public static final class Verified {
        private final Ticket ticket;
        private Verified(Ticket ticket) { this.ticket = ticket; }
    }

    public Verified verify(Fingerprint expected) throws IOException {
        if (expected == null) throw new IOException("Snapshot has no disk verification");
        Ticket ticket;
        try { ticket = capture(); }
        catch (IllegalStateException stopped) { throw new IOException(stopped.getMessage(), stopped); }
        if (!expected.equals(fingerprint(ticket)))
            throw new IOException("The mounted disks differ from this snapshot. Load an original game save instead.");
        synchronized (this) {
            requireCurrent(ticket);
            return new Verified(ticket);
        }
    }

    /** Called again with Core's monitor held at the native restore boundary. No disk I/O. */
    public synchronized boolean isCurrent(Verified proof) {
        return proof != null && !stopped && proof.ticket.owner == this
                && proof.ticket.revision == revision;
    }

    private static final class Entry {
        final int slot;
        final boolean writable;
        final long length;
        final byte[] sha256;
        Entry(int slot, boolean writable, long length, byte[] sha256) {
            this.slot = slot; this.writable = writable; this.length = length;
            this.sha256 = sha256.clone();
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry e = (Entry)other;
            return slot == e.slot && writable == e.writable && length == e.length
                    && Arrays.equals(sha256, e.sha256);
        }
        @Override public int hashCode() { return slot * 31 + Arrays.hashCode(sha256); }
    }

    /** Canonical, bounded binary metadata. No file paths or mutable disk handles are persisted. */
    public static final class Fingerprint {
        private final Entry[] entries;
        private Fingerprint(Entry[] entries) { this.entries = entries.clone(); }
        public static Fingerprint empty() { return new Fingerprint(new Entry[0]); }
        public void writeTo(OutputStream stream) throws IOException {
            DataOutputStream out = new DataOutputStream(stream);
            out.writeByte(entries.length);
            for (Entry e : entries) {
                out.writeByte(e.slot); out.writeByte(e.writable ? 1 : 0);
                out.writeLong(e.length); out.write(e.sha256);
            }
        }
        public static Fingerprint readFrom(InputStream stream) throws IOException {
            DataInputStream in = new DataInputStream(stream);
            int count = in.readUnsignedByte();
            if (count > MAX_DRIVES) throw new IOException("Invalid snapshot disk count");
            Entry[] entries = new Entry[count];
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int slot = in.readUnsignedByte(), writable = in.readUnsignedByte();
                long length = in.readLong();
                if (slot <= previous || slot >= MAX_DRIVES || writable > 1 || length < 0)
                    throw new IOException("Invalid snapshot disk metadata");
                byte[] hash = new byte[32]; in.readFully(hash);
                entries[i] = new Entry(slot, writable == 1, length, hash); previous = slot;
            }
            return new Fingerprint(entries);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Fingerprint && Arrays.equals(entries, ((Fingerprint)other).entries);
        }
        @Override public int hashCode() { return Arrays.hashCode(entries); }
    }
}
