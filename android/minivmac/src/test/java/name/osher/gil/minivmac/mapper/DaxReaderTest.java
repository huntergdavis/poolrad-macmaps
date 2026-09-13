package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DaxReaderTest {
    private static byte[] archive(byte[] payload, int unpacked) {
        byte[] bytes = new byte[11 + payload.length];
        bytes[0] = 9;
        bytes[2] = 20;
        bytes[7] = (byte) unpacked;
        bytes[8] = (byte) (unpacked >>> 8);
        bytes[9] = (byte) payload.length;
        bytes[10] = (byte) (payload.length >>> 8);
        System.arraycopy(payload, 0, bytes, 11, payload.length);
        return bytes;
    }

    private static byte[] literals(byte[] raw) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length; i += 128) {
            int count = Math.min(128, raw.length - i);
            bytes.write(count - 1);
            bytes.write(raw, i, count);
        }
        return bytes.toByteArray();
    }

    @Test public void decodesDirectionalWallsAndDoorBits() throws Exception {
        byte[] raw = new byte[1026];
        raw[2] = 0x12; raw[258] = 0x34; raw[770] = (byte) 0xe4;
        GeoMap map = DaxReader.readMaps(archive(literals(raw), 1026)).get(0);
        assertEquals(20, map.id);
        for (int d = 0; d < 4; d++) {
            assertEquals(d + 1, map.wall(0,0,d));
            assertEquals(d, map.door(0,0,d));
        }
        assertArrayEquals(raw, map.copyData());
    }

    @Test public void acceptsStoredUncompressedRecord() throws Exception {
        assertEquals(1, DaxReader.readMaps(archive(new byte[1026], 0)).size());
    }

    @Test public void negative128Repeats128TimesNot129() throws Exception {
        byte[] got = DaxReader.decode(new byte[]{(byte)128, 42},0,2,128);
        assertEquals(128, got.length);
        for (byte value : got) assertEquals(42, value);
    }

    @Test public void negativeOneRepeatsExactlyOnce() throws Exception {
        assertArrayEquals(new byte[]{7}, DaxReader.decode(new byte[]{-1,7},0,2,1));
    }

    @Test public void zeroControlCopiesOneLiteral() throws Exception {
        assertArrayEquals(new byte[]{-9}, DaxReader.decode(new byte[]{0,-9},0,2,1));
    }

    @Test public void rejectsShortAndOversizedFiles() {
        assertThrows(IOException.class, () -> DaxReader.readMaps(new byte[10]));
        assertThrows(IOException.class, () -> DaxReader.readMaps(new byte[DaxReader.MAX_FILE_BYTES + 1]));
        assertThrows(IOException.class, () -> DaxReader.readMaps(null));
    }

    @Test public void rejectsInvalidIndex() {
        byte[] bytes = archive(new byte[1026],0); bytes[0] = 8;
        assertThrows(IOException.class, () -> DaxReader.readMaps(bytes));
    }

    @Test public void rejectsUnsignedOffsetOutsideFile() {
        byte[] bytes = archive(new byte[1026],0);
        Arrays.fill(bytes, 3, 7, (byte) 255);
        assertThrows(IOException.class, () -> DaxReader.readMaps(bytes));
    }

    @Test public void rejectsWrongGeometrySize() {
        assertThrows(IOException.class, () -> DaxReader.readMaps(archive(new byte[512],0)));
    }

    @Test public void rejectsTruncatedStoredData() {
        byte[] bytes = archive(new byte[1026],0);
        assertThrows(IOException.class, () -> DaxReader.readMaps(Arrays.copyOf(bytes,bytes.length-1)));
    }

    @Test public void rejectsMissingRunPayloads() {
        assertThrows(IOException.class, () -> DaxReader.decode(new byte[]{-5},0,1,5));
        assertThrows(IOException.class, () -> DaxReader.decode(new byte[]{4,1},0,2,5));
    }

    @Test public void rejectsUnderflowAndOverflow() {
        assertThrows(IOException.class, () -> DaxReader.decode(new byte[]{-5,1},0,2,6));
        assertThrows(IOException.class, () -> DaxReader.decode(new byte[]{-5,1},0,2,4));
    }

    @Test public void validatesCoordinatesAndProtectsMapData() throws Exception {
        GeoMap map = DaxReader.readMaps(archive(new byte[1026],0)).get(0);
        byte[] copy = map.copyData(); copy[2] = (byte)255;
        assertEquals(0, map.wall(0,0,0));
        assertThrows(IllegalArgumentException.class, () -> map.wall(16,0,0));
        assertThrows(IllegalArgumentException.class, () -> map.door(0,-1,0));
        assertThrows(IllegalArgumentException.class, () -> map.wall(0,0,4));
    }
}
