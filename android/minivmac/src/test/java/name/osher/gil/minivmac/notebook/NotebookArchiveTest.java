package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class NotebookArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";
    private static final String LAST_AREA = "por-mac-v11-geo-32";

    private InkNote ink() {
        return new InkNote(Arrays.asList(
                new InkNote.Stroke(false, .0125f, new float[]{0, 0, .25f, .75f, 1, 1}),
                new InkNote.Stroke(true, .04f, new float[]{.1f, .2f, .9f, .8f})));
    }

    private File file(File root, String id, String path) { return new File(new File(root, id), path); }
    private byte[] bytes(File file) throws IOException { return Files.readAllBytes(file.toPath()); }
    private byte[] export(NotebookStore store, String id) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        store.exportNotebook(id, out); return out.toByteArray();
    }

    private static final class RawEntry {
        final String path; final byte[] bytes;
        RawEntry(String path, byte[] bytes) { this.path = path; this.bytes = bytes; }
    }

    /** Independent format fixture, intentionally permitting invalid paths and duplicates. */
    private byte[] archive(String id, RawEntry... entries) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        out.writeInt(0x50524e41); out.writeInt(1); out.writeUTF(id); out.writeInt(entries.length);
        for (RawEntry entry : entries) {
            out.writeUTF(entry.path); out.writeInt(entry.bytes.length); out.write(entry.bytes);
        }
        out.write(MessageDigest.getInstance("SHA-256").digest(body.toByteArray()));
        return body.toByteArray();
    }

    private RawEntry metadata(File root, String id) throws IOException {
        return new RawEntry("notebook.bin", bytes(file(root, id, "notebook.bin")));
    }

    private byte[] legacy(String id, int tile) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(payload);
        out.writeUTF(id); out.writeUTF(AREA); out.writeByte(tile % 16); out.writeByte(tile / 16);
        out.writeInt(1); out.writeBoolean(false); out.writeFloat(.025f);
        out.writeInt(2); out.writeFloat(.125f); out.writeFloat(.25f); out.writeFloat(.75f); out.writeFloat(.875f);
        byte[] data = payload.toByteArray();
        CRC32 crc = new CRC32(); crc.update(data);
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        out = new DataOutputStream(record);
        out.writeInt(0x50524e49); out.writeInt(1); out.writeInt(data.length);
        out.write(data); out.writeLong(crc.getValue()); return record.toByteArray();
    }

    private void noStages() {
        String[] names = temporary.getRoot().list((parent, name) -> name.startsWith(".poolrad-notebook-"));
        assertNotNull(names); assertEquals(0, names.length);
    }

    @Test public void completeRoundTripPreservesIdsEveryIconBlankFlagsAndExactNoteBytes() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore store = new NotebookStore(source);
        NotebookStore.Notebook original = store.createNotebook();
        for (NoteIcon icon : NoteIcon.values()) {
            store.save(original.id(), AREA, icon.ordinal(), 2, ink(), icon);
        }
        store.save(original.id(), LAST_AREA, 15, 15, InkNote.empty(), NoteIcon.TREASURE);
        byte[] backup = export(store, original.id());
        NotebookStore restored = new NotebookStore(target);
        NotebookStore.Notebook imported = restored.importNotebook(new ByteArrayInputStream(backup));
        assertEquals(original.id(), imported.id()); assertEquals(original.label(), imported.label());
        restored = new NotebookStore(target);
        assertEquals(9, restored.listFlags(original.id(), AREA).size());
        for (NoteIcon icon : NoteIcon.values()) {
            String path = AREA + "/" + (32 + icon.ordinal()) + ".ink";
            assertArrayEquals(bytes(file(source, original.id(), path)), bytes(file(target, original.id(), path)));
            assertEquals(icon, restored.readIcon(original.id(), AREA, icon.ordinal(), 2));
        }
        assertEquals(Collections.singleton(255), restored.listFlags(original.id(), LAST_AREA));
        assertTrue(restored.read(original.id(), LAST_AREA, 15, 15).strokes().isEmpty());
        assertArrayEquals(backup, export(restored, imported.id()));
        noStages();
    }

    @Test public void legacyOriginalsBackupsAndOpaquePrototypeBytesSurviveWithoutPendingWrites() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore store = new NotebookStore(source);
        String id = store.createNotebook().id();
        store.save(id, AREA, 0, 0, InkNote.empty());
        Files.write(file(source, id, AREA + "/43.ink").toPath(), legacy(id, 43));
        Files.write(file(source, id, AREA + "/44.ink").toPath(), legacy(id, 44));
        store.save(id, AREA, 11, 2, store.read(id, AREA, 11, 2), NoteIcon.TEMPLE);
        byte[] prototype = {0, 1, 2, 3, (byte) 255};
        Files.write(file(source, id, AREA + "/map.ink").toPath(), prototype);
        Files.write(file(source, id, AREA + "/.pending-interrupted.tmp").toPath(), new byte[]{1,2});
        Files.write(file(source, id, ".pending-metadata.tmp").toPath(), new byte[]{3});
        NotebookStore restored = new NotebookStore(target);
        restored.importNotebook(new ByteArrayInputStream(export(store, id)));
        for (String name : new String[]{"43.ink", "43.ink.v1", "44.ink", "map.ink"}) {
            assertArrayEquals(bytes(file(source, id, AREA + "/" + name)), bytes(file(target, id, AREA + "/" + name)));
        }
        assertEquals(NoteIcon.TEMPLE, restored.readIcon(id, AREA, 11, 2));
        assertEquals(NoteIcon.FLAG, restored.readIcon(id, AREA, 12, 2));
        assertEquals(3, restored.listFlags(id, AREA).size());
        assertFalse(file(target, id, AREA + "/.pending-interrupted.tmp").exists());
        assertFalse(file(target, id, ".pending-metadata.tmp").exists());
        assertTrue(file(source, id, AREA + "/.pending-interrupted.tmp").exists());
        noStages();
    }

    @Test public void emptyNotebookRoundTripsAndLabelsMayChangeButNeverIdsOrNotes() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String original = first.createNotebook().id(), existing = second.createNotebook().id();
        NotebookStore.Notebook imported = second.importNotebook(new ByteArrayInputStream(export(first, original)));
        assertEquals(original, imported.id()); assertEquals("Notebook 2", imported.label());
        assertEquals("Notebook 1", second.listNotebooks().get(0).label());
        assertEquals(existing, second.listNotebooks().get(0).id());
        assertTrue(second.listFlags(original, AREA).isEmpty());
        noStages();
    }

    @Test public void uuidCollisionRejectsRatherThanReplacingNewerLocalNotes() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id();
        first.save(id, AREA, 1, 1, ink(), NoteIcon.INN);
        byte[] backup = export(first, id);
        second.importNotebook(new ByteArrayInputStream(backup));
        second.save(id, AREA, 1, 1, InkNote.empty(), NoteIcon.MONSTER);
        byte[] newer = bytes(file(target, id, AREA + "/17.ink"));
        assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(backup)));
        assertArrayEquals(newer, bytes(file(target, id, AREA + "/17.ink")));
        assertEquals(1, second.listNotebooks().size()); noStages();
    }

    @Test public void labelCollisionRewritesOnlyMetadataNotTheOriginalNoteIdentity() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(); second.createNotebook();
        first.save(id, AREA, 2, 3, ink(), NoteIcon.DISTRICT);
        byte[] original = bytes(file(source, id, AREA + "/50.ink"));
        NotebookStore.Notebook imported = second.importNotebook(new ByteArrayInputStream(export(first, id)));
        assertEquals("Notebook 2", imported.label()); assertEquals(id, imported.id());
        assertArrayEquals(original, bytes(file(target, id, AREA + "/50.ink")));
        assertEquals(NoteIcon.DISTRICT, second.readIcon(id, AREA, 2, 3)); noStages();
    }

    @Test public void truncationChecksumFutureVersionAndTrailingBytesNeverPublish() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(), existing = second.createNotebook().id();
        first.save(id, AREA, 1, 1, ink());
        byte[] good = export(first, id), checksum = good.clone(), future = good.clone();
        checksum[checksum.length - 1] ^= 1; ByteBuffer.wrap(future).putInt(4, 2);
        for (byte[] bad : new byte[][]{new byte[0], Arrays.copyOf(good, 7), Arrays.copyOf(good, good.length - 1),
                Arrays.copyOf(good, good.length / 2), checksum, future, Arrays.copyOf(good, good.length + 1)}) {
            assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(bad)));
            assertEquals(existing, second.listNotebooks().get(0).id());
            assertEquals(1, second.listNotebooks().size()); noStages();
        }
    }

    @Test public void rejectsTraversalAbsolutePathsDuplicatesAndUnknownRecords() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(); RawEntry metadata = metadata(source, id);
        for (String path : new String[]{"../escaped", "/tmp/escaped", AREA + "/../../escaped", AREA + "\\0.ink",
                AREA + "/256.ink", AREA + "/00.ink", AREA + "/nested/0.ink", "por-mac-v11-geo-33/0.ink",
                AREA + "/future.bin", AREA + "/.pending-fake.tmp", "unknown.bin"}) {
            byte[] bad = archive(id, metadata, new RawEntry(path, new byte[0]));
            assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(bad)));
            assertTrue(second.listNotebooks().isEmpty()); noStages();
        }
        byte[] duplicate = archive(id, metadata, metadata);
        assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(duplicate)));
        assertFalse(new File(temporary.getRoot(), "escaped").exists()); noStages();
    }

    @Test public void validArchiveChecksumDoesNotHideWrongIdentityOrDamagedNoteEnvelopes() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(), other = first.createNotebook().id();
        first.save(id, AREA, 1, 1, ink());
        byte[] note = bytes(file(source, id, AREA + "/17.ink"));
        byte[] damaged = note.clone(); damaged[damaged.length - 1] ^= 1;
        byte[] future = note.clone(); ByteBuffer.wrap(future).putInt(4, 4);
        for (byte[] bad : new byte[][]{
                archive(id, metadata(source, other)),
                archive(id, metadata(source, id), new RawEntry(AREA + "/18.ink", note)),
                archive(id, metadata(source, id), new RawEntry(AREA + "/17.ink", damaged)),
                archive(id, metadata(source, id), new RawEntry(AREA + "/17.ink", future)),
                archive(id, metadata(source, id), new RawEntry(AREA + "/17.ink.v1", note)),
                archive(id, new RawEntry(AREA + "/map.ink", new byte[0]))}) {
            assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(bad)));
            assertTrue(second.listNotebooks().isEmpty()); noStages();
        }
    }

    @Test public void exportAndRemovalRefuseCorruptUnknownOrLinkedLocalContent() throws Exception {
        File source = temporary.newFolder("source"); NotebookStore store = new NotebookStore(source);
        String id = store.createNotebook().id(), other = store.createNotebook().id();
        store.save(id, AREA, 1, 1, ink()); store.save(other, AREA, 1, 1, ink());
        File note = file(source, id, AREA + "/17.ink"); byte[] original = bytes(note);
        byte[] otherOriginal = bytes(file(source, other, AREA + "/17.ink"));
        Files.write(note.toPath(), new byte[]{0,1,2});
        assertThrows(IOException.class, () -> export(store, id));
        assertThrows(IOException.class, () -> store.deleteNotebook(id));
        assertArrayEquals(new byte[]{0,1,2}, bytes(note));
        Files.write(note.toPath(), original);
        File unknown = file(source, id, "future.bin"); Files.write(unknown.toPath(), new byte[]{9});
        assertThrows(IOException.class, () -> export(store, id));
        assertThrows(IOException.class, () -> store.deleteNotebook(id));
        assertTrue(unknown.delete());
        assertTrue(note.delete());
        Files.createSymbolicLink(note.toPath(), file(source, other, AREA + "/17.ink").toPath());
        assertThrows(IOException.class, () -> export(store, id));
        assertThrows(IOException.class, () -> store.deleteNotebook(id));
        assertArrayEquals(otherOriginal, bytes(file(source, other, AREA + "/17.ink")));
        assertEquals(2, store.listNotebooks().size()); noStages();
    }

    @Test public void removingOneNotebookLeavesOtherBytesAndAllowsRestoringItsBackup() throws Exception {
        File root = temporary.newFolder("store"); NotebookStore store = new NotebookStore(root);
        String removed = store.createNotebook().id(), kept = store.createNotebook().id();
        store.save(removed, AREA, 1, 1, ink(), NoteIcon.TEMPLE);
        store.save(kept, AREA, 1, 1, ink(), NoteIcon.SHOP);
        byte[] backup = export(store, removed), untouched = export(store, kept);
        Files.write(file(root, removed, AREA + "/.pending-leftover.tmp").toPath(), new byte[]{1});
        store.deleteNotebook(removed);
        assertFalse(new File(root, removed).exists());
        assertEquals(1, store.listNotebooks().size()); assertArrayEquals(untouched, export(store, kept));
        noStages();
        assertEquals(removed, store.importNotebook(new ByteArrayInputStream(backup)).id());
        assertEquals(NoteIcon.TEMPLE, store.readIcon(removed, AREA, 1, 1));
        store.deleteNotebook(removed); store.deleteNotebook(kept);
        assertTrue(store.listNotebooks().isEmpty());
        assertEquals("Notebook 1", store.createNotebook().label()); noStages();
    }

    @Test public void streamFailuresDoNotCloseCallerStreamsOrPublishPartialNotebook() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(); first.save(id, AREA, 1, 1, ink());
        byte[] good = export(first, id);
        final boolean[] outputClosed = {false}, inputClosed = {false};
        OutputStream broken = new OutputStream() {
            int count;
            @Override public void write(int value) throws IOException { if (++count > 80) throw new IOException("Full document"); }
            @Override public void close() { outputClosed[0] = true; }
        };
        assertThrows(IOException.class, () -> first.exportNotebook(id, broken));
        InputStream unavailable = new FilterInputStream(new ByteArrayInputStream(good)) {
            int count;
            @Override public int read() throws IOException { if (++count > 80) throw new IOException("Disconnected document"); return super.read(); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if ((count += length) > 80) throw new IOException("Disconnected document");
                return super.read(bytes, offset, length);
            }
            @Override public void close() { inputClosed[0] = true; }
        };
        assertThrows(IOException.class, () -> second.importNotebook(unavailable));
        assertFalse(outputClosed[0]); assertFalse(inputClosed[0]);
        assertTrue(second.listNotebooks().isEmpty()); assertArrayEquals(good, export(first, id)); noStages();
    }

    @Test public void entryAndCountLimitsRejectBeforeAllocatingOversizedPayloads() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id();
        for (boolean tooMany : new boolean[]{true, false}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x50524e41); out.writeInt(1); out.writeUTF(id);
            out.writeInt(tooMany ? NotebookArchive.MAX_ENTRIES + 1 : 1);
            if (!tooMany) { out.writeUTF(AREA + "/map.ink"); out.writeInt(NotebookArchive.MAX_ENTRY_BYTES + 1); }
            assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(bytes.toByteArray())));
            assertTrue(second.listNotebooks().isEmpty()); noStages();
        }
        first.save(id, AREA, 0, 0, InkNote.empty());
        try (RandomAccessFile huge = new RandomAccessFile(file(source, id, AREA + "/map.ink"), "rw")) {
            huge.setLength(NotebookArchive.MAX_ENTRY_BYTES + 1L);
        }
        assertThrows(IOException.class, () -> export(first, id));
    }

    @Test public void totalExportLimitIsCheckedBeforeWritingDestination() throws Exception {
        File large = temporary.newFile("large-entry");
        try (RandomAccessFile sparse = new RandomAccessFile(large, "rw")) { sparse.setLength(1024 * 1024); }
        List<NotebookArchive.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 64; i++) entries.add(new NotebookArchive.Entry(AREA + "/" + i + ".ink", large));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> NotebookArchive.write("00000000-0000-0000-0000-000000000000", entries, out));
        assertEquals(0, out.size());
    }

    @Test public void totalImportLimitStopsAnUncompressedOversizeStreamAndCleansItsStage() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(); RawEntry record = metadata(source, id);
        List<InputStream> pieces = new ArrayList<>();
        ByteArrayOutputStream header = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(header);
        out.writeInt(0x50524e41); out.writeInt(1); out.writeUTF(id); out.writeInt(65);
        out.writeUTF(record.path); out.writeInt(record.bytes.length); out.write(record.bytes);
        pieces.add(new ByteArrayInputStream(header.toByteArray()));
        byte[] block = new byte[1024 * 1024];
        for (int i = 0; i < 64; i++) {
            ByteArrayOutputStream entry = new ByteArrayOutputStream(); out = new DataOutputStream(entry);
            out.writeUTF(AREA + "/" + i + ".ink"); out.writeInt(block.length);
            pieces.add(new ByteArrayInputStream(entry.toByteArray())); pieces.add(new ByteArrayInputStream(block));
        }
        InputStream tooLarge = new SequenceInputStream(Collections.enumeration(pieces));
        IOException failure = assertThrows(IOException.class, () -> second.importNotebook(tooLarge));
        assertTrue(failure.getMessage().contains("64 MiB"));
        assertTrue(second.listNotebooks().isEmpty()); noStages();
    }

    @Test public void failedPublicationAndRetirementLeaveExistingNotebookBytesUntouched() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")));
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id(), kept = second.createNotebook().id();
        second.save(kept, AREA, 1, 1, ink());
        byte[] backup = export(first, id), original = export(second, kept);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target.toPath());
        try {
            Files.setPosixFilePermissions(target.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
            assertThrows(IOException.class, () -> second.importNotebook(new ByteArrayInputStream(backup)));
            assertThrows(IOException.class, () -> second.deleteNotebook(kept));
            assertArrayEquals(original, export(second, kept));
            assertEquals(1, second.listNotebooks().size());
        } finally { Files.setPosixFilePermissions(target.toPath(), permissions); }
        noStages();
    }

    @Test public void committedRemovalStillSucceedsWhenPrivateRetiredCleanupIsDenied() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")));
        File root = temporary.newFolder("store"); NotebookStore store = new NotebookStore(root);
        String id = store.createNotebook().id(); store.save(id, AREA, 1, 1, ink());
        File area = file(root, id, AREA);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(area.toPath());
        byte[] original = bytes(file(root, id, AREA + "/17.ink"));
        Files.setPosixFilePermissions(area.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        store.deleteNotebook(id); // Whole-directory retirement, not individual visible-note deletion.
        assertTrue(store.listNotebooks().isEmpty()); assertFalse(new File(root, id).exists());
        File[] stages = temporary.getRoot().listFiles((parent, name) -> name.startsWith(".poolrad-notebook-"));
        assertNotNull(stages); assertEquals(1, stages.length);
        File retiredArea = file(stages[0], id, AREA);
        try {
            assertArrayEquals(original, bytes(new File(retiredArea, "17.ink")));
        } finally {
            Files.setPosixFilePermissions(retiredArea.toPath(), permissions);
        }
    }
}
