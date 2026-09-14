package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeoMapEdgeTest {
    @Test public void noSurfaceIgnoresEveryDoorByteButPreservesRawData() {
        for (int bits = 0; bits < 256; bits++) {
            byte[] raw = new byte[1026]; raw[770] = (byte) bits;
            GeoMap map = new GeoMap(0, raw);
            for (int d = 0; d < 4; d++) {
                assertEquals(GeoMap.EdgeKind.OPEN, map.edgeKind(0, 0, d));
                assertEquals((bits >>> (d * 2)) & 3, map.door(0, 0, d));
            }
            assertArrayEquals(raw, map.copyData());
        }
    }

    @Test public void everyNonzeroSurfaceWithoutDoorBitsIsAWall() {
        for (int texture = 1; texture < 16; texture++) {
            GeoMap map = edge(texture, 0);
            for (int d = 0; d < 4; d++)
                assertEquals(GeoMap.EdgeKind.WALL, map.edgeKind(0, 0, d));
        }
    }

    @Test public void allDoorStatesRemainNeutralAcrossAllTextures() {
        for (int texture = 1; texture < 16; texture++) for (int state = 1; state < 4; state++) {
            GeoMap map = edge(texture, state);
            for (int d = 0; d < 4; d++)
                assertEquals(GeoMap.EdgeKind.DOORWAY, map.edgeKind(0, 0, d));
        }
    }

    @Test public void originalMacDirectionMasksAgreeAtEveryTile() {
        // CODE6/4 switches use raw facings 0,2,4,6 => NESW. Explicit masks,
        // independent of the production shift expression; synthetic bytes only.
        int[] masks = {0x03, 0x0c, 0x30, 0xc0};
        int[] divisors = {1, 4, 16, 64};
        for (int tile = 0; tile < 256; tile++) {
            byte[] raw = new byte[1026];
            raw[2 + tile] = 0x12; raw[258 + tile] = 0x34;
            raw[770 + tile] = (byte) tile;
            GeoMap map = new GeoMap(0, raw);
            for (int d = 0; d < 4; d++) {
                assertEquals(d + 1, map.wall(tile % 16, tile / 16, d));
                int state = (tile & masks[d]) / divisors[d];
                assertEquals(state, map.door(tile % 16, tile / 16, d));
                assertEquals(state == 0 ? GeoMap.EdgeKind.WALL : GeoMap.EdgeKind.DOORWAY,
                        map.edgeKind(tile % 16, tile / 16, d));
            }
        }
    }

    @Test public void oppositeSidesAreNotInventedOrMirrored() {
        byte[] raw = new byte[1026];
        raw[2] = 0x03; raw[770] = 0x04; // tile0 east doorway
        raw[259] = 0x02; // tile1 west wall, independently represented
        GeoMap map = new GeoMap(0, raw);
        assertEquals(GeoMap.EdgeKind.DOORWAY, map.edgeKind(0, 0, 1));
        assertEquals(GeoMap.EdgeKind.WALL, map.edgeKind(1, 0, 3));
        assertEquals(GeoMap.EdgeKind.OPEN, map.edgeKind(0, 1, 0));
    }

    @Test public void invalidCoordinatesAndDirectionsStillFail() {
        GeoMap map = edge(1, 1);
        assertThrows(IllegalArgumentException.class, () -> map.edgeKind(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> map.edgeKind(0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> map.edgeKind(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> map.edgeKind(0, 0, 4));
    }

    private static GeoMap edge(int texture, int state) {
        byte[] raw = new byte[1026];
        raw[2] = raw[258] = (byte) (texture * 17);
        raw[770] = (byte) (state * 85);
        return new GeoMap(0, raw);
    }
}
