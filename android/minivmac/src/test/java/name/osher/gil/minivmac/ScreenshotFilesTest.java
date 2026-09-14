package name.osher.gil.minivmac;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ScreenshotFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};

    private File write(File directory, byte[] bytes) throws Exception {
        File file = ScreenshotFiles.create(directory);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
        return file;
    }

    @Test public void pendingPickerRestoresOnlyItsPrivateCompletedPng() throws Exception {
        File directory = temporary.newFolder("screenshots");
        File png = write(directory, PNG);
        assertEquals(png, ScreenshotFiles.restore(directory, png.getName()));
        assertNull(ScreenshotFiles.restore(directory, null));
        assertNull(ScreenshotFiles.restore(directory, "../" + png.getName()));
        assertNull(ScreenshotFiles.restore(directory, png.getAbsolutePath()));
        assertNull(ScreenshotFiles.restore(directory, "PoolRad-missing.png"));
        File unfinished = write(directory, Arrays.copyOf(PNG, 8));
        assertNull(ScreenshotFiles.restore(directory, unfinished.getName()));
        File invalid = write(directory, new byte[20]);
        assertNull(ScreenshotFiles.restore(directory, invalid.getName()));
    }

    @Test public void saveCopiesAllBytes() throws Exception {
        byte[] bytes = new byte[48001];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
        File source = write(temporary.newFolder("screenshots"), bytes);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        ScreenshotFiles.copy(source, destination);
        assertArrayEquals(bytes, destination.toByteArray());
    }

    @Test public void savePropagatesProviderFailuresInsteadOfReportingSuccess() throws Exception {
        File source = write(temporary.newFolder("screenshots"), PNG);
        assertThrows(IOException.class, () -> ScreenshotFiles.copy(source, null));
        assertThrows(IOException.class, () -> ScreenshotFiles.copy(source, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Storage full"); }
        }));
        assertThrows(IOException.class, () -> ScreenshotFiles.copy(source, new ByteArrayOutputStream() {
            @Override public void flush() throws IOException { throw new IOException("Provider disconnected"); }
        }));
    }

    @Test public void cleanupKeepsRecentSharesPendingPickerAndUnrelatedFiles() throws Exception {
        File directory = temporary.newFolder("screenshots");
        File old = write(directory, PNG);
        File pending = write(directory, PNG);
        File recent = write(directory, PNG);
        File unrelated = new File(directory, "user.png");
        assertTrue(unrelated.createNewFile());
        long now = System.currentTimeMillis();
        long expired = now - ScreenshotFiles.RETENTION_MILLIS - 1000;
        assertTrue(old.setLastModified(expired));
        assertTrue(pending.setLastModified(expired));
        assertTrue(unrelated.setLastModified(expired));
        ScreenshotFiles.discardExpired(directory, pending, now);
        assertFalse(old.exists());
        assertTrue(pending.exists());
        assertTrue(recent.exists());
        assertTrue(unrelated.exists());
    }
}
