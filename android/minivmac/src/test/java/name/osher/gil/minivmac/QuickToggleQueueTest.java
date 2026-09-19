package name.osher.gil.minivmac;
import name.osher.gil.minivmac.mapper.PartyState;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuickToggleQueueTest {
    private PartyState party(String... names) {
        byte[] b=new byte[PartyState.QUICK_PACKET_SIZE];b[0]='P';b[1]='R';b[2]='P';b[3]='7';b[4]=(byte)names.length;
        for(int i=0;i<names.length;i++) {
            byte[] name=names[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(name,0,b,8+i*20,name.length);b[8+i*20+16]=10;b[8+i*20+17]=10;
            b[PartyState.CONDITION_PACKET_SIZE+i*PartyState.SPELL_STRIDE]=(byte)255;
            b[PartyState.SPELL_PACKET_SIZE+i*PartyState.EQUIP_STRIDE]=(byte)255;
            b[PartyState.EQUIP_PACKET_SIZE+i*PartyState.TRAIN_STRIDE]=(byte)255;
        }
        PartyState result=PartyState.parse(b);assertNotNull(result);return result;
    }
    @Test public void unreadableFramesAndWriterRefusalsKeepIntentUntilSuccess() {
        QuickToggleQueue q=new QuickToggleQueue();PartyState p=party("A","B");
        q.request(p.members.get(0),true);assertFalse(q.drain(null,(i,on)->{fail();return false;}));
        assertTrue(q.busy());assertFalse(q.drain(p,(i,on)->false));assertEquals(Boolean.TRUE,q.pending(p.members.get(0)));
        assertTrue(q.drain(p,(i,on)->{assertEquals(0,i);assertTrue(on);return true;}));assertFalse(q.busy());
    }
    @Test public void anotherTapReversesThePendingIntentWithoutWriting() {
        QuickToggleQueue q=new QuickToggleQueue();PartyState p=party("A");q.request(p.members.get(0),true);
        q.request(p.members.get(0),false);q.drain(p,(i,on)->{fail("Already off: no write needed");return false;});
        assertFalse(q.busy());
    }
    @Test public void reorderingUsesIdentityNotStaleIndex() {
        QuickToggleQueue q=new QuickToggleQueue();q.request(party("A","B").members.get(0),true);
        assertTrue(q.drain(party("B","A"),(i,on)->{assertEquals(1,i);return true;}));
    }
    @Test public void absentAndAmbiguousCharactersCancelInsteadOfWritingAnotherRow() {
        for(PartyState changed:new PartyState[]{party("B"),party("A","A")}) {
            QuickToggleQueue q=new QuickToggleQueue();q.request(party("A").members.get(0),true);
            q.drain(changed,(i,on)->{fail("Wrong identity");return false;});assertFalse(q.busy());
        }
    }
    @Test public void loadOrNewSessionClearsIntent() {
        QuickToggleQueue q=new QuickToggleQueue();PartyState p=party("A");q.request(p.members.get(0),true);q.clear();
        q.drain(p,(i,on)->{fail("Old session write");return false;});assertNull(q.pending(p.members.get(0)));
    }
    @Test public void severalCharactersCanWaitIndependently() {
        QuickToggleQueue q=new QuickToggleQueue();PartyState p=party("A","B");
        q.request(p.members.get(0),true);q.request(p.members.get(1),true);
        q.drain(p,(i,on)->i==1);assertTrue(q.busy());assertNull(q.pending(p.members.get(1)));
        q.drain(p,(i,on)->i==0);assertFalse(q.busy());
    }
    @Test public void queueIsBoundedAtEightDistinctCharacters() {
        QuickToggleQueue q=new QuickToggleQueue();
        for(int i=0;i<8;i++)assertTrue(q.request(party("Hero"+i).members.get(0),true));
        assertFalse(q.request(party("Ninth").members.get(0),true));
        assertTrue(q.request(party("Hero0").members.get(0),false));
    }
}
