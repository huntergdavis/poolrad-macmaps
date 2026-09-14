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
        assertEquals(29, AreaIdentity.prefixCatalogSize());
        AreaIdentity phlan = AreaIdentity.resolveFingerprint(
                "4d2541a2db2e3c3af90a9f1de437e2483146be72a91a766f7b970aec9d2db326");
        assertEquals("por-mac-v11-geo-0", phlan.id());
        assertEquals("New Phlan", phlan.label());
        assertEquals("por-mac-v11-geo-9", AreaIdentity.resolveFingerprint(
                "8c53a204869ee27bceb259bb41b14a6c73f6a7fbd5b37b97b7781618be07812f").id());
        AreaIdentity finalRecord = AreaIdentity.resolveFingerprint(
                "d50ae52621114d264567cd8ee26b119d23cd95ad852d2fee54dcd5fb81654d8f");
        assertEquals("por-mac-v11-geo-32", finalRecord.id());
        assertEquals("Kuto's Well Catacombs", finalRecord.label());
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

    @Test public void verifiedMacNamesDoNotChangeDurableKeys() {
        int[] ids={0,1,2,3,4,5,6,7,9,10,13,14,15,16,17,18,20,21,22,23,24,25,26,27,28,29,30,31,32};
        String[] names={"New Phlan","Buccaneer Base","Cadorna Textile House","Valjevo Castle — Northwest",
                "Valjevo Castle — Northeast","Valjevo Castle — Southeast","Valjevo Castle — Southwest",
                "Valjevo Castle — Inner Tower","Stojanow Gate","Valhingen Graveyard","Kobold Caves","Kovel Mansion",
                "Mendor's Library","Lizardmen Keep","Nomad Camp","Podal Plaza","Slums of Phlan","Sokal Keep",
                "Sorcerer's Pyramid — Entrance","Sorcerer's Pyramid — Inner Chambers","Temple of Bane","Dark Cave (25)",
                "Grove and Ruined Huts","Dark Cave (27)","Zhentil Outpost","Kuto's Well","Lizardmen Catacombs",
                "Mansion District","Kuto's Well Catacombs"};
        for(int i=0;i<ids.length;i++) {
            GeoMap map=synthetic(i);
            AreaIdentity area=new AreaIdentity.Catalog(new String[]{row(ids[i],map)}).resolve(map);
            assertEquals(names[i],area.label());assertEquals("por-mac-v11-geo-"+ids[i],area.id());
        }
        GeoMap unknownName=synthetic(20);
        assertEquals("Area 255",new AreaIdentity.Catalog(new String[]{row(255,unknownName)}).resolve(unknownName).label());
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

    @Test public void explicitRecordIdCannotAuthenticateOldOrDifferentGeometry() {
        GeoMap phlan=synthetic(12), slums=synthetic(13);
        AreaIdentity.Catalog catalog=new AreaIdentity.Catalog(new String[]{row(0,phlan),row(20,slums)});
        AreaIdentity original=catalog.resolve(phlan);
        assertEquals(original,catalog.resolve(0,phlan));
        assertNull(catalog.resolve(20,phlan)); // New destination ID, previous area's geometry.
        assertNull(catalog.resolve(0,slums)); // Old ID, newly loaded geometry.
        assertNull(catalog.resolve(1,phlan));
        assertNull(catalog.resolve(-1,phlan));
        assertNull(catalog.resolve(256,phlan));
        byte[] changed=phlan.copyData();changed[514]^=1;
        assertNull(catalog.resolve(0,new GeoMap(0,changed))); // No guessed mutable-plane mask.
        assertEquals("por-mac-v11-geo-0",catalog.resolve(0,phlan).id());
        assertEquals("por-mac-v11-geo-20",catalog.resolve(20,slums).id());
        assertEquals(original,catalog.resolve(0,new GeoMap(255,phlan.copyData())));
    }

    @Test public void mutableIdentityRequiresBothExactPrefixAndMatchingRecordId() {
        GeoMap original=synthetic(9), other=synthetic(10);
        AreaIdentity.Catalog catalog=new AreaIdentity.Catalog(new String[]{row(0,original),row(20,other)},
                new String[]{"0 "+AreaIdentity.prefixFingerprint(original),"20 "+AreaIdentity.prefixFingerprint(other)});
        AreaIdentity identity=catalog.resolve(original);
        for(int offset=770;offset<1026;offset++) {
            byte[] data=original.copyData();data[offset]^=0x55;GeoMap changed=new GeoMap(20,data);
            assertEquals("Door byte "+offset,identity,catalog.resolveMutable(0,changed));
            assertNull(catalog.resolve(changed)); // Legacy packets still require all bytes.
            assertNull(catalog.resolveMutable(20,changed));
        }
        for(int offset=2;offset<770;offset++) {
            byte[] data=original.copyData();data[offset]^=1;
            assertNull("Changed immutable byte "+offset,catalog.resolveMutable(0,new GeoMap(0,data)));
        }
        assertNull(catalog.resolveMutable(-1,original));assertNull(catalog.resolveMutable(8,original));
        assertNull(catalog.resolveMutable(0,other));assertNull(catalog.resolveMutable(0,null));
        assertEquals(identity,catalog.resolveMutable(0,original));
    }

    @Test public void mismatchedOrAmbiguousPrefixCatalogDisablesBothResolutionPaths() {
        GeoMap first=synthetic(1),second=synthetic(2);
        String[] exact={row(0,first),row(20,second)};
        String zero="0 "+AreaIdentity.prefixFingerprint(first),twenty="20 "+AreaIdentity.prefixFingerprint(second);
        for(String[] prefixes:new String[][]{{zero},{zero,zero},{zero,"20 "+AreaIdentity.prefixFingerprint(first)},
                {zero,"21 "+AreaIdentity.prefixFingerprint(second)},{zero,"invalid"},{zero,null},new String[0]}) {
            AreaIdentity.Catalog bad=new AreaIdentity.Catalog(exact,prefixes);
            assertNull(bad.resolve(first));assertNull(bad.resolve(second));assertNull(bad.resolveMutable(0,first));
        }
        AreaIdentity.Catalog good=new AreaIdentity.Catalog(exact,new String[]{twenty,zero});
        assertEquals(good.resolve(first),good.resolveMutable(0,first));
        assertNull(new AreaIdentity.Catalog(exact).resolveMutable(0,first));
    }
}
