package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class ExplorationStoreTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String AREA="por-mac-v11-geo-0", OTHER="por-mac-v11-geo-32";
    private File file(File root,String book,String area) { return new File(new File(new File(root,book),area),"exploration.bin"); }
    private ExplorationTrail trail() { return ExplorationTrail.empty().record(17,-1).record(18,17).record(17,18).record(100,-1); }
    private byte[] bytes(File file) throws IOException { return Files.readAllBytes(file.toPath()); }

    private byte[] raw(String book,String area,byte[] visited,int count,int... values) throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);
        out.writeUTF(book);out.writeUTF(area);out.write(visited);out.writeShort(count);
        for(int i=0;i<values.length;i+=2) { out.writeShort(values[i]);out.writeByte(values[i+1]); }
        byte[] payload=body.toByteArray();CRC32 crc=new CRC32();crc.update(payload);
        ByteArrayOutputStream record=new ByteArrayOutputStream();out=new DataOutputStream(record);
        out.writeInt(0x50524558);out.writeInt(1);out.writeInt(payload.length);out.write(payload);out.writeLong(crc.getValue());
        return record.toByteArray();
    }

    @Test public void missingHistoryIsEmptyWithoutCreatingAreaOrFlag() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        assertEquals(ExplorationTrail.empty(),store.loadExploration(book,AREA));
        assertFalse(file(root,book,AREA).getParentFile().exists());
        assertTrue(store.listFlags(book,AREA).isEmpty());
    }

    @Test public void exactHistorySurvivesStoreRecreationButRestartDoesNotRestoreContinuity() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.saveExploration(book,AREA,trail());
        ExplorationTrail restored=new NotebookStore(root).loadExploration(book,AREA);
        assertEquals(trail(),restored);assertEquals(3,restored.visitedCount());
        ExplorationTrail resumed=restored.record(101,-1);
        assertEquals(-1,resumed.steps.get(resumed.steps.size()-1).from);
        assertEquals(100,restored.steps.get(restored.steps.size()-1).to);
        assertTrue(file(root,book,AREA).length()<=NotebookStore.MAX_EXPLORATION_BYTES+20);
        assertArrayEquals(new String[]{"exploration.bin"},file(root,book,AREA).getParentFile().list());
    }

    @Test public void campaignAndAreaIsolationCoexistsWithUnchangedFlagBytes() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);
        String book=store.createNotebook().id(),otherBook=store.createNotebook().id();
        store.save(book,AREA,1,1,InkNote.empty(),NoteIcon.TEMPLE);
        File flag=new File(file(root,book,AREA).getParentFile(),"17.ink");byte[] original=bytes(flag);
        store.saveExploration(book,AREA,trail());
        assertEquals(ExplorationTrail.empty(),store.loadExploration(otherBook,AREA));
        assertEquals(ExplorationTrail.empty(),store.loadExploration(book,OTHER));
        assertEquals(Collections.singleton(17),store.listFlags(book,AREA));
        assertEquals(NoteIcon.TEMPLE,store.readIcon(book,AREA,1,1));assertArrayEquals(original,bytes(flag));
        store.saveExploration(book,OTHER,ExplorationTrail.empty().record(255,-1));
        assertEquals(trail(),store.loadExploration(book,AREA));
        assertTrue(store.loadExploration(book,OTHER).visited(255));
    }

    @Test public void clearTrailKeepsCoverageWhileExplicitResetAffectsOnlyExploration() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.save(book,AREA,1,1,InkNote.empty());store.saveExploration(book,AREA,trail());
        store.saveExploration(book,AREA,trail().clearTrail());
        ExplorationTrail cleared=new NotebookStore(root).loadExploration(book,AREA);
        assertTrue(cleared.steps.isEmpty());assertEquals(3,cleared.visitedCount());
        store.saveExploration(book,AREA,ExplorationTrail.empty());
        assertEquals(ExplorationTrail.empty(),store.loadExploration(book,AREA));
        assertEquals(Collections.singleton(17),store.listFlags(book,AREA));
    }

    @Test public void boundsAndIdentityInputsAreRejected() throws Exception {
        NotebookStore store=new NotebookStore(temporary.newFolder());String book=store.createNotebook().id();
        for(String area:new String[]{null,"../escape","por-mac-v11-geo-33","por-mac-v11-geo-00"})
            assertThrows(IllegalArgumentException.class,()->store.loadExploration(book,area));
        assertThrows(IllegalArgumentException.class,()->store.saveExploration("../escape",AREA,trail()));
        assertThrows(IOException.class,()->store.loadExploration(UUID.randomUUID().toString(),AREA));
        assertThrows(IllegalArgumentException.class,()->store.saveExploration(book,AREA,null));
    }

    @Test public void whereFightsStartedSurvivesBeingSavedAndLoaded() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);
        String book=store.createNotebook().id();
        ExplorationTrail marked=trail().recordAmbush(17).recordAmbush(200);
        store.saveExploration(book,AREA,marked);
        ExplorationTrail back=store.loadExploration(book,AREA);
        assertTrue(back.ambushed(17));
        assertTrue(back.ambushed(200));
        assertFalse(back.ambushed(18));
        assertEquals(2,back.ambushCount());
    }

    @Test public void aRecordFromBeforeThisFeatureStillLoads() throws Exception {
        /*
         * Version 1 has nothing after the steps. Reading it as "no fights
         * remembered here" is the truth about that record rather than a loss,
         * and refusing it would throw away somebody's whole walked map.
         */
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);
        String book=store.createNotebook().id();
        store.saveExploration(book,AREA,trail().recordAmbush(17));
        File file=file(root,book,AREA);
        byte[] current=bytes(file);
        // Rebuild it as version 1: magic, version, payload length, payload,
        // CRC of the payload. The 32 bytes of marks this build appends come
        // off the end of the payload.
        int payloadLength=ByteBuffer.wrap(current).getInt(8)-32;
        byte[] payload=java.util.Arrays.copyOfRange(current,12,12+payloadLength);
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();
        crc.update(payload);
        // The checksum is a long, so the envelope costs twenty bytes, not sixteen.
        ByteBuffer older=ByteBuffer.allocate(12+payloadLength+8);
        older.putInt(ByteBuffer.wrap(current).getInt(0)).putInt(1).putInt(payloadLength)
                .put(payload).putLong(crc.getValue());
        Files.write(file.toPath(),older.array());

        ExplorationTrail back=store.loadExploration(book,AREA);
        assertEquals("no fights remembered, because the record had none",0,back.ambushCount());
        assertTrue("and the walked squares came through",back.visited(17));
    }

    @Test public void corruptFutureTruncatedOversizedDataFailsAndIsNeverOverwritten() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.save(book,AREA,1,1,InkNote.empty());store.saveExploration(book,AREA,trail());
        File file=file(root,book,AREA);byte[] original=bytes(file),badCrc=original.clone(),future=original.clone();
        // Version 2 is what this build writes; 3 is the one from the future.
        badCrc[badCrc.length-1]^=1;ByteBuffer.wrap(future).putInt(4,3);
        byte[] length=original.clone();ByteBuffer.wrap(length).putInt(8,Integer.MAX_VALUE);
        for(byte[] broken:new byte[][]{new byte[0],Arrays.copyOf(original,11),Arrays.copyOf(original,original.length-1),
                badCrc,future,length,Arrays.copyOf(original,original.length+1),new byte[1045]}) {
            Files.write(file.toPath(),broken);
            assertThrows(IOException.class,()->store.loadExploration(book,AREA));
            assertThrows(IOException.class,()->store.saveExploration(book,AREA,ExplorationTrail.empty()));
            assertArrayEquals(broken,bytes(file));
            assertEquals(Collections.singleton(17),store.listFlags(book,AREA)); // Notes remain independent.
        }
    }

    @Test public void checksIdentityEvenWhenEnvelopeChecksumIsValid() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);
        String book=store.createNotebook().id(),otherBook=store.createNotebook().id();
        store.saveExploration(book,AREA,trail());File file=file(root,book,AREA);
        for(byte[] wrong:new byte[][]{raw(otherBook,AREA,new byte[32],0),raw(book,OTHER,new byte[32],0)}) {
            Files.write(file.toPath(),wrong);
            assertThrows(IOException.class,()->store.loadExploration(book,AREA));
            assertThrows(IOException.class,()->store.saveExploration(book,AREA,trail()));
            assertArrayEquals(wrong,bytes(file));
        }
    }

    @Test public void rejectsImpossibleSegmentsUnvisitedPointsAndInvalidCountsWithValidChecksums() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.saveExploration(book,AREA,trail());File file=file(root,book,AREA);
        byte[] all=new byte[32];Arrays.fill(all,(byte)255);
        for(byte[] wrong:new byte[][]{
                raw(book,AREA,all,257),raw(book,AREA,all,1),raw(book,AREA,new byte[32],1,-1,17),
                raw(book,AREA,all,1,15,16),raw(book,AREA,all,1,17,17),raw(book,AREA,all,1,-2,17),
                raw(book,AREA,all,2,-1,17,19,18),raw(book,AREA,all,0,-1,17)}) {
            Files.write(file.toPath(),wrong);
            assertThrows(IOException.class,()->store.loadExploration(book,AREA));
            assertThrows(IOException.class,()->store.saveExploration(book,AREA,trail()));
            assertArrayEquals(wrong,bytes(file));
        }
    }

    @Test public void fullBoundedTrailWithAllCoverageRoundTrips() throws Exception {
        NotebookStore store=new NotebookStore(temporary.newFolder());String book=store.createNotebook().id();
        ExplorationTrail bounded=ExplorationTrail.empty();
        for(int i=0;i<512;i++) bounded=bounded.record(i%256,i==0?-1:(i-1)%256);
        assertEquals(256,bounded.steps.size());assertEquals(256,bounded.visitedCount());
        store.saveExploration(book,AREA,bounded);assertEquals(bounded,store.loadExploration(book,AREA));
    }

    @Test public void failedWritePreservesPreviousBytesAndDoesNotLeavePendingFiles() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.saveExploration(book,AREA,trail());File file=file(root,book,AREA);byte[] original=bytes(file);
        File parent=file.getParentFile();Set<PosixFilePermission> before=Files.getPosixFilePermissions(parent.toPath());
        try {
            Files.setPosixFilePermissions(parent.toPath(),EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_EXECUTE));
            assumeFalse(Files.isWritable(parent.toPath()));
            assertThrows(IOException.class,()->store.saveExploration(book,AREA,ExplorationTrail.empty()));
        } finally { Files.setPosixFilePermissions(parent.toPath(),before); }
        assertArrayEquals(original,bytes(file));assertEquals(trail(),store.loadExploration(book,AREA));
        assertArrayEquals(new String[]{"exploration.bin"},parent.list());
    }

    @Test public void linkedExplorationFilesCannotReadOrOverwriteAnotherArea() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);String book=store.createNotebook().id();
        store.saveExploration(book,AREA,trail());store.saveExploration(book,OTHER,ExplorationTrail.empty());
        File linked=file(root,book,OTHER),target=file(root,book,AREA);byte[] original=bytes(target);
        Files.delete(linked.toPath());Files.createSymbolicLink(linked.toPath(),target.toPath());
        assertThrows(IOException.class,()->store.loadExploration(book,OTHER));
        assertThrows(IOException.class,()->store.saveExploration(book,OTHER,ExplorationTrail.empty()));
        assertArrayEquals(original,bytes(target));
    }
}
