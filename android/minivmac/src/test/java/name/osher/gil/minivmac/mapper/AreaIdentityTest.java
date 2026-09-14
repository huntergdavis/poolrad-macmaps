package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AreaIdentityTest {
    private GeoMap synthetic(int seed) {
        byte[] data = new byte[1026];
        for (int i = 2; i < data.length; i++) data[i] = (byte) (i * 17 + seed);
        return new GeoMap(-1, data);
    }

    private String row(int id, GeoMap map) { return id + " " + AreaIdentity.fingerprint(map); }

    @Test public void productionCatalogContainsOnlyThe29VerifiedRecords() {
        assertEquals(29, AreaIdentity.catalogSize());
        AreaIdentity phlan = AreaIdentity.resolveFingerprint(
                "4d2541a2db2e3c3af90a9f1de437e2483146be72a91a766f7b970aec9d2db326");
        assertEquals("por-mac-v11-geo-0", phlan.id());
        assertEquals("New Phlan", phlan.label());
        assertEquals("por-mac-v11-geo-9", AreaIdentity.resolveFingerprint(
                "8c53a204869ee27bceb259bb41b14a6c73f6a7fbd5b37b97b7781618be07812f").id());
        AreaIdentity finalRecord = AreaIdentity.resolveFingerprint(
                "d50ae52621114d264567cd8ee26b119d23cd95ad852d2fee54dcd5fb81654d8f");
        assertEquals("por-mac-v11-geo-32", finalRecord.id());
        assertEquals("Area 32", finalRecord.label());
    }

    @Test public void unknownAndEmptyGeometryNeverAcquireAnIdentity() {
        assertNull(AreaIdentity.resolve(null));
        assertNull(AreaIdentity.resolve(synthetic(1)));
        assertNull(AreaIdentity.resolve(new GeoMap(0, new byte[1026])));
        assertNull(AreaIdentity.resolveFingerprint(null));
        assertNull(AreaIdentity.resolveFingerprint("por-mac-v11-geo-0"));
        byte[] justEvents = new byte[1026];
        justEvents[514] = 1;
        assertNull(AreaIdentity.fingerprint(new GeoMap(0, justEvents)));
    }

    @Test public void ignoresRecordPrefixAndReportedIdButNotGeometry() {
        GeoMap original = synthetic(3);
        AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(new String[]{row(24, original)});
        byte[] copy = original.copyData();
        copy[0] = 98;
        copy[1] = 76;
        AreaIdentity first = catalog.resolve(original);
        AreaIdentity second = catalog.resolve(new GeoMap(255, copy));
        assertEquals("por-mac-v11-geo-24", first.id());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.id(), first.toString());
    }

    @Test public void heapRelocationMovementAndFacingDoNotChangeIdentity() {
        GeoMap original = synthetic(6);
        AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(new String[]{row(2, original)});
        byte[] sample = new byte[1200];
        sample[0] = 'P'; sample[1] = 'R'; sample[2] = 'M'; sample[3] = '1';
        System.arraycopy(original.copyData(), 2, sample, 176, 1024);
        AreaIdentity before = catalog.resolve(PoolRadState.parse(sample).map);
        Arrays.fill(sample, 36, 48, (byte) 0x45); // A5, map handle, movable allocation.
        sample[130] = 12; sample[131] = 8; sample[132] = 6;
        assertNotNull(before);
        assertEquals(before, catalog.resolve(PoolRadState.parse(sample).map));
    }

    @Test public void everyByteOfAllFourPlanesIsRequiredToMatch() {
        GeoMap original = synthetic(11);
        AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(new String[]{row(1, original)});
        assertNotNull(catalog.resolve(original));
        for (int offset = 2; offset < 1026; offset++) {
            byte[] changed = original.copyData();
            changed[offset] ^= 0x01;
            assertNull("Changed plane byte " + (offset - 2), catalog.resolve(new GeoMap(1, changed)));
        }
        assertNotNull(catalog.resolve(original)); // A temporary unknown does not alter the catalog.
    }

    @Test public void differentAreasStayDistinctAndCanBeRevisited() {
        GeoMap firstMap = synthetic(2), secondMap = synthetic(3);
        AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(new String[]{row(0, firstMap), row(1, secondMap)});
        AreaIdentity first = catalog.resolve(firstMap), second = catalog.resolve(secondMap);
        assertNotEquals(first, second);
        assertEquals(first, catalog.resolve(new GeoMap(77, firstMap.copyData())));
        assertNotEquals(first, null);
        assertNotEquals(first, first.id());
    }

    @Test public void duplicateDigestOrIdInvalidatesEntireCatalog() {
        GeoMap first = synthetic(2), second = synthetic(3);
        for (String[] rows : new String[][]{
                {row(0, first), row(1, first)},
                {row(0, first), row(0, second)},
                {row(0, first), row(0, first)}}) {
            AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(rows);
            assertNull(catalog.resolve(first));
            assertNull(catalog.resolve(second));
        }
    }

    @Test public void malformedRowsCannotLeaveAPartiallyTrustedCatalog() {
        GeoMap first = synthetic(4);
        String digest = AreaIdentity.fingerprint(first);
        for (String bad : new String[]{null, "", "0", "-1 " + digest, "256 " + digest,
                "01 " + digest, "1 " + digest.toUpperCase(java.util.Locale.ROOT),
                "1 " + digest.substring(1), "1 " + digest + "0", "1  " + digest,
                "1 " + digest + " trailing", "0 " + digest.replace('a', 'z') + "z"}) {
            AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(new String[]{row(0, first), bad});
            assertNull("Malformed row " + bad, catalog.resolve(first));
        }
        assertNull(new AreaIdentity.Catalog(null).resolve(first));
        assertNull(new AreaIdentity.Catalog(new String[0]).resolve(first));
    }

    @Test public void catalogAndGeometryAreNotChangedThroughInputArrays() {
        byte[] data = synthetic(5).copyData();
        GeoMap map = new GeoMap(0, data);
        String[] rows = {row(8, map)};
        AreaIdentity.Catalog catalog = new AreaIdentity.Catalog(rows);
        data[2] = 0;
        rows[0] = "invalid";
        assertEquals("por-mac-v11-geo-8", catalog.resolve(map).id());
        assertNull(catalog.resolve(new GeoMap(0, data)));
    }
}
