package name.osher.gil.minivmac.journal;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class MessageHistoryTest {
    public static GameMessage message(String text, boolean truncated) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        byte[] packet = new byte[GameMessage.PACKET_SIZE];
        packet[0]='P'; packet[1]='R'; packet[2]='T'; packet[3]='1'; packet[4]=1;
        packet[5]=(byte)(truncated?1:0); packet[6]=(byte)(bytes.length>>>8); packet[7]=(byte)bytes.length;
        System.arraycopy(bytes,0,packet,8,bytes.length);
        return GameMessage.parse(packet);
    }
    private MessageHistory read(byte[] data) throws IOException {
        return MessageHistory.read(new DataInputStream(new ByteArrayInputStream(data)));
    }

    @Test public void repeatedPollsAndUnavailableReadingsDoNotDuplicateMessages() {
        MessageHistory h=new MessageHistory();
        assertTrue(h.observe(message("A closed door.",false),100));
        assertFalse(h.observe(message("A closed door.",false),200));
        assertFalse(h.observe(null,300));
        assertFalse(h.observe(message("A closed door.",false),400));
        assertEquals(1,h.entries().size());
        assertEquals(100,h.entries().get(0).observedAt);
    }

    @Test public void growingTextUpdatesTheReadingButARealClearSeparatesRepeats() {
        MessageHistory h=new MessageHistory();
        h.observe(message("You hear",false),10);
        h.observe(message("You hear footsteps.",false),20);
        assertEquals(1,h.entries().size());
        assertEquals("You hear footsteps.",h.entries().get(0).text);
        assertEquals(10,h.entries().get(0).observedAt);
        h.observe(message("",false),30);
        h.observe(message("You hear footsteps.",false),40);
        assertEquals(2,h.entries().size());
        assertEquals(40,h.entries().get(1).observedAt);
    }

    @Test public void rotationKeepsLatestTwoHundredAndMarksTruncation() throws Exception {
        MessageHistory h=new MessageHistory();
        for(int i=0;i<205;i++) h.observe(message("Message "+i+".",i==204),i);
        assertEquals(200,h.entries().size());
        assertEquals("Message 5.",h.entries().get(0).text);
        MessageHistory restored=read(h.encode());
        assertTrue(restored.entries().get(199).truncated);
        assertEquals("Message 204.",restored.entries().get(199).text);
        assertFalse(restored.observe(message("Message 204.",true),300));
        assertTrue(h.encode().length<MessageHistory.MAX_BYTES);
    }

    @Test public void returnedEntriesCannotMutateHistoryAndTruncationChangesAreKept() {
        MessageHistory h=new MessageHistory();
        h.observe(message("Sample",false),1);
        assertTrue(h.observe(message("Sample",true),2));
        assertEquals(1,h.entries().size());
        assertTrue(h.entries().get(0).truncated);
        try { h.entries().clear(); fail(); } catch(UnsupportedOperationException expected) { }
        assertEquals(1,h.entries().size());
    }

    @Test public void malformedCountsTextFlagsTimesAndTrailingBytesAreRefused() throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(b); out.writeInt(201);
        refuse(b.toByteArray());
        for(int scenario=0;scenario<5;scenario++) {
            b.reset(); out.writeInt(1); out.writeLong(scenario==0?-1:1);
            out.writeByte(scenario==1?2:0);
            out.writeUTF(scenario==2?"bad\ntext":scenario==3?"x".repeat(513):"valid");
            if(scenario==4) out.writeByte(0);
            refuse(b.toByteArray());
        }
        MessageHistory h=new MessageHistory(); h.observe(message("Valid",false),1);
        byte[] encoded=h.encode();
        refuse(java.util.Arrays.copyOf(encoded,encoded.length-1));
    }
    private void refuse(byte[] bytes) throws Exception {
        try { read(bytes); fail("Malformed history accepted"); } catch(IOException expected) { }
    }
}
