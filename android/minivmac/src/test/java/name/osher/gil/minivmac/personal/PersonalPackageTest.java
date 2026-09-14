package name.osher.gil.minivmac.personal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PersonalPackageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    /** A generated checksum fixture, not any original ROM or disk payload. */
    private static byte[] rom(long signature) {
        byte[] bytes = new byte[262144]; ByteBuffer.wrap(bytes).putInt((int) signature);
        long remaining = signature;
        for (int i = 4; remaining > 0; i += 2) {
            int word = (int) Math.min(65535, remaining);
            bytes[i] = (byte) (word >>> 8); bytes[i + 1] = (byte) word; remaining -= word;
        }
        return bytes;
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private static final class Bundle implements PersonalPackage.Source {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        final List<String> opened = new ArrayList<>(), closed = new ArrayList<>();
        final int disks;
        Bundle(int disks, long signature) throws Exception {
            this.disks = disks; files.put("MacII.ROM", rom(signature));
            for (int n = 1; n <= disks; n++) {
                byte[] disk = new byte[n * 512]; Arrays.fill(disk, (byte) n);
                files.put("disk" + n + ".dsk", disk);
            }
            refreshManifest();
        }
        void refreshManifest() throws Exception {
            StringBuilder text = new StringBuilder("format=1\nmachine=macII\nrom.file=MacII.ROM\nrom.bytes=262144\nrom.sha256=")
                    .append(sha(files.get("MacII.ROM"))).append("\ndisk.count=").append(disks).append('\n');
            for (int n = 1; n <= disks; n++) {
                String name = "disk" + n + ".dsk", key = "disk." + n;
                text.append(key).append(".file=").append(name).append('\n');
                text.append(key).append(".bytes=").append(files.get(name).length).append('\n');
                text.append(key).append(".sha256=").append(sha(files.get(name))).append('\n');
            }
            setManifest(text.toString());
        }
        String manifest() { return new String(files.get("bundle.properties"), StandardCharsets.ISO_8859_1); }
        void setManifest(String value) { files.put("bundle.properties", value.getBytes(StandardCharsets.ISO_8859_1)); }
        @Override public InputStream open(String name) throws IOException {
            opened.add(name);
            byte[] bytes = files.get(name);
            if (bytes == null) throw new IOException("Missing synthetic asset");
            return new ByteArrayInputStream(bytes) {
                @Override public void close() { closed.add(name); }
            };
        }
    }

    private void noStage(File root) {
        String[] stages = root.list((parent, name) -> name.startsWith(".personal-stage-"));
        assertNotNull(stages); assertEquals(0, stages.length);
    }

    private void unpublished(File root) throws IOException {
        assertFalse(new File(root, "personal-package").exists());
        assertEquals(root, PersonalPackage.dataRoot(root)); noStage(root);
    }

    @Test public void freshInstallPublishesExactFilesAndReceiptAfterEmptyLegacyFolders() throws Exception {
        File root = temporary.newFolder("files");
        assertTrue(new File(root, "rom").mkdir()); assertTrue(new File(root, "disks").mkdir());
        Files.write(new File(root, "notes.txt").toPath(), new byte[]{42});
        Bundle bundle = new Bundle(2, 0x9779d2c4L);
        PersonalPackage.Result result = PersonalPackage.install(root, bundle);
        assertEquals(PersonalPackage.Status.INSTALLED, result.status());
        assertEquals(0x9779d2c4L, result.romChecksum());
        assertEquals(new File(root, "personal-package"), result.dataRoot());
        assertEquals(result.dataRoot(), PersonalPackage.dataRoot(root));
        assertArrayEquals(bundle.files.get("MacII.ROM"), Files.readAllBytes(new File(result.dataRoot(), "rom/MacII.ROM").toPath()));
        for (int n = 1; n <= 2; n++) assertArrayEquals(bundle.files.get("disk" + n + ".dsk"),
                Files.readAllBytes(new File(result.dataRoot(), "disks/disk" + n + ".dsk").toPath()));
        assertArrayEquals(bundle.files.get("bundle.properties"), Files.readAllBytes(new File(result.dataRoot(), "bundle.properties").toPath()));
        assertEquals(48, new File(result.dataRoot(), "install.receipt").length());
        assertArrayEquals(new byte[]{42}, Files.readAllBytes(new File(root, "notes.txt").toPath()));
        assertEquals(bundle.opened, bundle.closed); noStage(root);
    }

    @Test public void alternateCompatibleRomAndEightDisksAreSupported() throws Exception {
        File root = temporary.newFolder("files"); Bundle bundle = new Bundle(8, 0x97851db6L);
        PersonalPackage.Result installed = PersonalPackage.install(root, bundle);
        assertEquals(0x97851db6L, installed.romChecksum());
        assertEquals(8, new File(installed.dataRoot(), "disks").list().length);
        assertEquals(10, bundle.opened.size()); assertEquals(bundle.opened, bundle.closed); noStage(root);
    }

    @Test public void repeatAndApkUpdateNeverReadSourceOrReplaceChangedMediaAndNewSaves() throws Exception {
        File root = temporary.newFolder("files");
        PersonalPackage.Result installed = PersonalPackage.install(root, new Bundle(2, 0x9779d2c4L));
        File disks = new File(installed.dataRoot(), "disks"), rom = new File(installed.dataRoot(), "rom/MacII.ROM");
        Files.write(new File(disks, "disk1.dsk").toPath(), new byte[]{9,8,7});
        Files.write(new File(disks, "MyNewSave.dsk").toPath(), new byte[]{6,5});
        Files.write(rom.toPath(), new byte[]{1}); // Existing management can replace/remove media.
        PersonalPackage.Result repeated = PersonalPackage.install(root, path -> { throw new AssertionError("APK assets must not be read"); });
        assertEquals(PersonalPackage.Status.ALREADY_INSTALLED, repeated.status());
        assertEquals(installed.romChecksum(), repeated.romChecksum());
        assertArrayEquals(new byte[]{9,8,7}, Files.readAllBytes(new File(disks, "disk1.dsk").toPath()));
        assertArrayEquals(new byte[]{6,5}, Files.readAllBytes(new File(disks, "MyNewSave.dsk").toPath()));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(rom.toPath())); noStage(root);
    }

    @Test public void intentionallyRemovedRomAndDisksStayRemovedWithReceiptSelectingTheSameRoot() throws Exception {
        File root = temporary.newFolder("files");
        PersonalPackage.Result installed = PersonalPackage.install(root, new Bundle(1, 0x9779d2c4L));
        File disk = new File(installed.dataRoot(), "disks/disk1.dsk"), rom = new File(installed.dataRoot(), "rom/MacII.ROM");
        assertTrue(disk.delete()); assertTrue(rom.delete());
        PersonalPackage.Result repeated = PersonalPackage.install(root, null);
        assertEquals(PersonalPackage.Status.ALREADY_INSTALLED, repeated.status());
        assertEquals(0x9779d2c4L, repeated.romChecksum());
        assertEquals(installed.dataRoot(), PersonalPackage.dataRoot(root));
        assertFalse(disk.exists()); assertFalse(rom.exists());
    }

    @Test public void anyLegacyEntryIncludingUnknownOrDanglingLinksSkipsAllSourceReads() throws Exception {
        for (String parent : new String[]{"rom", "disks"}) {
            File root = temporary.newFolder(); File folder = new File(root, parent); assertTrue(folder.mkdir());
            File existing = new File(folder, "keep-me"); Files.write(existing.toPath(), new byte[]{1,2});
            PersonalPackage.Result result = PersonalPackage.install(root, path -> { throw new AssertionError("No source reads"); });
            assertEquals(PersonalPackage.Status.EXISTING_USER_FILES, result.status());
            assertEquals(-1, result.romChecksum()); assertEquals(root, result.dataRoot());
            assertArrayEquals(new byte[]{1,2}, Files.readAllBytes(existing.toPath())); unpublished(root);
        }
        File root = temporary.newFolder();
        Files.createSymbolicLink(new File(root, "rom").toPath(), new File(root, "missing").toPath());
        assertEquals(PersonalPackage.Status.EXISTING_USER_FILES, PersonalPackage.install(root, null).status());
        assertTrue(Files.isSymbolicLink(new File(root, "rom").toPath()));
    }

    @Test public void occupiedPersonalTargetOrDanglingSymlinkIsAnErrorNeverAnOverwrite() throws Exception {
        File root = temporary.newFolder(), target = new File(root, "personal-package");
        Files.write(target.toPath(), new byte[]{7});
        assertThrows(IOException.class, () -> PersonalPackage.install(root, null));
        assertThrows(IOException.class, () -> PersonalPackage.dataRoot(root));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(target.toPath()));
        assertTrue(target.delete());
        Files.createSymbolicLink(target.toPath(), new File(root, "missing").toPath());
        assertThrows(IOException.class, () -> PersonalPackage.install(root, null));
        assertTrue(Files.isSymbolicLink(target.toPath())); noStage(root);
    }

    @Test public void malformedUnknownAndUnsafeManifestsRejectBeforeAnyPayloadReads() throws Exception {
        Bundle template = new Bundle(1, 0x9779d2c4L); String good = template.manifest();
        String[][] replacements = {{"format=1", "format=2"}, {"machine=macII", "machine=macPlus"},
                {"rom.file=MacII.ROM", "rom.file=../MacII.ROM"}, {"rom.bytes=262144", "rom.bytes=131072"},
                {"disk.count=1", "disk.count=0"}, {"disk.count=1", "disk.count=9"},
                {"disk.1.file=disk1.dsk", "disk.1.file=/tmp/disk1.dsk"},
                {"disk.1.bytes=512", "disk.1.bytes=0"}, {"disk.1.bytes=512", "disk.1.bytes=513"},
                {"disk.1.bytes=512", "disk.1.bytes=134218240"},
                {"rom.sha256=", "rom.sha256=not-hex"}, {"format=1", "format=1\nformat=1"}};
        for (String[] change : replacements) {
            File root = temporary.newFolder(); Bundle bad = new Bundle(1, 0x9779d2c4L);
            bad.setManifest(good.replace(change[0], change[1]));
            assertThrows(IOException.class, () -> PersonalPackage.install(root, bad));
            assertEquals(Arrays.asList("bundle.properties"), bad.opened);
            assertEquals(bad.opened, bad.closed); unpublished(root);
        }
        for (String invalid : new String[]{good + "future.option=true\n", good.replace("disk.1.bytes=512\n", ""),
                new String(new char[8193]).replace('\0', '#'), "format=\\uZZZZ\n"}) {
            File root = temporary.newFolder(); Bundle bad = new Bundle(1, 0x9779d2c4L); bad.setManifest(invalid);
            assertThrows(IOException.class, () -> PersonalPackage.install(root, bad)); unpublished(root);
        }
    }

    @Test public void combinedDiskLimitIs256MiBWithRomAdditionalButLargerTotalsReject() throws Exception {
        File root = temporary.newFolder(); Bundle tooLarge = new Bundle(3, 0x9779d2c4L);
        tooLarge.setManifest(tooLarge.manifest().replace(".bytes=512", ".bytes=134217728")
                .replace(".bytes=1024", ".bytes=134217728").replace(".bytes=1536", ".bytes=512"));
        assertThrows(IOException.class, () -> PersonalPackage.install(root, tooLarge));
        assertEquals(Arrays.asList("bundle.properties"), tooLarge.opened); unpublished(root);
        Bundle exact = new Bundle(2, 0x9779d2c4L);
        exact.setManifest(exact.manifest().replace(".bytes=512", ".bytes=134217728").replace(".bytes=1024", ".bytes=134217728"));
        IOException absentPayload = assertThrows(IOException.class, () -> PersonalPackage.install(root, exact));
        assertTrue(absentPayload.getMessage().contains("Truncated")); // Manifest accepted; tiny fixture is not a 128 MiB disk.
        assertTrue(exact.opened.contains("disk1.dsk")); unpublished(root);
    }

    @Test public void wrongShaTruncationAndTrailingBytesNeverPublishOrLeakStreams() throws Exception {
        for (String filename : new String[]{"MacII.ROM", "disk1.dsk"}) {
            for (int change = 0; change < 3; change++) {
                File root = temporary.newFolder(); Bundle bad = new Bundle(1, 0x9779d2c4L);
                byte[] original = bad.files.get(filename);
                byte[] altered = Arrays.copyOf(original, change == 0 ? original.length : original.length + (change == 1 ? -1 : 1));
                if (change == 0) altered[altered.length - 1] ^= 1;
                bad.files.put(filename, altered);
                assertThrows(IOException.class, () -> PersonalPackage.install(root, bad));
                assertEquals(bad.opened, bad.closed); unpublished(root);
            }
        }
    }

    @Test public void romSignatureAndFullWordChecksumAreVerifiedEvenWhenShaMatches() throws Exception {
        for (boolean unsupported : new boolean[]{true, false}) {
            File root = temporary.newFolder(); Bundle bad = new Bundle(1, 0x9779d2c4L);
            byte[] bytes = bad.files.get("MacII.ROM");
            if (unsupported) ByteBuffer.wrap(bytes).putInt(0x12345678);
            else bytes[bytes.length - 1] = 1; // Prefix reached expected sum, but complete ROM does not.
            bad.refreshManifest();
            assertThrows(IOException.class, () -> PersonalPackage.install(root, bad));
            assertEquals(bad.opened, bad.closed); unpublished(root);
        }
    }

    @Test public void partialStageIsNeverSelectedAndSourceFailureClosesEveryOpenedStream() throws Exception {
        File root = temporary.newFolder(); Bundle bundle = new Bundle(2, 0x9779d2c4L);
        PersonalPackage.Source failing = path -> {
            if (!path.equals("disk2.dsk")) return bundle.open(path);
            assertEquals(root, PersonalPackage.dataRoot(root));
            assertFalse(new File(root, "personal-package").exists());
            assertEquals(1, root.list((parent, name) -> name.startsWith(".personal-stage-")).length);
            return new FilterInputStream(bundle.open(path)) {
                @Override public int read(byte[] bytes, int offset, int length) throws IOException { throw new IOException("Source disconnected"); }
            };
        };
        assertThrows(IOException.class, () -> PersonalPackage.install(root, failing));
        assertEquals(bundle.opened, bundle.closed); unpublished(root);
    }

    @Test public void legacyImportArrivingDuringCopyWinsWithoutAnyOverwrite() throws Exception {
        File root = temporary.newFolder(); Bundle bundle = new Bundle(1, 0x9779d2c4L);
        File userDisk = new File(root, "disks/my-save.dsk");
        PersonalPackage.Result result = PersonalPackage.install(root, path -> {
            if (path.equals("disk1.dsk")) {
                assertTrue(userDisk.getParentFile().mkdir()); Files.write(userDisk.toPath(), new byte[]{4,5});
            }
            return bundle.open(path);
        });
        assertEquals(PersonalPackage.Status.EXISTING_USER_FILES, result.status());
        assertArrayEquals(new byte[]{4,5}, Files.readAllBytes(userDisk.toPath()));
        assertEquals(bundle.opened, bundle.closed); unpublished(root);
    }

    @Test public void publicationCollisionPreservesTheNewTargetAndCleansOnlyItsOwnStage() throws Exception {
        File root = temporary.newFolder(); Bundle bundle = new Bundle(1, 0x9779d2c4L);
        File collision = new File(root, "personal-package");
        File oldStage = new File(root, ".personal-stage-old-interrupted"); assertTrue(oldStage.mkdir());
        File oldBytes = new File(oldStage, "keep-me"); Files.write(oldBytes.toPath(), new byte[]{3});
        assertThrows(IOException.class, () -> PersonalPackage.install(root, path -> {
            if (path.equals("disk1.dsk")) Files.write(collision.toPath(), new byte[]{1,2});
            return bundle.open(path);
        }));
        assertArrayEquals(new byte[]{1,2}, Files.readAllBytes(collision.toPath()));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(oldBytes.toPath()));
        assertArrayEquals(new String[]{oldStage.getName()}, root.list((parent, name) -> name.startsWith(".personal-stage-")));
    }

    @Test public void damagedReceiptOrChangedManifestIsExplicitRecoveryErrorNotLegacyFallback() throws Exception {
        for (boolean manifest : new boolean[]{true, false}) {
            File root = temporary.newFolder(); PersonalPackage.Result original = PersonalPackage.install(root, new Bundle(1, 0x9779d2c4L));
            File altered = new File(original.dataRoot(), manifest ? "bundle.properties" : "install.receipt");
            byte[] bytes = Files.readAllBytes(altered.toPath()); bytes[bytes.length - 1] ^= 1;
            Files.write(altered.toPath(), bytes);
            assertThrows(IOException.class, () -> PersonalPackage.dataRoot(root));
            assertThrows(IOException.class, () -> PersonalPackage.install(root, path -> { throw new AssertionError("Never reinstall over invalid receipt"); }));
            assertArrayEquals(bytes, Files.readAllBytes(altered.toPath())); noStage(root);
        }
    }

    @Test public void rootLookupDoesNotWaitForAnOngoingInstallAndSeesOnlyAtomicPublication() throws Exception {
        File root = temporary.newFolder(); Bundle bundle = new Bundle(1, 0x9779d2c4L);
        CountDownLatch copying = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        Future<PersonalPackage.Result> installation = threads.submit(() -> PersonalPackage.install(root, path -> {
            if (path.equals("disk1.dsk")) {
                copying.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test copy was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IOException("Test copy interrupted", interrupted);
                }
            }
            return bundle.open(path);
        }));
        try {
            assertTrue(copying.await(5, TimeUnit.SECONDS));
            // Previously this waited on install's class monitor, blocking Activity recreation.
            Future<File> lookup = threads.submit(() -> PersonalPackage.dataRoot(root));
            assertEquals(root, lookup.get(1, TimeUnit.SECONDS));
            assertFalse(installation.isDone());
            assertFalse(new File(root, "personal-package").exists());
            release.countDown();
            PersonalPackage.Result installed = installation.get(5, TimeUnit.SECONDS);
            assertEquals(PersonalPackage.Status.INSTALLED, installed.status());
            assertEquals(installed.dataRoot(), PersonalPackage.dataRoot(root));
            assertEquals(bundle.opened, bundle.closed); noStage(root);
        } finally {
            release.countDown();
            threads.shutdown();
            if (!threads.awaitTermination(5, TimeUnit.SECONDS)) threads.shutdownNow();
        }
    }
}
