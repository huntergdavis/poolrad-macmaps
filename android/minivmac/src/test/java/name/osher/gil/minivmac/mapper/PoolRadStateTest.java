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
}
