package name.osher.gil.minivmac;

import name.osher.gil.minivmac.mapper.PartyState;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartySelectionTargetTest {
    private PartyState party(String... names) { return selected(-1, names); }
    private PartyState selected(int row, String... names) {
        byte[] b = new byte[PartyState.QUICK_PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='P'; b[3]='8'; b[4]=(byte)names.length; b[5]=(byte)(row+1);
        for (int i=0; i<names.length; i++) {
            byte[] name=names[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(name,0,b,8+i*20,name.length);
            b[8+i*20+16]=10; b[8+i*20+17]=10;
            b[PartyState.CONDITION_PACKET_SIZE+i*PartyState.SPELL_STRIDE]=(byte)255;
            b[PartyState.SPELL_PACKET_SIZE+i*PartyState.EQUIP_STRIDE]=(byte)255;
            b[PartyState.EQUIP_PACKET_SIZE+i*PartyState.TRAIN_STRIDE]=(byte)255;
        }
        PartyState result=PartyState.parse(b); assertNotNull(result); return result;
    }
    @Test public void followupRequiresActualSelectionAndUniqueFreshIdentity() {
        PartyState.Member a=party("A","B").members.get(0);
        assertEquals(1, PartySelectionTarget.confirmedIndex(selected(1,"B","A"),a));
        assertEquals(-1, PartySelectionTarget.confirmedIndex(selected(0,"B","A"),a));
        assertEquals(-1, PartySelectionTarget.confirmedIndex(party("A","B"),a));
        assertEquals(-1, PartySelectionTarget.confirmedIndex(selected(0,"A","A"),a));
        assertEquals(-1, PartySelectionTarget.confirmedIndex(null,a));
    }
    @Test public void followsReorderedIdentity() {
        assertEquals(1, PartySelectionTarget.index(party("B","A"), party("A","B").members.get(0)));
    }
    @Test public void refusesAbsentAmbiguousAndUnreadableTargets() {
        PartyState.Member a=party("A").members.get(0);
        assertEquals(-1, PartySelectionTarget.index(party("B"),a));
        assertEquals(-1, PartySelectionTarget.index(party("A","A"),a));
        assertEquals(-1, PartySelectionTarget.index(null,a));
        assertEquals(-1, PartySelectionTarget.index(party("A"),null));
    }
}
