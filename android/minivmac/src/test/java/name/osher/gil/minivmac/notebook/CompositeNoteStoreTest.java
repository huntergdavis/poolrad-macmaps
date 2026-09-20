package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class CompositeNoteStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";
    private static final String OTHER_AREA = "por-mac-v11-geo-20";

    private InkNote ink() {
        return new InkNote(Arrays.asList(
                new InkNote.Stroke(false, .0125f, new float[]{0, 0, .25f, .75f, .5f, .5f, 1, 1}),
                new InkNote.Stroke(true, .04f, new float[]{.1f, .2f, .9f, .8f}),
                new InkNote.Stroke(false, .02f, new float[]{.375f, .625f})));
    }

    private File noteFile(File root, String book, String area, int x, int y) {
        return new File(new File(new File(root, book), area), (y * 16 + x) + ".ink");
    }

    private File backup(File note) { return new File(note.getParentFile(), note.getName() + ".v1"); }

    private byte[] payload(String book, String area, int x, int y, InkNote note, String icon)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(book); out.writeUTF(area); out.writeByte(x); out.writeByte(y);
            if (icon != null) out.writeUTF(icon);
            out.writeInt(note.strokes().size());
            for (InkNote.Stroke stroke : note.strokes()) {
                out.writeBoolean(stroke.eraser()); out.writeFloat(stroke.width());
                out.writeInt(stroke.pointCount());
                for (float point : stroke.points()) out.writeFloat(point);
            }
        }
        return bytes.toByteArray();
    }

    /** Independent fixture writer for the previously shipped binary layout. */
    private byte[] envelope(int version, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            CRC32 crc = new CRC32(); crc.update(payload);
            out.writeInt(0x50524e49); out.writeInt(version); out.writeInt(payload.length);
            out.write(payload); out.writeLong(crc.getValue());
        }
        return bytes.toByteArray();
    }

    private File fixture(File root, String book, int x, int y, int version, String icon)
            throws IOException {
        File file = noteFile(root, book, AREA, x, y);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), envelope(version, payload(book, AREA, x, y, ink(), icon)));
        return file;
    }

    private void assertInk(InkNote expected, InkNote actual) {
        assertEquals(expected.strokes().size(), actual.strokes().size());
        for (int i = 0; i < expected.strokes().size(); i++) {
            InkNote.Stroke first = expected.strokes().get(i), second = actual.strokes().get(i);
            assertEquals(first.eraser(), second.eraser());
            assertEquals(first.width(), second.width(), 0);
            assertArrayEquals(first.points(), second.points(), 0);
        }
    }

    private void assertWidened(InkNote original, InkNote actual) {
        assertEquals(original.strokes().size(), actual.strokes().size());
        for (int i = 0; i < original.strokes().size(); i++) {
            InkNote.Stroke before = original.strokes().get(i), after = actual.strokes().get(i);
            assertEquals(before.eraser(), after.eraser());
            assertEquals(before.width(), after.width(), 0);
            float[] points = before.points(), moved = after.points();
            assertEquals(points.length, moved.length);
            for (int p = 0; p < points.length; p += 2) {
                assertEquals(.5f + .5f * points[p], moved[p], 0);
                assertEquals(points[p + 1], moved[p + 1], 0);
                assertTrue(moved[p] >= .5f);
            }
        }
    }

    @Test public void readsLegacySheetsIntoRightHalfWithoutModifyingAnyFiles() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 11, 2, 1, null);
        byte[] original = Files.readAllBytes(note.toPath());
        assertWidened(ink(), store.read(book, AREA, 11, 2));
        assertWidened(ink(), new NotebookStore(root).read(book, AREA, 11, 2));
        assertEquals(NoteIcon.FLAG, store.readIcon(book, AREA, 11, 2));
        assertEquals(Collections.singletonMap(43, NoteIcon.FLAG), store.listFlagIcons(book, AREA));
        assertArrayEquals(original, Files.readAllBytes(note.toPath()));
        assertArrayEquals(new String[]{"43.ink"}, note.getParentFile().list());
    }

    @Test public void oldCompositeNotesRemainPlainAndByteExactUntilSaved() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File file = fixture(root, book, 3, 4, 2, NoteIcon.TREASURE.id());
        byte[] original = Files.readAllBytes(file.toPath());
        InkNote note = store.read(book, AREA, 3, 4);
        assertEquals(NoteTemplate.PLAIN, note.template());
        assertInk(ink(), note);
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
        store.save(book, AREA, 3, 4, note.withTemplate(NoteTemplate.GRID));
        InkNote reopened = new NotebookStore(root).read(book, AREA, 3, 4);
        assertEquals(NoteTemplate.GRID, reopened.template());
        assertEquals(NoteIcon.TREASURE, store.readIcon(book, AREA, 3, 4));
        assertInk(note, reopened);
    }

    @Test public void firstLegacySaveWritesCurrentVersionAndRetainsByteExactOriginalBackup() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 11, 2, 1, null);
        byte[] original = Files.readAllBytes(note.toPath());
        File metadata = new File(new File(root, book), "notebook.bin");
        byte[] originalMetadata = Files.readAllBytes(metadata.toPath());
        InkNote migrated = store.read(book, AREA, 11, 2);
        store.save(book, AREA, 11, 2, migrated, NoteIcon.TEMPLE);
        assertArrayEquals(original, Files.readAllBytes(backup(note).toPath()));
        try (RandomAccessFile record = new RandomAccessFile(note, "r")) {
            assertEquals(0x50524e49, record.readInt()); assertEquals(3, record.readInt());
        }
        try (RandomAccessFile record = new RandomAccessFile(metadata, "r")) {
            assertEquals(0x50524e42, record.readInt()); assertEquals(1, record.readInt());
        }
        NotebookStore reopened = new NotebookStore(root);
        assertInk(migrated, reopened.read(book, AREA, 11, 2));
        assertEquals(NoteIcon.TEMPLE, reopened.readIcon(book, AREA, 11, 2));
        reopened.save(book, AREA, 11, 2, migrated); // Neither double-shift nor reset icon.
        assertInk(migrated, reopened.read(book, AREA, 11, 2));
        assertEquals(NoteIcon.TEMPLE, reopened.readIcon(book, AREA, 11, 2));
        assertArrayEquals(original, Files.readAllBytes(backup(note).toPath()));
        assertArrayEquals(originalMetadata, Files.readAllBytes(metadata.toPath()));
        assertEquals(Collections.singleton(43), reopened.listFlags(book, AREA));
    }

    @Test public void currentVersionKeepsBothCompositeHalvesAndEveryIconAcrossRestart() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        int x = 0;
        for (NoteIcon icon : NoteIcon.values()) store.save(book, AREA, x++, 4, ink(), icon);
        NotebookStore reopened = new NotebookStore(root);
        Map<Integer, NoteIcon> symbols = reopened.listFlagIcons(book, AREA);
        assertEquals(9, symbols.size());
        x = 0;
        for (NoteIcon icon : NoteIcon.values()) {
            assertInk(ink(), reopened.read(book, AREA, x, 4));
            assertEquals(icon, reopened.readIcon(book, AREA, x, 4));
            assertEquals(icon, symbols.get(64 + x));
            assertFalse(backup(noteFile(root, book, AREA, x++, 4)).exists());
        }
        assertArrayEquals(new Integer[]{64,65,66,67,68,69,70,71,72}, symbols.keySet().toArray(new Integer[0]));
        assertThrows(UnsupportedOperationException.class, () -> symbols.put(100, NoteIcon.FLAG));
        assertThrows(UnsupportedOperationException.class, () -> reopened.listFlags(book, AREA).clear());
    }

    @Test public void iconOnlyChangesAndCompatibilitySavesPreserveInkAndSelectedSymbol() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        store.save(book, AREA, 1, 2, ink());
        assertEquals(NoteIcon.FLAG, store.readIcon(book, AREA, 1, 2));
        store.save(book, AREA, 1, 2, store.read(book, AREA, 1, 2), NoteIcon.SMITHY);
        assertInk(ink(), store.read(book, AREA, 1, 2));
        store.save(book, AREA, 1, 2, InkNote.empty());
        assertEquals(NoteIcon.SMITHY, store.readIcon(book, AREA, 1, 2));
        assertTrue(store.read(book, AREA, 1, 2).strokes().isEmpty());
        assertEquals(Collections.singletonMap(33, NoteIcon.SMITHY), store.listFlagIcons(book, AREA));
    }

    @Test public void symbolsAndCompositeInkRemainIsolatedByRunAreaAndTile() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String first = store.createNotebook().id(), second = store.createNotebook().id();
        store.save(first, AREA, 2, 3, ink(), NoteIcon.MONSTER);
        store.save(first, OTHER_AREA, 2, 3, InkNote.empty(), NoteIcon.INN);
        store.save(second, AREA, 2, 3, InkNote.empty(), NoteIcon.SHOP);
        assertEquals(NoteIcon.MONSTER, store.readIcon(first, AREA, 2, 3));
        assertEquals(NoteIcon.INN, store.readIcon(first, OTHER_AREA, 2, 3));
        assertEquals(NoteIcon.SHOP, store.readIcon(second, AREA, 2, 3));
        assertEquals(NoteIcon.FLAG, store.readIcon(first, AREA, 3, 3));
        assertInk(ink(), store.read(first, AREA, 2, 3));
        assertTrue(store.read(second, AREA, 2, 3).strokes().isEmpty());
    }

    @Test public void missingNotesDoNotCreateAnAreaWhileNullSymbolsAreRejected() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        assertEquals(NoteIcon.FLAG, store.readIcon(book, AREA, 0, 0));
        assertTrue(store.read(book, AREA, 0, 0).strokes().isEmpty());
        assertTrue(store.listFlagIcons(book, AREA).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.save(book, AREA, 0, 0, ink(), null));
        assertThrows(IllegalArgumentException.class, () -> store.save(book, AREA, 0, 0, null, NoteIcon.FLAG));
        assertFalse(noteFile(root, book, AREA, 0, 0).getParentFile().exists());
    }

    @Test public void invalidVersionTwoIconsFailEveryReadWriteAndDeleteWithoutChangingBytes() throws Exception {
        for (String bad : new String[]{"", "FLAG", "hidden_wall", "future-icon"}) {
            File root = temporary.newFolder();
            NotebookStore store = new NotebookStore(root);
            String book = store.createNotebook().id();
            File note = fixture(root, book, 1, 1, 2, bad);
            byte[] original = Files.readAllBytes(note.toPath());
            assertThrows(IOException.class, () -> store.read(book, AREA, 1, 1));
            assertThrows(IOException.class, () -> store.readIcon(book, AREA, 1, 1));
            assertThrows(IOException.class, () -> store.listFlagIcons(book, AREA));
            assertThrows(IOException.class, () -> store.save(book, AREA, 1, 1, ink()));
            assertThrows(IOException.class, () -> store.save(book, AREA, 1, 1, ink(), NoteIcon.FLAG));
            assertThrows(IOException.class, () -> store.delete(book, AREA, 1, 1));
            assertArrayEquals(original, Files.readAllBytes(note.toPath()));
            assertFalse(backup(note).exists());
        }
    }

    @Test public void legacyInvalidCoordinatesAreRejectedBeforeTheyCouldBecomeValidAfterShifting() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 1, 1, 1, null);
        byte[] data = payload(book, AREA, 1, 1, ink(), null);
        int firstCoordinate = 2 + book.length() + 2 + AREA.length() + 2 + 4 + 1 + 4 + 4;
        ByteBuffer.wrap(data).putFloat(firstCoordinate, -.25f);
        byte[] invalid = envelope(1, data); // Valid checksum, invalid original coordinate.
        Files.write(note.toPath(), invalid);
        assertThrows(IOException.class, () -> store.read(book, AREA, 1, 1));
        assertThrows(IOException.class, () -> store.save(book, AREA, 1, 1, ink(), NoteIcon.TREASURE));
        assertArrayEquals(invalid, Files.readAllBytes(note.toPath()));
        assertFalse(backup(note).exists());
    }

    @Test public void corruptOrFutureRecordsNeverMigrateOrCreateBackups() throws Exception {
        for (int corruption = 0; corruption < 3; corruption++) {
            File root = temporary.newFolder();
            NotebookStore store = new NotebookStore(root);
            String book = store.createNotebook().id();
            File note = fixture(root, book, 0, 0, 1, null);
            try (RandomAccessFile file = new RandomAccessFile(note, "rw")) {
                if (corruption == 0) file.setLength(10);
                if (corruption == 1) { file.seek(4); file.writeInt(99); }
                if (corruption == 2) { file.seek(note.length() - 8); file.writeLong(-1); }
            }
            byte[] original = Files.readAllBytes(note.toPath());
            assertThrows(IOException.class, () -> store.readIcon(book, AREA, 0, 0));
            assertThrows(IOException.class, () -> store.save(book, AREA, 0, 0, ink(), NoteIcon.FLAG));
            assertArrayEquals(original, Files.readAllBytes(note.toPath()));
            assertFalse(backup(note).exists());
        }
    }

    @Test public void conflictingBackupPreventsLegacyReplacementButMatchingBackupAllowsRetry() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 2, 3, 1, null);
        byte[] original = Files.readAllBytes(note.toPath());
        byte[] conflicting = envelope(1, payload(book, AREA, 2, 3, InkNote.empty(), null));
        Files.write(backup(note).toPath(), conflicting);
        assertThrows(IOException.class, () -> store.save(book, AREA, 2, 3, store.read(book, AREA, 2, 3), NoteIcon.INN));
        assertArrayEquals(original, Files.readAllBytes(note.toPath()));
        assertArrayEquals(conflicting, Files.readAllBytes(backup(note).toPath()));
        Files.write(backup(note).toPath(), original);
        store.save(book, AREA, 2, 3, store.read(book, AREA, 2, 3), NoteIcon.INN);
        assertWidened(ink(), store.read(book, AREA, 2, 3));
        assertEquals(NoteIcon.INN, store.readIcon(book, AREA, 2, 3));
        assertArrayEquals(original, Files.readAllBytes(backup(note).toPath()));
    }

    @Test public void failedBackupCannotReplaceTheOriginalLegacyNote() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 2, 3, 1, null);
        byte[] original = Files.readAllBytes(note.toPath());
        assertTrue(backup(note).mkdir());
        assertThrows(IOException.class, () -> store.save(book, AREA, 2, 3, store.read(book, AREA, 2, 3), NoteIcon.TEMPLE));
        assertArrayEquals(original, Files.readAllBytes(note.toPath()));
        assertWidened(ink(), store.read(book, AREA, 2, 3));
    }

    @Test public void deliberateDeletionRemovesSelectedCompositeAndItsLegacyBackupOnly() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File note = fixture(root, book, 2, 3, 1, null);
        store.save(book, AREA, 2, 3, store.read(book, AREA, 2, 3), NoteIcon.DISTRICT);
        store.save(book, AREA, 3, 3, ink(), NoteIcon.HIDDEN_WALL);
        store.delete(book, AREA, 2, 3);
        assertFalse(note.exists()); assertFalse(backup(note).exists());
        assertEquals(Collections.singletonMap(51, NoteIcon.HIDDEN_WALL), store.listFlagIcons(book, AREA));
        assertInk(ink(), store.read(book, AREA, 3, 3));
    }

    @Test public void onlyExactUnreleasedMapInkFilenameAndValidBackupNamesAreIgnored() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 0, 0, ink(), NoteIcon.SHOP);
        File folder = noteFile(root, book, AREA, 0, 0).getParentFile();
        Files.write(new File(folder, "map.ink").toPath(), new byte[]{1,2,3});
        assertEquals(Collections.singletonMap(0, NoteIcon.SHOP), store.listFlagIcons(book, AREA));
        File unknown = new File(folder, "map-other.ink");
        assertTrue(unknown.createNewFile());
        assertThrows(IOException.class, () -> store.listFlagIcons(book, AREA));
        assertTrue(unknown.delete());
        assertTrue(new File(folder, "999.ink.v1").createNewFile());
        assertThrows(IOException.class, () -> store.listFlagIcons(book, AREA));
    }

    @Test public void failedAtomicCompositeWritePreservesInkAndIconTogether() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, ink(), NoteIcon.TEMPLE);
        File note = noteFile(root, book, AREA, 2, 3), folder = note.getParentFile();
        byte[] original = Files.readAllBytes(note.toPath());
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(folder.toPath());
        try {
            Files.setPosixFilePermissions(folder.toPath(), EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
            assumeFalse("Root bypasses directory write restrictions", folder.canWrite());
            assertThrows(IOException.class, () -> store.save(book, AREA, 2, 3, InkNote.empty(), NoteIcon.MONSTER));
            assertArrayEquals(original, Files.readAllBytes(note.toPath()));
            assertInk(ink(), store.read(book, AREA, 2, 3));
            assertEquals(NoteIcon.TEMPLE, store.readIcon(book, AREA, 2, 3));
        } finally { Files.setPosixFilePermissions(folder.toPath(), permissions); }
    }
}
