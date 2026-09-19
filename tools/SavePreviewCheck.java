package name.osher.gil.minivmac;

import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** Real Android PNG codec check; only synthetic frames in a caller-supplied temporary directory. */
public final class SavePreviewCheck {
    public static void main(String[] args) throws Exception {
        File directory=new File(args[0]);
        if (directory.exists()) throw new IOException("Use a new temporary directory");
        SaveStateStore store=new SaveStateStore(directory);
        int[] pixels=new int[384*288];
        for(int y=0;y<288;y++) for(int x=0;x<384;x++) pixels[y*384+x]=x<192?0xff000000:0xffffffff;
        byte[] state=new byte[1000];state[5]=42;
        File save=store.writeQuick(state,1790000000123L);
        if(SavePreview.read(save)!=null) throw new AssertionError("Invented legacy preview");
        long start=android.os.SystemClock.elapsedRealtime();
        store.writePreview(save,SavePreview.encode(pixels,384,288));
        long elapsed=android.os.SystemClock.elapsedRealtime()-start;
        Bitmap image=SavePreview.read(save);
        if(image==null||image.getWidth()!=384||image.getHeight()!=288)throw new AssertionError("PNG dimensions");
        int[] decoded=new int[pixels.length];image.getPixels(decoded,0,384,0,0,384,288);image.recycle();
        if(!Arrays.equals(pixels,decoded))throw new AssertionError("Preview pixels changed");
        if(!Arrays.equals(state,store.read(save)))throw new AssertionError("Preview changed machine state");
        try {SavePreview.encode(pixels,100000,100000);throw new AssertionError("Accepted oversized raster");}catch(IOException expected){}
        store.writePreview(save,new byte[]{(byte)137,'P','N','G',13,10,26,10});
        if(SavePreview.read(save)!=null)throw new AssertionError("Accepted truncated PNG");
        if(!Arrays.equals(state,store.read(save)))throw new AssertionError("Damaged preview blocked valid state");
        store.delete(save);
        if(SaveStateStore.previewFile(save).exists())throw new AssertionError("Orphan preview");
        System.out.println("PASS Android preview: exact pixels, size bounds, missing/corrupt fallback, independent state and cleanup; PNG write="+elapsed+"ms");
    }
}
