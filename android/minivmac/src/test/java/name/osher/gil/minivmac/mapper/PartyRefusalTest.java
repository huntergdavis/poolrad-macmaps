package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class PartyRefusalTest {
    private byte[] refusal(int code, int links) { return refusal(code, links, 0); }

    private byte[] refusal(int code, int links, long detail) {
        return new byte[]{'P','R','P','X',(byte) code,(byte) links,
                (byte)(detail >> 24),(byte)(detail >> 16),(byte)(detail >> 8),(byte) detail};
    }

    @Test public void everyRefusalCodeReadsAsAnEnglishReason() {
        assertEquals("no party loaded", PartyRefusal.parse(refusal(4, 0)).label());
        assertEquals("heap block rejected after 3 links", PartyRefusal.parse(refusal(7, 3)).label());
        assertEquals("bad roster handle after 1 link", PartyRefusal.parse(refusal(5, 1)).label());
        assertEquals("game not running", PartyRefusal.parse(refusal(1, 0)).label());
        // A code this build does not know about must still say something useful.
        assertEquals("refused 99 after 2 links", PartyRefusal.parse(refusal(99, 2)).label());
    }

    @Test public void onlyARefusalPacketIsARefusal() {
        assertNull(PartyRefusal.parse(null));
        assertNull(PartyRefusal.parse(new byte[0]));
        assertNull(PartyRefusal.parse(new byte[]{'P','R','P','X',1}));                 // too short
        assertNull(PartyRefusal.parse(new byte[11]));                                  // too long
        assertNull(PartyRefusal.parse(refusalWith('6')));                              // a party header
        assertNull(PartyRefusal.parse(refusalWith('X', 'M')));                         // a map header
    }

    private byte[] refusalWith(char kind) { return refusalWith(kind, 'P'); }

    private byte[] refusalWith(char kind, char family) {
        byte[] packet = refusal(1, 0); packet[2] = (byte) family; packet[3] = (byte) kind;
        return packet;
    }

    @Test public void codeAndLinkCountSurviveUnsignedBytes() {
        PartyRefusal high = PartyRefusal.parse(refusal(200, 250));
        assertEquals(200, high.code);
        assertEquals(250, high.links);
    }

    /* The whole point of the detail: a rejected Memory Manager block header has
     * to arrive intact, top bit and all, or it cannot be acted on. */
    @Test public void theRejectedValueSurvivesTheHighBit() {
        PartyRefusal block = PartyRefusal.parse(refusal(7, 2, 0x82000140L));
        assertEquals(0x82000140L, block.detail);
        assertEquals("heap block rejected after 2 links [82000140]", block.label());
        assertEquals("no party loaded", PartyRefusal.parse(refusal(4, 0, 0)).label());
        assertEquals("game not running [1234]", PartyRefusal.parse(refusal(1, 0, 0x1234)).label());
    }
}
