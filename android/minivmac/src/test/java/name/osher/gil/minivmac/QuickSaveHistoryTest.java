package name.osher.gil.minivmac;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class QuickSaveHistoryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private SaveStateStore store() { return new SaveStateStore(tmp.getRoot()); }
    private static byte[] state(int n) { byte[] bytes=new byte[1024];bytes[4]=(byte)n;return bytes; }
    private static final byte[] PNG={(byte)137,'P','N','G',13,10,26,10,0};

    @Test public void twelveRapidSavesKeepTheNewestTenAndTheirExactStates() throws Exception {
        SaveStateStore s=store();
        for(int i=0;i<12;i++) s.writeQuick(state(i),1234567890000L);
        List<File> files=store().quickSaves();assertEquals(10,files.size());
        for(int i=0;i<10;i++) {
            assertArrayEquals(state(11-i),store().read(files.get(i)));
            assertEquals(1234567890000L,SaveStateStore.capturedAt(files.get(i)));
        }
        assertEquals(10,s.saves().size());
    }
    @Test public void clockRollbackDoesNotChangeRotationOrder() throws Exception {
        SaveStateStore s=store();
        for(int i=0;i<12;i++) s.writeQuick(state(i),20000-i);
        assertArrayEquals(state(11),s.read(s.quickSaves().get(0)));
        assertEquals(10,s.quickSaves().size());
    }
    @Test public void rotationRemovesSidecarsButNotNamedAutoOrReference() throws Exception {
        SaveStateStore s=store();
        File named=s.write("Quick save",state(50)), auto=s.writeAuto("Auto test",state(51),20);
        File oldest=s.writeQuick(state(0),10000);
        s.writeBinding(oldest,"notebook-1");s.writePreview(oldest,PNG);
        for(int i=1;i<=10;i++) s.writeQuick(state(i),10000+i);
        assertFalse(oldest.exists());assertFalse(SaveStateStore.previewFile(oldest).exists());assertNull(s.readBinding(oldest));
        assertArrayEquals(state(50),s.read(named));assertArrayEquals(state(51),s.read(auto));
        assertArrayEquals(state(10),store().read(s.quickSaves().get(0)));
        assertTrue(new File(tmp.getRoot(),"reference.prqref").isFile());
    }
    @Test public void failedSaveNeverPrunesExistingHistory() throws Exception {
        SaveStateStore s=store();for(int i=0;i<10;i++) s.writeQuick(state(i),i+1);
        List<File> before=s.quickSaves();
        try { s.writeQuick(new byte[0],50);fail(); } catch(IOException expected) { }
        assertEquals(before,s.quickSaves());
    }
    @Test public void failedPublicationNeverPrunesExistingHistory() throws Exception {
        SaveStateStore s=store();for(int i=0;i<10;i++) s.writeQuick(state(i),i+1);
        List<File> before=s.quickSaves();
        File obstacle=new File(tmp.getRoot(),"quick-history/q00000000000000000011_0000000000050.prqs.part");
        assertTrue(obstacle.mkdir());
        try { s.writeQuick(state(11),50);fail(); } catch(IOException expected) { }
        assertEquals(before,s.quickSaves());
    }
    @Test public void legacyQuickIsKeptUntilItNaturallyAgesOut() throws Exception {
        SaveStateStore s=store();s.write(s.quickFile(),state(99));s.writeBinding(s.quickFile(),"old-book");
        for(int i=0;i<9;i++) s.writeQuick(state(i),i+1);
        assertEquals(10,s.quickSaves().size());assertEquals(s.quickFile(),s.quickSaves().get(9));
        assertArrayEquals(state(99),s.read(s.quickFile()));assertEquals("old-book",s.readBinding(s.quickFile()));
        s.writeQuick(state(10),11);assertFalse(s.quickFile().exists());assertNull(s.readBinding(s.quickFile()));
    }
    @Test public void previewIsOptionalBoundedAndDeletedWithSave() throws Exception {
        SaveStateStore s=store();File file=s.writeQuick(state(1),111);
        assertFalse(SaveStateStore.previewFile(file).exists());assertArrayEquals(state(1),s.read(file));
        s.writePreview(file,PNG);assertTrue(SaveStateStore.previewFile(file).isFile());
        try {s.writePreview(file,new byte[SaveStateStore.MAX_PREVIEW_BYTES+1]);fail();}catch(IOException expected){}
        assertEquals(PNG.length,SaveStateStore.previewFile(file).length());
        assertTrue(s.delete(file));assertFalse(SaveStateStore.previewFile(file).exists());
    }
    @Test public void timestampIncludesYearSecondsAndTwelveHourPeriod() throws Exception {
        File f=store().writeQuick(state(1),1790000000123L);String label=SaveStateStore.displayLabel(f);
        assertTrue(label.startsWith("Quick save\n"));assertTrue(label.contains("'26"));
        assertTrue(label.matches("(?s).*\\d{1,2}:\\d{2}:\\d{2} (AM|PM) .*"));
    }
    @Test public void userNamedQuickSavesNeverEnterTheRotatingNamespace() throws Exception {
        SaveStateStore s=store();File named=s.write("q00000000000000000001_1234567890000",state(40));
        for(int i=0;i<12;i++)s.writeQuick(state(i),i);
        assertTrue(named.exists());assertEquals(10,s.quickSaves().size());assertFalse(s.quickSaves().contains(named));
    }
    @Test public void manualNamesCannotClaimLegacyQuickOrAutomaticSlots() throws Exception {
        SaveStateStore s=store();
        File quick=s.write("quick",state(50)), auto=s.write("Auto my checkpoint",state(51));
        assertFalse(s.quickSaves().contains(quick));assertFalse(s.autoSaves().contains(auto));
        for(int i=0;i<12;i++)s.writeQuick(state(i),i+1);
        for(int i=0;i<3;i++)s.writeAuto("Auto test",state(i),1);
        assertArrayEquals(state(50),s.read(quick));assertArrayEquals(state(51),s.read(auto));
    }
}
