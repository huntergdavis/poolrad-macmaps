package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class PoolRadStateTest {
    private byte[] sample() {
        byte[] data = new byte[1200];
        data[0] = 'P'; data[1] = 'R'; data[2] = 'M'; data[3] = '1';
        data[130] = 15; data[131] = 1; data[132] = 6;
        data[176] = 0x12; data[432] = 0x34; data[944] = (byte) 0xe4;
        return data;
    }

    @Test public void decodesPositionFacingAndFourGeometryPlanes() {
        PoolRadState state = PoolRadState.parse(sample());
        assertNotNull(state);
        assertEquals("15, 1 W", state.positionLabel());
        assertEquals(-1, state.map.id);
        for (int d = 0; d < 4; d++) {
            assertEquals(d + 1, state.map.wall(0, 0, d));
            assertEquals(d, state.map.door(0, 0, d));
        }
    }

    @Test public void handlesAllFourDirections() {
        byte[] data = sample();
        for (int d = 0; d < 4; d++) {
            data[132] = (byte) (d * 2);
            assertEquals(d, PoolRadState.parse(data).facing);
        }
    }

    @Test public void rejectsUnavailableMalformedAndOutOfBounds() {
        assertNull(PoolRadState.parse(null));
        assertNull(PoolRadState.parse(new byte[1199]));
        assertNull(PoolRadState.parse(new byte[1201]));
        byte[] data = sample(); data[3] = '2'; assertNull(PoolRadState.parse(data));
        for (int index = 130; index <= 132; index++) {
            data = sample(); data[index] = (byte) 255; assertNull(PoolRadState.parse(data));
            data[index] = 16; assertNull(PoolRadState.parse(data));
        }
        data = sample(); data[132] = 3; assertNull(PoolRadState.parse(data));
    }

    @Test public void ignoresUnrelatedGlobalAndPointerChanges() {
        byte[] data = sample(); PoolRadState before = PoolRadState.parse(data);
        data[40] = 3; data[110] = 7;
        assertTrue(before.sameDisplay(PoolRadState.parse(data)));
    }

    @Test public void detectsMovementTurningAndMapChanges() {
        PoolRadState before = PoolRadState.parse(sample());
        for (int index : new int[]{130, 131, 132, 176, 432, 688, 944}) {
            byte[] data = sample(); data[index] = 0;
            if (index == 688) data[index] = 1;
            assertFalse(before.sameDisplay(PoolRadState.parse(data)));
        }
        assertFalse(before.sameDisplay(null));
    }

    @Test public void ownsItsGeometryAfterInputChanges() {
        byte[] data = sample(); PoolRadState before = PoolRadState.parse(data);
        data[176] = 0;
        assertEquals(1, before.map.wall(0, 0, 0));
        assertTrue(before.sameDisplay(PoolRadState.parse(sample())));
    }

    @Test public void identityOnlyChangesInvalidateTheDisplayedNotebookContext() {
        byte[] data=sample(); GeoMap map=PoolRadState.parse(data).map;
        String digest=AreaIdentity.fingerprint(map);
        PoolRadState first=PoolRadState.parse(data,new AreaIdentity.Catalog(new String[]{"0 "+digest}));
        PoolRadState changed=PoolRadState.parse(data,new AreaIdentity.Catalog(new String[]{"20 "+digest}));
        PoolRadState unknown=PoolRadState.parse(data);
        assertNotNull(first.area); assertNotNull(changed.area); assertNull(unknown.area);
        assertFalse(first.sameDisplay(changed)); assertFalse(changed.sameDisplay(first));
        assertFalse(first.sameDisplay(unknown)); assertFalse(unknown.sameDisplay(first));
        assertEquals("por-mac-v11-geo-0",first.area.id());
        assertEquals("por-mac-v11-geo-20",changed.area.id());
    }

    @Test public void legacyRevisitAndReloadKeepExistingNotebookKeys() {
        byte[] original=sample();
        String digest=AreaIdentity.fingerprint(PoolRadState.parse(original).map);
        AreaIdentity.Catalog catalog=new AreaIdentity.Catalog(new String[]{"20 "+digest});
        PoolRadState first=PoolRadState.parse(original,catalog);
        byte[] moved=original.clone(); moved[40]=99;moved[130]=4;moved[131]=5;moved[132]=2;
        assertEquals(first.area,PoolRadState.parse(moved,catalog).area);
        moved[176]^=1; assertNull(PoolRadState.parse(moved,catalog).area);
        assertEquals("por-mac-v11-geo-20",PoolRadState.parse(original.clone(),catalog).area.id());
        assertTrue(first.sameDisplay(PoolRadState.parse(original.clone(),catalog)));
    }

    private byte[] verified(int id) {
        byte[] data=sample();data[3]='2';data[32]=1;data[33]=1;data[34]=0;data[35]=(byte)id;return data;
    }
    private GeoMap legacyMap(byte[] data) {
        byte[] legacy=data.clone();legacy[3]='1';return PoolRadState.parse(legacy).map;
    }
    private String fullRow(int id,byte[] data) { return id+" "+AreaIdentity.fingerprint(legacyMap(data)); }
    private String prefixRow(int id,byte[] data) { return id+" "+AreaIdentity.prefixFingerprint(legacyMap(data)); }
    private AreaIdentity.Catalog verifiedCatalog(int id,byte[] data) {
        return new AreaIdentity.Catalog(new String[]{fullRow(id,data)},new String[]{prefixRow(id,data)});
    }

    @Test public void verifiedPacketUsesActualGeoIdAndKeepsLegacyNotebookKey() {
        byte[] data=verified(20);AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        PoolRadState live=PoolRadState.parse(data,catalog);
        assertNotNull(live);assertEquals(20,live.map.id);assertEquals("Slums of Phlan",live.area.label());
        assertEquals("por-mac-v11-geo-20",live.area.id());assertEquals("15, 1 W",live.positionLabel());
        byte[] legacy=data.clone();legacy[3]='1';
        assertEquals(live.area,PoolRadState.parse(legacy,catalog).area);
        assertTrue(live.sameDisplay(PoolRadState.parse(legacy,catalog)));
    }

    @Test public void verifiedDoorChangesRetainIdentityButLegacyChangesRemainUnknown() {
        byte[] data=verified(20);AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        PoolRadState original=PoolRadState.parse(data,catalog);
        for(int at=944;at<1200;at++) {
            byte[] changed=data.clone();changed[at]^=(byte)0xff;
            PoolRadState live=PoolRadState.parse(changed,catalog);
            assertNotNull("Door byte "+at,live);assertEquals(original.area,live.area);
            assertFalse(original.sameDisplay(live));
            changed[3]='1';assertNull(PoolRadState.parse(changed,catalog).area);
        }
        for(int at=176;at<944;at++) {
            byte[] changed=data.clone();changed[at]^=1;
            assertNull("Changed immutable byte "+at,PoolRadState.parse(changed,catalog));
        }
    }

    @Test public void explicitUntrustedMetadataNeverFallsBackToTheFullGeometryMatch() {
        byte[] data=verified(20);AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        for(int at:new int[]{32,33}) for(int value:new int[]{0,2,127,255}) {
            byte[] changed=data.clone();changed[at]=(byte)value;
            assertNull("Rejected metadata at "+at+" = "+value,PoolRadState.parse(changed,catalog));
        }
        for(int id:new int[]{0,8,11,12,19,21,33,255}) {
            byte[] changed=data.clone();changed[35]=(byte)id;assertNull(PoolRadState.parse(changed,catalog));
        }
        byte[] changed=data.clone();changed[34]=1;assertNull(PoolRadState.parse(changed,catalog));
        changed[34]=(byte)255;changed[35]=(byte)255;assertNull(PoolRadState.parse(changed,catalog));
        assertNull(PoolRadState.parse(data,new AreaIdentity.Catalog(new String[]{fullRow(20,data)})));
        assertNull(PoolRadState.parse(data)); // Synthetic geometry cannot spoof the real catalog.
    }

    @Test public void earlyDestinationIdCannotBindOldGeometryAndRevisitSurvivesReload() {
        byte[] phlan=verified(0),slums=verified(20);slums[176]^=7;
        AreaIdentity.Catalog catalog=new AreaIdentity.Catalog(new String[]{fullRow(0,phlan),fullRow(20,slums)},
                new String[]{prefixRow(0,phlan),prefixRow(20,slums)});
        PoolRadState before=PoolRadState.parse(phlan,catalog);
        byte[] transition=phlan.clone();transition[35]=20;
        assertNull(PoolRadState.parse(transition,catalog)); // Outer script writes destination before loader.
        transition=slums.clone();transition[35]=0;
        assertNull(PoolRadState.parse(transition,catalog));
        PoolRadState arrived=PoolRadState.parse(slums,catalog);
        assertNotEquals(before.area,arrived.area);assertFalse(before.sameDisplay(arrived));
        byte[] changed=slums.clone();changed[944]^=1;changed[36]=44;changed[40]=99;
        assertEquals(arrived.area,PoolRadState.parse(changed,catalog).area);
        assertEquals(before.area,PoolRadState.parse(phlan.clone(),catalog).area);
        assertEquals("por-mac-v11-geo-0",PoolRadState.parse(phlan,catalog).area.id());
    }

    @Test public void prm3RequiresExplicitExplorationGateAndReadsUnsignedContinuity() {
        byte[] data=verified(20); data[3]='3'; data[25]=1; data[26]=1; data[27]=4;
        data[28]=(byte)0xfe; data[29]=(byte)0xdc; data[30]=(byte)0xba; data[31]=(byte)0x98;
        AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        PoolRadState live=PoolRadState.parse(data,catalog);
        assertTrue(live.hasExplorationMetadata); assertTrue(live.explorationSafe);
        assertEquals(0xfedcba98L,live.continuityToken);
        for(int at:new int[]{25,26,27}) {
            for(int value=0;value<256;value++) {
                byte[] changed=data.clone();changed[at]=(byte)value;
                assertEquals(value==(at==27?4:1),PoolRadState.parse(changed,catalog).explorationSafe);
            }
        }
    }

    @Test public void prm4LocalStateCannotAuthorizeCoordinatesForAnotherMode() {
        byte[] data=verified(20);data[3]='4';data[24]=1;data[25]=1;data[26]=1;data[27]=4;data[31]=1;
        AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        assertTrue(PoolRadState.parse(data,catalog).explorationSafe);
        byte[] busy=data.clone();busy[26]=0;
        assertTrue(PoolRadState.parse(busy,catalog).explorationProcessing);
        byte[] noEpoch=data.clone();noEpoch[31]=0;
        assertNull(PoolRadState.parse(noEpoch,catalog));
        for(int mode=0;mode<256;mode++) if(mode!=1) {
            byte[] changed=data.clone();changed[24]=(byte)mode;
            assertNull("Non-local mode "+mode,PoolRadState.parse(changed,catalog));
        }
        for(int index:new int[]{25,26,27,33}) {
            byte[] changed=data.clone();changed[index]=(byte)255;
            assertNull("Untrusted metadata "+index,PoolRadState.parse(changed,catalog));
        }
    }

    @Test public void legacyDiagnosticBytesCannotMasqueradeAsWalkingMetadata() {
        byte[] data=verified(20); data[25]=1;data[26]=1;data[27]=4;
        AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        for(byte protocol:new byte[]{'1','2'}) {
            data[3]=protocol;PoolRadState old=PoolRadState.parse(data,catalog);
            assertFalse(old.hasExplorationMetadata);assertFalse(old.explorationSafe);
        }
    }

    @Test public void processingFramesDoNotAuthorizePositionOrUnknownMetadata() {
        byte[] data=verified(20);data[3]='3';data[25]=1;data[26]=0;data[27]=4;
        AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        PoolRadState processing=PoolRadState.parse(data,catalog);
        assertTrue(processing.explorationProcessing);assertFalse(processing.explorationSafe);
        data[26]=2;assertFalse(PoolRadState.parse(data,catalog).explorationProcessing);
        data[26]=0;data[27]=5;assertFalse(PoolRadState.parse(data,catalog).explorationProcessing);
        data[27]=4;data[25]=0;assertFalse(PoolRadState.parse(data,catalog).explorationProcessing);
        data[25]=1;data[3]='2';assertFalse(PoolRadState.parse(data,catalog).explorationProcessing);
    }

    @Test public void prm3KeepsStrictIdentityAndSuppressesUnsafeLivePosition() {
        byte[] data=verified(20);data[3]='3';data[25]=1;data[26]=1;data[27]=4;
        AreaIdentity.Catalog catalog=verifiedCatalog(20,data);
        PoolRadState live=PoolRadState.parse(data,catalog);
        data[27]=5;PoolRadState combat=PoolRadState.parse(data,catalog);
        assertEquals(live.area,combat.area);assertFalse(combat.explorationSafe);assertFalse(live.sameDisplay(combat));
        data[35]=0;assertNull(PoolRadState.parse(data,catalog));
        data[35]=20;data[176]^=1;assertNull(PoolRadState.parse(data,catalog));
    }
}
