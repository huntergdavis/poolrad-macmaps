package name.osher.gil.minivmac.hfs;

import java.io.IOException;

/**
 * Random access to a disk image, so the HFS reader can work against a 64 MB
 * file on the tablet without holding it in memory, and against a byte array in
 * a test.
 */
public interface Blocks extends java.io.Closeable {
    long size() throws IOException;
    void read(long offset, byte[] into, int at, int length) throws IOException;
    /** Optional: an image opened read-only throws, which is the safe default. */
    void write(long offset, byte[] from, int at, int length) throws IOException;

    /** Nothing to release unless the image is a file. */
    @Override default void close() throws IOException { }

    final class ReadOnly implements Blocks {
        private final Blocks inner;
        public ReadOnly(Blocks inner) { this.inner = inner; }
        @Override public long size() throws IOException { return inner.size(); }
        @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
            inner.read(offset, into, at, length);
        }
        @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
            throw new IOException("This disk image is open read-only");
        }
        @Override public void close() throws IOException { inner.close(); }
    }

    /** For tests and for small images; the whole volume in memory. */
    final class Array implements Blocks {
        private final byte[] data;
        public Array(byte[] data) { this.data = data; }
        public byte[] data() { return data; }
        @Override public long size() { return data.length; }
        @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > data.length)
                throw new IOException("Read outside the image at " + offset);
            System.arraycopy(data, (int) offset, into, at, length);
        }
        @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > data.length)
                throw new IOException("Write outside the image at " + offset);
            System.arraycopy(from, at, data, (int) offset, length);
        }
    }
}
