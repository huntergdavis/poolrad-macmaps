package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class GameClockTest {
    private byte[] packet(int day, int hour, int minute) {
        byte[] p = new byte[1212];
        p[0]='P'; p[1]='R'; p[2]='M'; p[3]='6';
        p[24]=3; p[25]=1; p[27]=2; p[31]=1; p[32]=1;
        p[34]=p[35]=p[130]=p[131]=p[132]=p[1200]=(byte)255;
        p[1204]=1; p[1205]=(byte)(day >>> 24); p[1206]=(byte)(day >>> 16);
        p[1207]=(byte)(day >>> 8); p[1208]=(byte)day;
        p[1209]=(byte)hour; p[1210]=(byte)minute;
        return p;
    }

    @Test public void midnightNoonAndLastMinuteUseAmPm() {
        assertEquals("Day 1 · 12:00 am", MapObservation.parse(packet(1,0,0)).clock.label());
        assertEquals("Day 31 · 12:05 pm", MapObservation.parse(packet(31,12,5)).clock.label());
        assertEquals("Day 361 · 11:59 pm", MapObservation.parse(packet(361,23,59)).clock.label());
        assertEquals("Day 92160 · 1:01 am", MapObservation.parse(packet(92160,1,1)).clock.label());
    }

    @Test public void invalidClockNeverFabricatesTimeOrHidesValidMode() {
        for (int[] fields : new int[][]{{0,0,0},{92161,0,0},{-1,0,0},{1,24,0},{1,0,60}}) {
            MapObservation o=MapObservation.parse(packet(fields[0],fields[1],fields[2]));
            assertNull(o.clock); assertEquals(MapMode.CAMP,o.mode);
        }
        for (int at : new int[]{1204,1211}) {
            byte[] p=packet(1,0,0); p[at]=2;
            assertNull(MapObservation.parse(p).clock);
        }
    }

    @Test public void loadingAndUpdatingDiscardEvenValidClockPayload() {
        for (int mode : new int[]{5,6}) {
            byte[] p=packet(1,0,0); p[24]=(byte)mode; p[27]=4;
            assertNull(MapObservation.parse(p).clock);
        }
        assertNull(MapObservation.parse(null).clock);
    }

    @Test public void legacyAndTruncatedPacketsNeverGuessTime() {
        for (int size : new int[]{1200,1204,1211,1213}) {
            byte[] p=Arrays.copyOf(packet(1,0,0),size);
            assertEquals(MapMode.UNAVAILABLE,MapObservation.parse(p).mode);
        }
        byte[] legacy=Arrays.copyOf(packet(1,0,0),1204); legacy[3]='5';
        assertEquals(MapMode.CAMP,MapObservation.parse(legacy).mode);
        assertNull(MapObservation.parse(legacy).clock);
    }

    @Test public void clockIsImmutableAndComparedByDisplayedMinute() {
        byte[] p=packet(42,13,3); GameClock c=MapObservation.parse(p).clock;
        GameClock same=MapObservation.parse(p).clock;
        assertEquals(c,same); assertEquals(c.hashCode(),same.hashCode());
        p[1210]=4;
        assertEquals("Day 42 · 1:03 pm",c.label());
        assertNotEquals(c,MapObservation.parse(p).clock);
    }

    @Test public void combatAndWildernessKeepClockWithoutLocalCoordinates() {
        for (int[] pair : new int[][]{{2,5},{4,3}}) {
            byte[] p=packet(2,8,10); p[24]=(byte)pair[0];p[27]=(byte)pair[1];
            MapObservation o=MapObservation.parse(p);
            assertNull(o.state);assertEquals("Day 2 · 8:10 am",o.clock.label());
        }
    }
}
