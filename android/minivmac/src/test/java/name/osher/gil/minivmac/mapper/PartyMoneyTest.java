package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PartyMoneyTest {
    private byte[] packet(int count) {
        byte[] p=Arrays.copyOf(PartyStateTest.quickPacket(count),PartyState.PURSE_PACKET_SIZE);
        p[3]='A';
        for(int i=0;i<count;i++) p[PartyState.QUICK_PACKET_SIZE+i*PartyState.PURSE_STRIDE]=1;
        return p;
    }
    private void put(byte[] p,int member,int denomination,int count) {
        int at=PartyState.QUICK_PACKET_SIZE+member*PartyState.PURSE_STRIDE+1+2*denomination;
        p[at]=(byte)(count>>8);p[at+1]=(byte)count;
    }

    @Test public void fiveCoinsAndTwoUnappraisedCountsStaySeparate() {
        byte[] p=packet(2);put(p,0,0,1);put(p,0,3,3);put(p,1,4,5);
        put(p,0,5,7);put(p,1,6,8);
        PartyState party=PartyState.parse(p);assertNotNull(party);
        String text=PartyMoney.describe(party);
        assertTrue(text.contains("1 cp · 3 gp · 5 pp"));
        assertTrue(text.contains("Coin value: 28 gp + 1 cp"));
        assertTrue(text.contains("Gems: 7 · Jewelry: 8"));
        assertEquals(7,party.members.get(0).purse.count(5));
    }

    @Test public void unreadablePurseNeverBecomesZeroOrPartialTotal() {
        byte[] p=packet(2);put(p,0,4,5);
        p[PartyState.QUICK_PACKET_SIZE+PartyState.PURSE_STRIDE]=0;
        String text=PartyMoney.describe(PartyState.parse(p));
        assertTrue(text.contains("Party total unavailable · 1 unreadable purse"));
        assertTrue(text.contains("5 pp"));assertFalse(text.contains("Coin value:"));
        assertEquals("Party money unavailable",PartyMoney.describe(null));
        assertTrue(PartyMoney.describe(PartyState.parse(PartyStateTest.quickPacket(2)))
                .contains("2 unreadable purses"));
    }

    @Test public void emptyPurseAndEightMaximumPursesUseExactArithmetic() {
        assertTrue(PartyMoney.describe(PartyState.parse(packet(1))).contains("No coins\nCoin value: 0 gp"));
        byte[] p=packet(8);
        for(int i=0;i<8;i++) for(int j=0;j<7;j++) put(p,i,j,32767);
        String text=PartyMoney.describe(PartyState.parse(p));
        assertTrue(text.contains("262,136 cp"));
        assertTrue(text.contains("Gems: 262,136 · Jewelry: 262,136"));
        long total=32767L*8*1311;
        assertTrue(text.contains("Coin value: "+String.format(java.util.Locale.US,"%,d",total/200)+" gp"));
    }

    @Test public void malformedPurseFramingAndNegativeCountsAreRejected() {
        byte[] p=packet(1);put(p,0,0,32768);assertNull(PartyState.parse(p));
        p=packet(1);p[PartyState.QUICK_PACKET_SIZE]=2;assertNull(PartyState.parse(p));
        p=packet(1);p[PartyState.QUICK_PACKET_SIZE]=0;put(p,0,0,1);assertNull(PartyState.parse(p));
        p=packet(1);p[PartyState.QUICK_PACKET_SIZE+PartyState.PURSE_STRIDE]=1;assertNull(PartyState.parse(p));
        assertNull(PartyState.parse(Arrays.copyOf(packet(1),PartyState.PURSE_PACKET_SIZE-1)));
        p=packet(1);p[3]='9';assertNull(PartyState.parse(p));
    }

    @Test public void purseIsImmutableAndChangesTriggerRetainedReadingUpdate() {
        byte[] p=packet(1);put(p,0,3,7);
        PartyState first=PartyState.parse(p);put(p,0,3,8);
        PartyState second=PartyState.parse(p);
        assertEquals(7,first.members.get(0).purse.count(3));
        assertFalse(first.sameDisplay(second));assertTrue(second.sameDisplay(PartyState.parse(p)));
    }

    @Test public void selectionAndNpcIdentitySurvivePurseExtension() {
        byte[] p=packet(2);p[5]=2;p[6]=2;put(p,1,4,11);
        PartyState party=PartyState.parse(p);assertNotNull(party);
        assertEquals(1,party.selectedIndex);
        assertTrue(party.members.get(1).npc);assertEquals("Hero 2",party.members.get(1).name);
        assertTrue(PartyMoney.describe(party).contains("NPC · Hero 2\n11 pp"));
    }
}
