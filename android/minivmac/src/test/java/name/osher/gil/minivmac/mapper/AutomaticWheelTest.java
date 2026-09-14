package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticWheelTest {
    private WheelPrompt prompt(String text, int attempt) {
        byte[] p = new byte[80]; p[0]='P'; p[1]='R'; p[2]='W'; p[3]='1';
        put(p,4,0x700000); put(p,8,0x6ff000); put(p,12,0x600000);
        p[16]=0; p[17]=(byte)attempt; p[18]=(byte)text.length(); p[19]=6;
        for(int i=0;i<6;i++)p[20+i]=(byte)"BEWARE".charAt(i);
        for(int i=0;i<text.length();i++)p[28+i]=(byte)text.charAt(i);
        return WheelPrompt.parse(p);
    }
    private void put(byte[] p,int at,int value) { for(int i=3;i>=0;i--){p[at+i]=(byte)value;value>>>=8;} }
    @Test public void verifiesEveryLetterBeforeOneReturn() {
        AutomaticWheel a=new AutomaticWheel(); assertEquals(0,a.observe(prompt("",1),0));
        String typed=""; for(int i=0;i<6;i++){assertEquals("BEWARE".charAt(i),a.observe(prompt(typed,1),250+i*250));typed+="BEWARE".charAt(i);}
        assertEquals('\n',a.observe(prompt(typed,1),2000));
        assertEquals(0,a.observe(prompt(typed,1),2250));
    }
    @Test public void leavesManuallyTypedPromptsAlone() {
        AutomaticWheel a=new AutomaticWheel(); assertEquals(0,a.observe(prompt("B",1),0));
        assertEquals(0,a.observe(prompt("",1),250));
        assertEquals(0,a.observe(prompt("",2),500)); assertEquals('B',a.observe(prompt("",2),750));
    }
    @Test public void heldKeyReadbackNeverConsumesTheNextLetterOrReturn() {
        AutomaticWheel a=new AutomaticWheel();
        assertEquals(0,a.observeIfReleased(prompt("",1),0,false));
        String typed="";
        for(int i=0;i<6;i++) {
            assertEquals("BEWARE".charAt(i),a.observeIfReleased(prompt(typed,1),250+i*300,false));
            typed+="BEWARE".charAt(i);
            assertEquals(0,a.observeIfReleased(prompt(typed,1),300+i*300,true));
            assertEquals(0,a.observeIfReleased(prompt(typed,1),350+i*300,true));
        }
        assertEquals('\n',a.observeIfReleased(prompt(typed,1),2200,false));
        assertEquals(0,a.observeIfReleased(prompt(typed,1),2500,false));
    }
    @Test public void missingReadbackDoesNotRepeatAKeyOrReturn() {
        AutomaticWheel a=new AutomaticWheel(); a.observe(prompt("",1),0); assertEquals('B',a.observe(prompt("",1),250));
        assertEquals(0,a.observe(prompt("",1),500)); assertEquals(0,a.observe(prompt("",1),2500));
        assertEquals(0,a.observe(prompt("B",1),2750));
    }
    @Test public void userEditsAndSuspensionStopFurtherInput() {
        AutomaticWheel a=new AutomaticWheel(); a.observe(prompt("",1),0);a.observe(prompt("",1),250);
        assertEquals(0,a.observe(prompt("X",1),500)); assertEquals(0,a.observe(prompt("B",1),750));
        a.reset();a.observe(prompt("",1),0);a.observe(prompt("",1),250);a.suspend();
        assertEquals(0,a.observe(prompt("B",1),500));
    }
    @Test public void unknownOrIntermittentSamplesNeverType() {
        AutomaticWheel a=new AutomaticWheel();assertEquals(0,a.observe(null,0));
        a.observe(prompt("",1),250);assertEquals(0,a.observe(null,500));assertEquals(0,a.observe(prompt("",1),750));
        assertEquals('B',a.observe(prompt("",1),1000)); assertEquals(0,a.observe(null,1250));
        assertEquals('E',a.observe(prompt("B",1),1500));
    }
    @Test public void returningToGameResetsAfterSustainedAbsence() {
        AutomaticWheel a=new AutomaticWheel();a.observe(prompt("",1),0);a.observe(prompt("",1),250);a.suspend();
        a.observe(null,500);a.observe(null,2600);assertEquals(0,a.observe(prompt("",1),3000));
        assertEquals('B',a.observe(prompt("",1),3250));
    }
}
