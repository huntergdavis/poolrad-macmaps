import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.osher.gil.minivmac.InkSheetView;
import name.osher.gil.minivmac.NotePageImage;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.InkSheetLayout;
import name.osher.gil.minivmac.notebook.NoteIcon;

/**
 * Synthetic, real Android View/software-Canvas checks; no device input, files or guest fixture.
 * Reuses CompositeSheetRenderCheck's detached View and PartyPaneRenderCheck's font bootstrap.
 * From repository root using Bash:
 *   check_dir=$(mktemp -d scratch/note-image-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/NotePageImageCheck.java \
 *     "$source_dir"/{NotePageImage,InkSheetView,MapArtwork}.java \
 *     "$source_dir"/notebook/{InkNote,InkHistory,InkSheetLayout,InkViewport,NoteIcon}.java \
 *     "$source_dir"/mapper/{PoolRadState,GeoMap,AreaIdentity}.java
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/note-image-check.zip" "$check_dir/classes.jar"
 * Only the emulator owner runs:
 *   adb -s emulator-5580 push "$check_dir/note-image-check.zip" /data/local/tmp/
 *   adb -s emulator-5580 shell \
 *     'CLASSPATH=/data/local/tmp/note-image-check.zip app_process /system/bin NotePageImageCheck'
 */
public final class NotePageImageCheck {
    private static final int PAGE_LEFT=32,PAGE_TOP=128,PAGE_WIDTH=1536,PAGE_HEIGHT=608;
    private static final int TILE_X=4,TILE_Y=3;
    private static final List<Bitmap> BITMAPS=new ArrayList<>();
    private static final PoolRadState MAP=snapshot();
    private static Context context;
    private static int passed;

    public static void main(String[] args) {
        try { runChecks(); }
        catch(Throwable failure) { failure.printStackTrace(System.err);System.exit(1); }
    }
    private static void runChecks() throws Exception {
        if(Looper.getMainLooper()==null) Looper.prepareMainLooper();
        if(Typeface.DEFAULT==null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        check(Typeface.DEFAULT!=null,"Missing system font map");
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Object thread=activityThread.getMethod("systemMain").invoke(null);
        context=(Context)activityThread.getMethod("getSystemContext").invoke(thread);

        run("readable 1600-pixel page has title/metadata, full map, white writing space and grayscale pixels",()->{
            Bitmap page=export(InkNote.empty(),MAP,symbols(NoteIcon.TEMPLE),"Notebook Alpha");
            check(page.getWidth()==1600 && page.getHeight()==768,"Unexpected export size");
            check(darkPixels(page,32,8,1568,48)>300,"Notebook title is missing");
            check(darkPixels(page,32,52,1568,87)>300,"Area/tile/symbol metadata is missing");
            check(darkPixels(page,32,89,1568,115)>150,"Pinned-map explanation is missing");
            InkSheetLayout layout=pageLayout();
            int x=(int)(PAGE_LEFT+layout.toX(.75f)),y=(int)(PAGE_TOP+layout.toY(.5f));
            check(page.getPixel(x,y)==Color.WHITE,"Unused right writing half is not white");
            check(darkPixels(page,(int)(PAGE_LEFT+layout.mapLeft),(int)(PAGE_TOP+layout.mapTop),
                    (int)(PAGE_LEFT+layout.mapLeft+layout.mapSize),
                    (int)(PAGE_TOP+layout.mapTop+layout.mapSize))>1000,"Full map did not render");
            for(int pixel:pixels(page)) check(Color.alpha(pixel)==255 && Color.red(pixel)==Color.green(pixel)
                    && Color.green(pixel)==Color.blue(pixel),"Export includes colored or transparent pixels");
            check(page.getPixel(0,0)==Color.WHITE && page.getPixel(1599,767)==Color.WHITE,"Page margins are not white");
        });

        run("export body exactly reuses the fitted editor and title changes remain in the header",()->{
            InkNote note=note(line(false,.025f));Map<Integer,NoteIcon> symbols=symbols(NoteIcon.INN);
            Bitmap exported=export(note,MAP,symbols,"Notebook Alpha");
            InkSheetView reference=view(note,MAP,symbols,PAGE_WIDTH,PAGE_HEIGHT);
            reference.setBackgroundColor(Color.WHITE);reference.fitPage();
            Bitmap fitted=render(reference);
            int[] body=new int[PAGE_WIDTH*PAGE_HEIGHT];
            exported.getPixels(body,0,PAGE_WIDTH,PAGE_LEFT,PAGE_TOP,PAGE_WIDTH,PAGE_HEIGHT);
            check(Arrays.equals(body,pixels(fitted)),"Image body diverged from the actual fitted editor");
            Bitmap changed=export(note,MAP,symbols,"Notebook Beta");
            check(differences(exported,changed,0,0,1600,PAGE_TOP)>50,"Notebook title did not affect image");
            check(differences(exported,changed,0,PAGE_TOP,1600,768)==0,"Title change affected map or ink");
        });

        run("cross-sheet erasing restores geometry and markers exactly, not a white map",()->{
            Map<Integer,NoteIcon> symbols=symbols(NoteIcon.TREASURE);
            Bitmap base=export(InkNote.empty(),MAP,symbols,"Notebook Alpha");
            Bitmap ink=export(note(line(false,.035f)),MAP,symbols,"Notebook Alpha");
            check(differences(base,ink,0,PAGE_TOP,1600,768)>1000,"Cross-sheet handwriting did not appear");
            Bitmap erased=export(note(line(false,.035f),line(true,.15f)),MAP,symbols,"Notebook Alpha");
            equal(base,erased,"Erasing damaged the underlying map, protected symbols or paper");
        });

        run("all nine symbols remain distinct and an empty symbol map gains only a rendered flag",()->{
            InkSheetLayout layout=pageLayout();
            int centerX=(int)(PAGE_LEFT+layout.mapTileX(TILE_X+.5f));
            int centerY=(int)(PAGE_TOP+layout.mapTileY(TILE_Y+.5f));
            int radius=(int)(layout.mapSize/32);
            Set<Integer> signatures=new HashSet<>();
            for(NoteIcon icon:NoteIcon.values()) {
                Bitmap page=export(InkNote.empty(),MAP,symbols(icon),"Notebook Alpha");
                int[] marker=new int[(2*radius)*(2*radius)];
                page.getPixels(marker,0,2*radius,centerX-radius,centerY-radius,2*radius,2*radius);
                check(signatures.add(Arrays.hashCode(marker)),"Duplicate/missing rendered symbol "+icon);
                release(page);
            }
            check(signatures.size()==9,"Expected all nine manual symbols");
            Map<Integer,NoteIcon> empty=new HashMap<>();
            Bitmap automatic=export(InkNote.empty(),MAP,empty,"Notebook Alpha");
            equal(automatic,export(InkNote.empty(),MAP,symbols(NoteIcon.FLAG),"Notebook Alpha"),
                    "An empty flag page did not export its selected default flag");
            check(empty.isEmpty(),"Rendering inserted a flag into the caller's symbol map");
        });

        run("PNG bytes round-trip losslessly and missing map/long title remain readable",()->{
            Bitmap original=export(note(line(false,.02f)),MAP,symbols(NoteIcon.SMITHY),"Notebook Alpha");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            check(original.compress(Bitmap.CompressFormat.PNG,100,out),"PNG compression failed");
            byte[] encoded=out.toByteArray();
            check(encoded.length>8 && encoded[0]==(byte)137 && encoded[1]=='P' && encoded[2]=='N' && encoded[3]=='G',
                    "Encoded image is not a PNG");
            Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length);
            check(decoded!=null,"PNG decoder rejected image");BITMAPS.add(decoded);
            equal(original,decoded,"PNG round-trip changed page pixels");
            Bitmap fallback=export(InkNote.empty(),null,null,null);
            StringBuilder longName=new StringBuilder();for(int i=0;i<60;i++) longName.append("A very long notebook name ");
            Bitmap longTitle=export(InkNote.empty(),null,null,longName+"\nSecond line must not enter the page");
            check(darkPixels(fallback,32,8,1568,115)>600,"Missing map/title erased the readable header");
            check(differences(fallback,longTitle,0,PAGE_TOP,1600,768)==0,"Long title leaked into the writing page");
        });

        editorIsolationCheck();
        System.out.println("PASS "+passed+" note-image Android software checks; synthetic data, no tablet/GPU acceptance.");
    }

    private static void editorIsolationCheck() {
        run("export leaves zoomed live editor, unfinished preview, committed ink and redo untouched",()->{
            Map<Integer,NoteIcon> symbols=symbols(NoteIcon.HIDDEN_WALL);
            byte[] geometry=MAP.map.copyData();
            InkNote initial=note(line(false,.025f),new InkNote.Stroke(false,.02f,new float[]{.7f,.4f}));
            InkSheetView live=view(initial,MAP,symbols,900,450);
            live.undo();live.setPenOnly(true);
            int[] changes={0};live.setOnChangeListener(()->changes[0]++);
            Input input=new Input(live);input.pinch();
            float zoom=live.zoomFactor();check(zoom>1,"Live editor fixture did not zoom");
            check(live.canRedo(),"Live editor fixture has no redo history");
            input.pen(MotionEvent.ACTION_DOWN,430,240);
            input.pen(MotionEvent.ACTION_MOVE,570,275);
            InkNote committed=live.getNote();Bitmap before=render(live);
            Bitmap output=export(committed,MAP,symbols,"Notebook Alpha");
            equal(output,export(note(line(false,.025f)),MAP,symbols,"Notebook Alpha"),
                    "Unfinished live-editor preview leaked into exported committed ink");
            equal(before,render(live),"Export altered the live editor framing or unfinished preview");
            check(live.zoomFactor()==zoom && live.canRedo() && changes[0]==0,"Export changed zoom/history or notified autosave");
            sameNote(committed,live.getNote());
            check(initial.strokes().size()==2,"Export mutated the original loaded note");
            check(symbols.size()==1 && symbols.get(TILE_Y*16+TILE_X)==NoteIcon.HIDDEN_WALL,"Export mutated caller symbols");
            check(Arrays.equals(geometry,MAP.map.copyData()),"Export changed pinned map data");
            live.cancelActiveStroke();
        });
    }

    private static final class Input {
        final InkSheetView view;long time=SystemClock.uptimeMillis(),downTime;
        Input(InkSheetView view) { this.view=view; }
        void pinch() {
            event(MotionEvent.ACTION_DOWN,new int[]{7},MotionEvent.TOOL_TYPE_FINGER,new float[]{390,225});
            event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    new int[]{7,13},MotionEvent.TOOL_TYPE_FINGER,new float[]{390,225,510,225});
            event(MotionEvent.ACTION_MOVE,new int[]{7,13},MotionEvent.TOOL_TYPE_FINGER,new float[]{330,225,570,225});
            event(MotionEvent.ACTION_MOVE,new int[]{7,13},MotionEvent.TOOL_TYPE_FINGER,new float[]{270,225,630,225});
            event(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    new int[]{7,13},MotionEvent.TOOL_TYPE_FINGER,new float[]{270,225,630,225});
            event(MotionEvent.ACTION_UP,new int[]{7},MotionEvent.TOOL_TYPE_FINGER,new float[]{270,225});
        }
        void pen(int action,float x,float y) { event(action,new int[]{21},MotionEvent.TOOL_TYPE_STYLUS,new float[]{x,y}); }
        void event(int action,int[] ids,int tool,float[] xy) {
            time+=20;if((action&MotionEvent.ACTION_MASK)==MotionEvent.ACTION_DOWN) downTime=time;
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
            MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
            for(int i=0;i<ids.length;i++) {
                properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=tool;
                coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[i*2];coords[i].y=xy[i*2+1];coords[i].pressure=1;
            }
            MotionEvent event=MotionEvent.obtain(downTime,time,action,ids.length,properties,coords,0,0,1,1,0,0,
                    tool==MotionEvent.TOOL_TYPE_STYLUS?InputDevice.SOURCE_STYLUS:InputDevice.SOURCE_TOUCHSCREEN,0);
            try { check(view.onTouchEvent(event),"Synthetic editor event escaped the detached view"); }
            finally { event.recycle(); }
        }
    }
    private static PoolRadState snapshot() {
        byte[] sample=new byte[1200];sample[0]='P';sample[1]='R';sample[2]='M';sample[3]='1';
        sample[130]=12;sample[131]=9;sample[132]=2;
        for(int tile=0;tile<256;tile++)
            sample[176+tile]=(byte)((tile%16%4==0?1:0)|(tile/16%3==0?0x10:0));
        return PoolRadState.parse(sample);
    }
    private static Map<Integer,NoteIcon> symbols(NoteIcon selected) {
        Map<Integer,NoteIcon> symbols=new HashMap<>();symbols.put(TILE_Y*16+TILE_X,selected);return symbols;
    }
    private static InkNote.Stroke line(boolean erase,float width) {
        return new InkNote.Stroke(erase,width,new float[]{erase?.05f:.1f,.5f,erase?.95f:.9f,.5f});
    }
    private static InkNote note(InkNote.Stroke... strokes) { return new InkNote(Arrays.asList(strokes)); }
    private static InkSheetLayout pageLayout() {
        return new InkSheetLayout(PAGE_WIDTH,PAGE_HEIGHT,6*context.getResources().getDisplayMetrics().density);
    }
    private static InkSheetView view(InkNote note,PoolRadState map,Map<Integer,NoteIcon> symbols,int w,int h) {
        InkSheetView view=new InkSheetView(context);view.setMap(map,symbols,TILE_X,TILE_Y);view.setNote(note);
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        view.layout(0,0,w,h);return view;
    }
    private static Bitmap render(InkSheetView view) {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        BITMAPS.add(bitmap);view.draw(new Canvas(bitmap));return bitmap;
    }
    private static Bitmap export(InkNote note,PoolRadState map,Map<Integer,NoteIcon> symbols,String title) {
        Bitmap bitmap=NotePageImage.render(context,note,map,symbols,TILE_X,TILE_Y,title);BITMAPS.add(bitmap);return bitmap;
    }
    private static int[] pixels(Bitmap bitmap) {
        int[] result=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(result,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return result;
    }
    private static int darkPixels(Bitmap bitmap,int left,int top,int right,int bottom) {
        int width=right-left,height=bottom-top;int[] region=new int[width*height];
        bitmap.getPixels(region,0,width,left,top,width,height);int dark=0;
        for(int color:region) if(Color.red(color)<128) dark++;return dark;
    }
    private static int differences(Bitmap a,Bitmap b,int left,int top,int right,int bottom) {
        check(a.getWidth()==b.getWidth() && a.getHeight()==b.getHeight(),"Different image sizes");
        int w=right-left,h=bottom-top;int[] first=new int[w*h],second=new int[w*h];
        a.getPixels(first,0,w,left,top,w,h);b.getPixels(second,0,w,left,top,w,h);int changed=0;
        for(int i=0;i<first.length;i++) if(first[i]!=second[i]) changed++;return changed;
    }
    private static void equal(Bitmap a,Bitmap b,String message) {
        check(differences(a,b,0,0,a.getWidth(),a.getHeight())==0,message);
    }
    private static void sameNote(InkNote a,InkNote b) {
        check(a.strokes().size()==b.strokes().size(),"Committed stroke count changed");
        for(int i=0;i<a.strokes().size();i++) {
            InkNote.Stroke first=a.strokes().get(i),second=b.strokes().get(i);
            check(first.eraser()==second.eraser() && first.width()==second.width()
                    && Arrays.equals(first.points(),second.points()),"Committed ink changed during export");
        }
    }
    private static void release(Bitmap bitmap) { BITMAPS.remove(bitmap);bitmap.recycle(); }
    private static void check(boolean okay,String message) { if(!okay) throw new AssertionError(message); }
    private static void run(String name,Runnable test) {
        try { test.run();passed++;System.out.println("PASS "+passed+": "+name); }
        finally { for(Bitmap bitmap:BITMAPS) bitmap.recycle();BITMAPS.clear(); }
    }
}
