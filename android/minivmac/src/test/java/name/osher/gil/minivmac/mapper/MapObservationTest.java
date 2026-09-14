package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapObservationTest {
    private byte[] local() {
        byte[] data = new byte[1200];
        data[0]='P'; data[1]='R'; data[2]='M'; data[3]='4';
        data[24]=1; data[25]=1; data[26]=1; data[27]=4; data[31]=42;
        data[32]=1; data[33]=1; data[35]=20;
        data[130]=15; data[131]=1; data[132]=6;
        data[176]=0x12; data[432]=0x34; data[944]=(byte)0xe4;
        return data;
    }

    private AreaIdentity.Catalog catalog(byte[] packet) {
        byte[] legacy=packet.clone(); legacy[3]='1';
        GeoMap map=PoolRadState.parse(legacy).map;
        return new AreaIdentity.Catalog(new String[]{"20 "+AreaIdentity.fingerprint(map)},
                new String[]{"20 "+AreaIdentity.prefixFingerprint(map)});
    }

    private byte[] status(int mode, int engine) {
        byte[] data=new byte[1200];
        data[0]='P'; data[1]='R'; data[2]='M'; data[3]='4';
        data[24]=(byte)mode; data[25]=1; data[27]=(byte)engine; data[31]=42;
        data[32]=1; data[34]=data[35]=(byte)255;
        data[130]=data[131]=data[132]=(byte)255;
        return data;
    }

    private void unavailable(MapObservation observation) {
        assertNotNull(observation); assertEquals(MapMode.UNAVAILABLE,observation.mode);
        assertNull(observation.state);
    }

    @Test public void verifiedLocalPositionAndProcessingRemainDistinct() {
        byte[] data=local(); AreaIdentity.Catalog identities=catalog(data);
        MapObservation live=MapObservation.parse(data,identities);
        assertEquals(MapMode.EXPLORATION,live.mode);assertTrue(live.state.explorationSafe);
        assertEquals("15, 1 W",live.state.positionLabel());
        assertEquals("por-mac-v11-geo-20",live.state.area.id());
        data[26]=0;
        MapObservation busy=MapObservation.parse(data,identities);
        assertEquals(MapMode.EXPLORATION,busy.mode);assertFalse(busy.state.explorationSafe);
        assertTrue(busy.state.explorationProcessing);
    }

    @Test public void namedStatusOnlyModesNeverInventLocalCoordinates() {
        int[] modes={2,3,4,5,6}, engines={5,2,3,4,4};
        MapMode[] expected={MapMode.COMBAT,MapMode.CAMP,MapMode.WILDERNESS,MapMode.LOADING,MapMode.UPDATING};
        for(int i=0;i<modes.length;i++) {
            byte[] packet=status(modes[i],engines[i]); byte[] original=packet.clone();
            MapObservation observation=MapObservation.parse(packet);
            assertEquals(expected[i],observation.mode);assertNull(observation.state);
            assertArrayEquals(original,packet);
            packet[31]=0; // Exhausted/missing walk epoch cannot invalidate independently known status.
            assertEquals(expected[i],MapObservation.parse(packet).mode);
        }
    }

    @Test public void unavailableLocalGeometryCannotMakeExplorationLive() {
        unavailable(MapObservation.parse(status(1,4)));
        byte[] data=local();unavailable(MapObservation.parse(data));
        AreaIdentity.Catalog identities=catalog(data);
        data[35]=0;unavailable(MapObservation.parse(data,identities));
        data=local();data[176]^=1;unavailable(MapObservation.parse(data,identities));
    }

    @Test public void unavailableModesAndMalformedPacketsHaveNoState() {
        unavailable(MapObservation.parse(null));
        unavailable(MapObservation.parse(new byte[1199]));
        unavailable(MapObservation.parse(new byte[1201]));
        for(int at=0;at<4;at++) {
            byte[] data=local();data[at]='X';unavailable(MapObservation.parse(data,catalog(local())));
        }
        for(int mode:new int[]{0,7,255}) unavailable(MapObservation.parse(status(mode,4)));
    }

    @Test public void contradictorySafeAndGeometryFlagsAreRejected() {
        for(int mode:new int[]{2,3,4,5,6}) {
            byte[] data=status(mode,mode==2?5:mode==3?2:mode==4?3:4);
            data[26]=1;unavailable(MapObservation.parse(data));
            data[26]=0;data[33]=1;unavailable(MapObservation.parse(data));
        }
        byte[] data=status(1,4);data[26]=1;unavailable(MapObservation.parse(data));
        data=local();data[33]=2;unavailable(MapObservation.parse(data,catalog(local())));
    }

    @Test public void unknownMovementVersionsAndFlagValuesAreNotInterpreted() {
        for(int mode:new int[]{1,2}) {
            byte[] original=mode==1?local():status(2,5);
            for(int at:new int[]{25,26}) for(int value:new int[]{2,255}) {
                byte[] changed=original.clone();changed[at]=(byte)value;
                unavailable(MapObservation.parse(changed,catalog(local())));
            }
            original[25]=0;unavailable(MapObservation.parse(original,catalog(local())));
        }
    }

    @Test public void exactEngineAndPresentationValuesAreRequiredForNamedModes() {
        int[] modes={1,2,3,4,6}, engines={4,5,2,3,4};
        for(int i=0;i<modes.length;i++) {
            byte[] data=modes[i]==1?local():status(modes[i],engines[i]);
            data[27]=0;unavailable(MapObservation.parse(data,catalog(local())));
            data[27]=(byte)engines[i];data[32]=0;unavailable(MapObservation.parse(data,catalog(local())));
            data[32]=5;unavailable(MapObservation.parse(data,catalog(local())));
        }
        for(int engine:new int[]{0,7}) assertEquals(MapMode.LOADING,MapObservation.parse(status(5,engine)).mode);
        unavailable(MapObservation.parse(status(5,8)));
        unavailable(MapObservation.parse(status(5,255)));
        for(int presentation=1;presentation<=4;presentation++) {
            byte[] data=status(2,5);data[32]=(byte)presentation;
            assertEquals(MapMode.COMBAT,MapObservation.parse(data).mode);
        }
        byte[] data=local();data[32]=2;unavailable(MapObservation.parse(data,catalog(local())));
    }

    @Test public void statusOnlyPacketsRejectLeakedOrContradictoryLocalPayload() {
        for(int at:new int[]{34,35,40,47,48,129,130,131,132,133,176,1199}) {
            byte[] data=status(2,5);data[at]^=1;
            unavailable(MapObservation.parse(data));
        }
    }

    @Test public void recordableExplorationRequiresNonzeroEpochAndPositionsAreBounded() {
        byte[] data=local();AreaIdentity.Catalog identities=catalog(data);
        data[31]=0;unavailable(MapObservation.parse(data,identities));
        data[26]=0;assertEquals(MapMode.EXPLORATION,MapObservation.parse(data,identities).mode);
        for(int at=130;at<=132;at++) {
            data=local();data[at]=(byte)255;unavailable(MapObservation.parse(data,identities));
        }
    }

    @Test public void localNarrativePositionUpdatesWithoutAuthorizingFootprints() {
        byte[] data=local(); AreaIdentity.Catalog identities=catalog(data);
        MapObservation before=MapObservation.parse(data,identities);
        data[26]=0; data[130]=14;
        MapObservation printing=MapObservation.parse(data,identities);
        assertEquals(MapMode.EXPLORATION,printing.mode);
        assertEquals(14,printing.state.x); assertEquals(15,before.state.x);
        assertEquals(before.state.area,printing.state.area);
        assertFalse(printing.state.explorationSafe);
        assertTrue(printing.state.explorationProcessing);
        MapObservation relocating=MapObservation.parse(status(6,4));
        assertEquals(MapMode.UPDATING,relocating.mode); assertNull(relocating.state);
    }

    @Test public void legacyPrm1AndPrm2KeepOriginalMapBehavior() {
        byte[] data=local();AreaIdentity.Catalog identities=catalog(data);
        for(byte version:new byte[]{'1','2'}) {
            data[3]=version;data[24]=(byte)255;data[25]=(byte)255;data[26]=(byte)255;data[27]=(byte)255;
            MapObservation old=MapObservation.parse(data,identities);
            assertEquals(MapMode.EXPLORATION,old.mode);assertNotNull(old.state);
            assertFalse(old.state.hasExplorationMetadata);assertFalse(old.state.explorationSafe);
            assertEquals("por-mac-v11-geo-20",old.state.area.id());
        }
        data[3]='1';assertNotNull(MapObservation.parse(data).state); // Old unidentified map stays viewable.
        data[3]='2';unavailable(MapObservation.parse(data));
    }

    @Test public void legacyPrm3UsesItsExistingAuthenticatedEngineMetadata() {
        byte[] data=local();data[3]='3';AreaIdentity.Catalog identities=catalog(data);
        assertEquals(MapMode.EXPLORATION,MapObservation.parse(data,identities).mode);
        data[26]=0;
        for(int engine:new int[]{2,4,5}) {
            data[27]=(byte)engine;
            MapObservation old=MapObservation.parse(data,identities);
            assertEquals(engine==2?MapMode.CAMP:engine==4?MapMode.UPDATING:MapMode.COMBAT,old.mode);
            assertNotNull(old.state);assertFalse(old.state.explorationSafe);
        }
        data[27]=3;unavailable(MapObservation.parse(data,identities));
        data[27]=5;data[26]=1;unavailable(MapObservation.parse(data,identities));
        data[26]=0;data[25]=2;unavailable(MapObservation.parse(data,identities));
        data[25]=1;data[26]=2;unavailable(MapObservation.parse(data,identities));
    }

    @Test public void observationOwnsAuthenticatedGeometryAfterCallerReusesPacket() {
        byte[] data=local();MapObservation observation=MapObservation.parse(data,catalog(data));
        data[130]=0;data[176]=0;data[24]=0;
        assertEquals(15,observation.state.x);assertEquals(1,observation.state.map.wall(0,0,0));
        assertEquals(MapMode.EXPLORATION,observation.mode);
    }

    @Test public void everyModeHasReadableLabelsAndNonMisleadingLoadingExplanation() {
        for(MapMode mode:MapMode.values()) {
            assertFalse(mode.label().trim().isEmpty());assertFalse(mode.explanation().trim().isEmpty());
        }
        assertTrue(MapMode.LOADING.label().contains("setup"));
        assertTrue(MapMode.COMBAT.explanation().contains("separate"));
    }
}
