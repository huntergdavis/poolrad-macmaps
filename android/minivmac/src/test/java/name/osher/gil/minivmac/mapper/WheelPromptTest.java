package name.osher.gil.minivmac.mapper;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class WheelPromptTest {
    private byte[] sample(String typed) {
        byte[] data = new byte[80]; ByteBuffer out = ByteBuffer.wrap(data);
        out.put("PRW1".getBytes(StandardCharsets.US_ASCII));
        out.putInt(0x14000); out.putInt(0x13042); out.putInt(0x6000);
        data[16]=7; data[17]=1; data[18]=(byte)typed.length(); data[19]=6;
        System.arraycopy("JUNGLE".getBytes(StandardCharsets.US_ASCII),0,data,20,6);
        System.arraycopy(typed.getBytes(StandardCharsets.US_ASCII),0,data,28,typed.length());
        return data;
    }
    @Test public void decodesVerifiedAnswerAndTypedPrefix() {
        WheelPrompt prompt=WheelPrompt.parse(sample("JUN")); assertNotNull(prompt);
        assertEquals("JUNGLE",prompt.answer); assertEquals("JUN",prompt.typed);
        assertEquals(7,prompt.index); assertEquals(1,prompt.attempt);
        assertEquals(WheelPrompt.parse(sample("")).token(),prompt.token());
    }
    @Test public void rejectsUnknownMalformedAndNonAsciiPackets() {
        assertNull(WheelPrompt.parse(null)); assertNull(WheelPrompt.parse(new byte[79]));
        for(int at:new int[]{0,16,17,18,19,20,26,27,28,79}) {
            byte[] data=sample(""); data[at]=(byte)255; assertNull("byte "+at,WheelPrompt.parse(data));
        }
        byte[] data=sample("J"); data[28]=1; assertNull(WheelPrompt.parse(data));
        data=sample("J"); data[29]='X'; assertNull(WheelPrompt.parse(data));
    }
    @Test public void rejectsUnboundedOrUnalignedAddresses() {
        for(int offset:new int[]{4,8,12}) {
            byte[] data=sample(""); ByteBuffer.wrap(data).putInt(offset,0x2000001);
            assertNull(WheelPrompt.parse(data));
        }
        byte[] data=sample(""); ByteBuffer.wrap(data).putInt(8,0x15000); assertNull(WheelPrompt.parse(data));
    }
    @Test public void changedAttemptHasADifferentSubmissionToken() {
        byte[] data=sample(""); String before=WheelPrompt.parse(data).token();
        data[17]=2; assertNotEquals(before,WheelPrompt.parse(data).token());
    }
}
