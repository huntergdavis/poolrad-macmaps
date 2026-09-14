package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only, no private character records. */
public class PartyConditionTest {
    private byte[] packet(int condition, int effects, int hp) {
        byte[] b = new byte[PartyState.CONDITION_PACKET_SIZE];
        b[0]='P';b[1]='R';b[2]='P';b[3]='3';b[4]=1;
        b[8]='A';b[24]=(byte)hp;b[25]=20;b[26]=0;b[27]=2;
        b[168]=(byte)condition;b[169]=(byte)effects;return b;
    }
    private PartyState.Member member(int condition,int effects,int hp) {
        PartyState state=PartyState.parse(packet(condition,effects,hp));assertNotNull(state);return state.members.get(0);
    }
    @Test public void originalStatusTableAndZeroHpAreIndependent() {
        String[] labels={"Okay","Animated","Temporarily gone","Running","Unconscious","Dying","Dead","Petrified (Stoned)","Gone"};
        for(int i=0;i<labels.length;i++)assertEquals(labels[i],member(i,0,0).conditionLabel());
        assertEquals("Okay",member(0,0,0).conditionLabel());
        assertEquals("+",member(0,0,0).badge());
        assertEquals("X",member(6,0,20).badge());
    }
    @Test public void badgePriorityPreservesFullConditionText() {
        String[] badges={"","A","–",">","Z","!","X","S","–"};
        for(int i=0;i<badges.length;i++)assertEquals(badges[i],member(i,0,20).badge());
        PartyState.Member m=member(5,3,0);
        assertEquals("!",m.badge());assertEquals("Dying",m.badgeMeaning());
        assertEquals("Dying; injured; Poisoned; helpless",m.conditionSummary());
        assertEquals("P",member(0,3,10).badge());assertEquals("H",member(0,2,10).badge());
    }
    @Test public void unknownNeverBecomesOkayOrAnEffect() {
        PartyState.Member m=member(255,255,20);
        assertEquals(-1,m.condition);assertEquals(-1,m.trackedEffects);assertEquals("?",m.badge());
        assertFalse(m.hasEffect(PartyState.POISONED));assertFalse(m.hasEffect(PartyState.HELPLESS));
        assertTrue(m.conditionSummary().contains("unavailable"));
        assertEquals(20,m.maxHp);assertEquals(Integer.valueOf(0),m.armorClass);
    }
    @Test public void allUnsupportedConditionAndEffectBytesAreRejected() {
        for(int i=9;i<255;i++)assertNull(PartyState.parse(packet(i,0,20)));
        for(int i=4;i<255;i++)assertNull(PartyState.parse(packet(0,i,20)));
    }
    @Test public void exactVersionSizeAndUnusedTailAreValidated() {
        byte[] b=packet(0,0,20);
        for(int len=0;len<b.length;len++)assertNull(PartyState.parse(Arrays.copyOf(b,len)));
        assertNull(PartyState.parse(Arrays.copyOf(b,b.length+1)));
        for(int at=170;at<b.length;at++){byte[] broken=b.clone();broken[at]=1;assertNull(PartyState.parse(broken));}
        b[3]='4';assertNull(PartyState.parse(b));
    }
    @Test public void legacyPacketsRemainHonestAndKeepClassMarks() {
        for(byte version:new byte[]{'1','2'}){
            byte[] b=Arrays.copyOf(packet(0,0,20),PartyState.PACKET_SIZE);b[3]=version;
            if(version=='1'){b[26]=0;b[27]=0;}
            PartyState.Member m=PartyState.parse(b).members.get(0);
            assertEquals(-1,m.condition);assertEquals(-1,m.trackedEffects);assertEquals("",m.badge());
            assertTrue(m.conditionLabel().contains("unavailable"));
        }
    }
    @Test public void conditionOnlyChangesTriggerImmutableRedraw() {
        byte[] b=packet(0,0,20);PartyState old=PartyState.parse(b);
        b[168]=5;assertFalse(old.sameDisplay(PartyState.parse(b)));assertEquals(0,old.members.get(0).condition);
        b[168]=0;b[169]=1;assertFalse(old.sameDisplay(PartyState.parse(b)));
        assertEquals(0,old.members.get(0).trackedEffects);
    }
    @Test public void reorderedAndIndependentMemberConditionsStayTogether() {
        byte[] b=packet(4,1,0);b[4]=2;
        System.arraycopy(b,8,b,28,20);b[28]='B';b[44]=10;b[170]=5;b[171]=2;
        PartyState p=PartyState.parse(b);assertNotNull(p);
        assertEquals("Unconscious",p.members.get(0).conditionLabel());
        assertEquals("Dying",p.members.get(1).conditionLabel());
        assertEquals("Poisoned",p.members.get(0).effectsLabel());assertEquals("Helpless",p.members.get(1).effectsLabel());
    }
}
