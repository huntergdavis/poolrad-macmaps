package name.osher.gil.minivmac;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNoException;

public class NotebookTransferFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};
    private static final byte[] ARCHIVE = {'P', 'R', 'N', 'A', 0, 0, 0, 1, 1, 2, 3};

    private File write(File directory, String extension, byte[] bytes) throws IOException {
        File file = NotebookTransferFiles.create(directory, extension);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
        return file;
    }

    @Test public void createsUniqueOwnedFilesAndRequiredDirectory() throws Exception {
        File directory = new File(temporary.getRoot(), "new-cache");
        File first = NotebookTransferFiles.create(directory, "prnb");
        File second = NotebookTransferFiles.create(directory, "prnb");
        File png = NotebookTransferFiles.create(directory, "png");
        assertNotEquals(first, second);
        for (File file : new File[]{first, second, png}) {
            assertTrue(file.isFile());
            assertTrue(file.getName().matches("PoolRad-notes-[A-Za-z0-9-]+\\.(prnb|png)"));
            assertEquals(directory.getCanonicalFile(), file.getCanonicalFile().getParentFile());
            assertEquals(0, file.length());
            assertNull(NotebookTransferFiles.restore(directory, file.getName()));
        }
    }

    @Test public void createsAndRestoresAPdfExport() throws Exception {
        File directory = new File(temporary.getRoot(), "pdf-cache");
        File pdf = NotebookTransferFiles.create(directory, "pdf");
        assertTrue(pdf.getName().matches("PoolRad-notes-[A-Za-z0-9-]+\\.pdf"));
        // A file with the PDF magic restores; one without does not.
        try (FileOutputStream out = new FileOutputStream(pdf)) { out.write("%PDF-1.4\nbody".getBytes()); }
        assertNotNull(NotebookTransferFiles.restore(directory, pdf.getName()));
        File bogus = write(directory, "pdf", "not a pdf at all".getBytes());
        assertNull(NotebookTransferFiles.restore(directory, bogus.getName()));
    }

    @Test public void refusesUnsupportedExtensionsAndUnusableDirectories() throws Exception {
        File directory = temporary.newFolder("cache");
        for (String extension : new String[]{null, "", ".png", "PNG", "zip", "../png", "png/other"})
            assertThrows(IOException.class, () -> NotebookTransferFiles.create(directory, extension));
        assertEquals(0, directory.list().length);
        File ordinary = temporary.newFile("not-a-directory");
        assertThrows(IOException.class, () -> NotebookTransferFiles.create(ordinary, "png"));
        assertThrows(IOException.class, () -> NotebookTransferFiles.create(null, "png"));
    }

    @Test public void restoresOnlyRecognizedConfinedBasenames() throws Exception {
        File directory = temporary.newFolder("cache");
        File png = write(directory, "png", PNG);
        File archive = write(directory, "prnb", ARCHIVE);
        assertEquals(png, NotebookTransferFiles.restore(directory, png.getName()));
        assertEquals(archive, NotebookTransferFiles.restore(directory, archive.getName()));
        for (String name : new String[]{null, "", "../" + png.getName(), png.getAbsolutePath(),
                "./" + png.getName(), "sub/" + png.getName(), "PoolRad-notes-nope.zip",
                "PoolRad-old.png", "PoolRad-notes-none.png", png.getName() + ".tmp"})
            assertNull(NotebookTransferFiles.restore(directory, name));
        assertNull(NotebookTransferFiles.restore(null, png.getName()));
        assertNull(NotebookTransferFiles.restore(temporary.newFile("plain-file"), png.getName()));
    }

    @Test public void rejectsTruncatedMismatchedUnknownAndFutureHeaders() throws Exception {
        File directory = temporary.newFolder("cache");
        for (int length = 0; length <= 8; length++) {
            for (String extension : new String[]{"png", "prnb"}) {
                File file = write(directory, extension, Arrays.copyOf(extension.equals("png") ? PNG : ARCHIVE, length));
                assertNull(NotebookTransferFiles.restore(directory, file.getName()));
            }
        }
        for (File file : new File[]{write(directory, "png", ARCHIVE), write(directory, "prnb", PNG),
                write(directory, "png", new byte[20]), write(directory, "prnb", new byte[20])})
            assertNull(NotebookTransferFiles.restore(directory, file.getName()));
        byte[] future = ARCHIVE.clone(); future[7] = 2;
        File file = write(directory, "prnb", future);
        assertNull(NotebookTransferFiles.restore(directory, file.getName()));
        // Header sniff only, not a claim that this is a complete valid archive.
        File headerAndByte = write(directory, "prnb", Arrays.copyOf(ARCHIVE, 9));
        assertEquals(headerAndByte, NotebookTransferFiles.restore(directory, headerAndByte.getName()));
    }

    @Test public void permitsExactSizeCapAndRejectsOversizeBeforeCopying() throws Exception {
        File directory = temporary.newFolder("cache");
        File file = write(directory, "prnb", ARCHIVE);
        try (RandomAccessFile sized = new RandomAccessFile(file, "rw")) {
            sized.setLength(NotebookTransferFiles.MAX_BYTES);
            assertEquals(file, NotebookTransferFiles.restore(directory, file.getName()));
            sized.setLength(NotebookTransferFiles.MAX_BYTES + 1);
        }
        assertNull(NotebookTransferFiles.restore(directory, file.getName()));
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(file, destination));
        assertEquals(0, destination.size());
    }

    @Test public void copiesBothFormatsExactlyFlushesAndLeavesDestinationOpen() throws Exception {
        File directory = temporary.newFolder("cache");
        for (String extension : new String[]{"png", "prnb"}) {
            byte[] bytes = new byte[48001];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
            System.arraycopy(extension.equals("png") ? PNG : ARCHIVE, 0, bytes, 0, 8);
            File source = write(directory, extension, bytes);
            class Destination extends ByteArrayOutputStream {
                boolean flushed, closed;
                @Override public void flush() { flushed = true; }
                @Override public void close() { closed = true; }
            }
            Destination destination = new Destination();
            NotebookTransferFiles.copy(source, destination);
            assertArrayEquals(bytes, destination.toByteArray());
            assertTrue(destination.flushed);
            assertFalse(destination.closed);
            assertTrue(source.isFile());
        }
    }

    @Test public void propagatesMissingSourceNullDestinationWriteAndFlushFailures() throws Exception {
        File directory = temporary.newFolder("cache");
        File source = write(directory, "png", PNG);
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(source, null));
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(null, new ByteArrayOutputStream()));
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(
                new File(directory, "PoolRad-notes-absent.png"), new ByteArrayOutputStream()));
        File invalid = write(directory, "png", new byte[20]);
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(invalid, new ByteArrayOutputStream()));
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(source, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Storage full"); }
        }));
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(source, new ByteArrayOutputStream() {
            @Override public void flush() throws IOException { throw new IOException("Provider disconnected"); }
        }));
        assertEquals(source, NotebookTransferFiles.restore(directory, source.getName()));
    }

    @Test public void sourceGrowthDuringCopyFailsInsteadOfSilentlyExportingChangingData() throws Exception {
        byte[] bytes = new byte[48001]; System.arraycopy(PNG, 0, bytes, 0, 8);
        File source = write(temporary.newFolder("cache"), "png", bytes);
        assertThrows(IOException.class, () -> NotebookTransferFiles.copy(source, new OutputStream() {
            boolean extended;
            @Override public void write(int value) {}
            @Override public void write(byte[] buffer, int offset, int count) throws IOException {
                if (!extended) {
                    extended = true;
                    try (FileOutputStream out = new FileOutputStream(source, true)) { out.write(1); }
                }
            }
        }));
    }

    @Test public void cleanupOnlyRemovesOwnedRegularFilesOlderThanSevenDays() throws Exception {
        File directory = temporary.newFolder("cache");
        File oldPng = write(directory, "png", PNG);
        File oldArchive = write(directory, "prnb", ARCHIVE);
        File pending = write(directory, "prnb", ARCHIVE);
        File boundary = write(directory, "png", PNG);
        File recent = write(directory, "png", PNG);
        File future = write(directory, "png", PNG);
        File unknown = new File(directory, "my-own-note.png");
        File unknownSuffix = new File(directory, "PoolRad-notes-other.zip");
        File folder = new File(directory, "PoolRad-notes-folder.png");
        assertTrue(unknown.createNewFile()); assertTrue(unknownSuffix.createNewFile()); assertTrue(folder.mkdir());
        long now = 2000000000000L;
        long expired = now - NotebookTransferFiles.RETENTION_MILLIS - 2000;
        for (File file : new File[]{oldPng, oldArchive, pending, unknown, unknownSuffix, folder})
            assertTrue(file.setLastModified(expired));
        assertTrue(boundary.setLastModified(now - NotebookTransferFiles.RETENTION_MILLIS));
        assertTrue(recent.setLastModified(now - 1000));
        assertTrue(future.setLastModified(now + 1000));
        NotebookTransferFiles.discardExpired(directory, new File(directory, "./" + pending.getName()), now);
        assertFalse(oldPng.exists()); assertFalse(oldArchive.exists());
        for (File file : new File[]{pending, boundary, recent, future, unknown, unknownSuffix, folder})
            assertTrue(file.getName(), file.exists());
        NotebookTransferFiles.discardExpired(null, null, now);
        NotebookTransferFiles.discardExpired(new File(directory, "missing"), null, now);
        NotebookTransferFiles.discardExpired(directory, null, Long.MIN_VALUE);
        assertTrue(pending.exists());
    }

    @Test public void restoreCopyAndCleanupRefuseLeafSymlinksInsideAndOutsideCache() throws Exception {
        File directory = temporary.newFolder("cache");
        File inside = write(directory, "png", PNG);
        File outside = write(temporary.newFolder("outside"), "png", PNG);
        File insideLink = new File(directory, "PoolRad-notes-inside-link.png");
        File outsideLink = new File(directory, "PoolRad-notes-outside-link.png");
        symlink(insideLink, inside); symlink(outsideLink, outside);
        long now = System.currentTimeMillis();
        assertTrue(inside.setLastModified(now - NotebookTransferFiles.RETENTION_MILLIS - 2000));
        assertTrue(outside.setLastModified(now - NotebookTransferFiles.RETENTION_MILLIS - 2000));
        for (File link : new File[]{insideLink, outsideLink}) {
            assertNull(NotebookTransferFiles.restore(directory, link.getName()));
            ByteArrayOutputStream destination = new ByteArrayOutputStream();
            assertThrows(IOException.class, () -> NotebookTransferFiles.copy(link, destination));
            assertEquals(0, destination.size());
        }
        NotebookTransferFiles.discardExpired(directory, inside, now);
        assertTrue(inside.isFile()); assertTrue(outside.isFile());
        assertTrue(Files.isSymbolicLink(insideLink.toPath()));
        assertTrue(Files.isSymbolicLink(outsideLink.toPath()));
    }

    @Test public void symlinkedCacheDirectoryIsNeverCreatedThroughReadOrCleaned() throws Exception {
        File actual = temporary.newFolder("actual-cache");
        File saved = write(actual, "prnb", ARCHIVE);
        File linked = new File(temporary.getRoot(), "linked-cache");
        symlink(linked, actual);
        assertThrows(IOException.class, () -> NotebookTransferFiles.create(linked, "png"));
        assertNull(NotebookTransferFiles.restore(linked, saved.getName()));
        long now = System.currentTimeMillis();
        assertTrue(saved.setLastModified(now - NotebookTransferFiles.RETENTION_MILLIS - 2000));
        NotebookTransferFiles.discardExpired(linked, null, now);
        assertTrue(saved.isFile());
        assertTrue(Files.isSymbolicLink(linked.toPath()));
    }

    @Test public void legitimateAliasedParentCanContainAnOrdinaryPrivateCache() throws Exception {
        File actualParent = temporary.newFolder("actual-parent");
        File aliasedParent = new File(temporary.getRoot(), "aliased-parent");
        symlink(aliasedParent, actualParent);
        File directory = new File(aliasedParent, "cache");
        File saved = write(directory, "png", PNG);
        assertEquals(saved, NotebookTransferFiles.restore(directory, saved.getName()));
        assertEquals(new File(actualParent, "cache").getCanonicalFile(), saved.getCanonicalFile().getParentFile());
    }

    private static void symlink(File link, File target) throws IOException {
        try { Files.createSymbolicLink(link.toPath(), target.toPath()); }
        catch (IOException | UnsupportedOperationException | SecurityException unsupported) {
            assumeNoException("Host filesystem does not allow symlink fixtures", unsupported);
        }
    }
}
