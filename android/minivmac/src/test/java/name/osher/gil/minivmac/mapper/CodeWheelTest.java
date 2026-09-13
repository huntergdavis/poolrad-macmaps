package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeWheelTest {
    @Test public void actualMacPromptWasAccepted() {
        assertEquals("ZOMBIE", CodeWheel.lookup(30, 20, 1));
        assertEquals("ZOMBIE", CodeWheel.entry(30, 20, 1));
        assertEquals("WYVERN", CodeWheel.entry(21, 14, 2)); // Native helper + automatic Return accepted, offline.
    }
    @Test public void preservesAllThreePathOffsetsAndWraparound() {
        assertEquals("BEWARE", CodeWheel.lookup(1, 1, 0));
        assertEquals("NOTNOW", CodeWheel.lookup(1, 1, 1));
        assertEquals("ZOMBIE", CodeWheel.lookup(1, 1, 2));
        assertEquals("0SOMAS", CodeWheel.lookup(36, 36, 0));
        assertEquals("AXEIAX", CodeWheel.lookup(36, 1, 0));
    }
    @Test public void omitsAlignmentDigitOnlyForGameEntry() {
        assertEquals("1GKKRY", CodeWheel.lookup(1, 26, 0));
        assertEquals("GKKRY", CodeWheel.entry(1, 26, 0));
        assertEquals("SOMAS", CodeWheel.entry(36, 36, 0));
        assertEquals("80ASIS", CodeWheel.lookup(1, 33, 0));
        assertEquals("0ASIS", CodeWheel.entry(1, 33, 0)); // Preserve the linked table, including its zero.
    }
    @Test public void all3888SelectionsProduceBoundedEntries() {
        for (int e = 1; e <= 36; e++) for (int d = 1; d <= 36; d++) for (int p = 0; p < 3; p++) {
            assertTrue(CodeWheel.lookup(e, d, p).matches("[A-Z0-9]{6}"));
            assertTrue(CodeWheel.entry(e, d, p).matches("[A-Z0-9]{5,6}"));
        }
    }
    @Test public void missingAndInvalidSelectionsAreRejected() {
        int[][] bad = {{0,1,0},{37,1,0},{1,0,0},{1,37,0},{1,1,-1},{1,1,3}};
        for (int[] b : bad) assertThrows(IllegalArgumentException.class, () -> CodeWheel.lookup(b[0], b[1], b[2]));
    }
}
