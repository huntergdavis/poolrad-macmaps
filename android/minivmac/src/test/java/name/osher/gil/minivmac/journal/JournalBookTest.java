package name.osher.gil.minivmac.journal;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class JournalBookTest {
    private static void text(DataOutputStream out,String value) throws IOException {
        byte[] b=value.getBytes("UTF-8"); out.writeInt(b.length); out.write(b);
    }
    static byte[] fixture(int blocks, byte[] image) throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(data);
        out.writeInt(0x50524a52); out.writeByte(1); text(out,"Invented test reference"); out.writeShort(99);
        for (int kind=0;kind<3;kind++) {
            int max=kind==0?58:kind==1?18:23;
            for (int n=1;n<=max;n++) {
                out.writeByte(kind); out.writeShort(kind==1?JournalBook.PROCLAMATIONS[n-1]:n);
                out.writeShort(kind==0 && n==1?blocks:1); out.writeByte(0); text(out,"Synthetic text " + kind + ":" + n);
                if (kind==0 && n==1 && blocks==2) { out.writeByte(1);out.writeInt(image.length);out.write(image); }
            }
        }
        return data.toByteArray();
    }
    private static void invalid(byte[] data) {
        try { JournalBook.read(data); fail("Invalid book accepted"); } catch (IOException expected) { }
    }
    @Test public void completeTypedReferenceSetAndNoNumberAliasing() throws Exception {
        JournalBook book=JournalBook.read(fixture(1,null));
        assertEquals(99,book.keys().size());
        assertEquals("Synthetic text 0:1",book.entry(new JournalBook.Key(0,1)).get(0).text);
        assertEquals("Synthetic text 2:1",book.entry(new JournalBook.Key(2,1)).get(0).text);
        assertNotEquals(new JournalBook.Key(0,1),new JournalBook.Key(2,1));
    }
    @Test public void rejectsUnavailableNumbersAndMalformedKeys() {
        for (String value:Arrays.asList("0:0","0:59","2:24","1:60","1:233","3:1","0:01","0:1:2","bad")) {
            try { JournalBook.Key.parse(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
        assertEquals(new JournalBook.Key(1,214),JournalBook.Key.parse("1:214"));
    }
    @Test public void rejectsTruncationTrailingContentBadMagicAndVersion() throws Exception {
        byte[] data=fixture(1,null);
        for (int n:new int[]{0,4,5,12,data.length-1}) invalid(Arrays.copyOf(data,n));
        invalid(Arrays.copyOf(data,data.length+1));
        data[4]=2; invalid(data); data[4]=1; data[0]=0; invalid(data);
    }
    @Test public void rejectsDuplicateEntryAndIncompleteSet() throws Exception {
        byte[] data=fixture(1,null);
        int header=5+4+"Invented test reference".length();
        data[header+1]=98; invalid(data);
        data=fixture(1,null);
        int first=header+2, second=first+5+1+4+"Synthetic text 0:1".length();
        data[second+2]=1; invalid(data);
    }
    @Test public void rejectsUnsafeLengthsBlockCountsAndUtf8() throws Exception {
        byte[] data=fixture(1,null); ByteBuffer.wrap(data,5,4).putInt(Integer.MAX_VALUE); invalid(data);
        data=fixture(1,null); data[9]=(byte)0xff; invalid(data);
        invalid(fixture(0,null)); invalid(fixture(33,null));
    }
    @Test public void pngDimensionsBoundBeforeAndroidDecodingAndBlocksImmutable() throws Exception {
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jFZkAAAAASUVORK5CYII=");
        JournalBook book=JournalBook.read(fixture(2,png));
        List<JournalBook.Block> blocks=book.entry(new JournalBook.Key(0,1));
        assertTrue(blocks.get(1).isImage());
        byte[] copy=blocks.get(1).image(); copy[0]=0; assertEquals((byte)0x89,blocks.get(1).image()[0]);
        try { blocks.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        ByteBuffer.wrap(png,16,4).putInt(100000); invalid(fixture(2,png));
        invalid(fixture(2,new byte[33]));
    }
    @Test public void streamIsBoundedWithoutTrustingAvailable() throws Exception {
        byte[] fixture=fixture(1,null);
        ByteArrayInputStream input=new ByteArrayInputStream(fixture) { @Override public int available() { return 0; } };
        assertArrayEquals(fixture,JournalBook.readBytes(input));
        InputStream huge=new InputStream() {
            int n;
            public int read() { return n++<=JournalBook.MAX_BYTES?1:-1; }
            public int read(byte[] b,int off,int len) {
                if (n>JournalBook.MAX_BYTES)return -1;
                int count=Math.min(len,JournalBook.MAX_BYTES+1-n);Arrays.fill(b,off,off+count,(byte)1);n+=count;return count;
            }
        };
        try { JournalBook.readBytes(huge);fail(); } catch(IOException expected) { }
    }
    @Test public void historyDeduplicatesBoundsAndSurvivesSerialization() {
        JournalHistory a=new JournalHistory("","");
        for(int n=1;n<=30;n++)a.opened(new JournalBook.Key(0,n));
        assertEquals(20,a.recent().size());a.opened(new JournalBook.Key(0,20));
        assertEquals(new JournalBook.Key(0,20),a.recent().get(0));
        a.toggle(new JournalBook.Key(1,59));a.toggle(new JournalBook.Key(2,1));
        JournalHistory b=new JournalHistory(a.recentValue(),a.bookmarkValue());
        assertEquals(a.recent(),b.recent());assertEquals(a.bookmarks(),b.bookmarks());
        b.toggle(new JournalBook.Key(1,59));assertFalse(b.bookmarked(new JournalBook.Key(1,59)));
        assertTrue(a.bookmarked(new JournalBook.Key(1,59)));
    }
    @Test public void corruptHistoryIsNotSilentlyOverwritten() {
        for(String value:Arrays.asList("0:1,0:1","0:1,","1:1","future-format")) {
            try { new JournalHistory(value,"");fail(value); } catch(IllegalArgumentException expected) { }
        }
    }
}
