import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.osher.gil.minivmac.MapArtwork;
import name.osher.gil.minivmac.mapper.GeoMap;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.ExplorationTrail;
import name.osher.gil.minivmac.notebook.NoteIcon;

/**
 * Actual Android software Canvas checks; invented geometry and observed-step fixtures only.
 * No device input, app window, game files, RAM mutation, or physical/GPU acceptance.
 * Reuses CompositeSheetRenderCheck's framework harness and original MapArtwork renderer.
 *
 * From repo root:
 *   check_dir=$(mktemp -d scratch/exploration-render.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/ExplorationRenderCheck.java \
 *     "$source_dir/MapArtwork.java" "$source_dir/notebook/ExplorationTrail.java" \
 *     "$source_dir/notebook/NoteIcon.java" "$source_dir/mapper/GeoMap.java" \
 *     "$source_dir/mapper/PoolRadState.java" "$source_dir/mapper/AreaIdentity.java"
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/exploration-render-check.zip" "$check_dir/classes.jar"
 * Root/user owning the isolated test emulator alone runs:
 *   adb -s emulator-5584 push "$check_dir/exploration-render-check.zip" /data/local/tmp/
 *   adb -s emulator-5584 shell \
 *     'CLASSPATH=/data/local/tmp/exploration-render-check.zip app_process /system/bin ExplorationRenderCheck'
 */
public final class ExplorationRenderCheck {
    private static final int CELL=40, PAD=20, SIZE=PAD*2+CELL*16, DEST=8*16+8;
    private static final MapArtwork ART=new MapArtwork();
    private static final List<Bitmap> BITMAPS=new ArrayList<>();
    private static int passed;

    public static void main(String[] args) {
        try {
            if(Looper.getMainLooper()==null) Looper.prepareMainLooper();
            if(Typeface.DEFAULT==null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
            run("visited stipple marks only observed tiles and clear-trail retains coverage",ExplorationRenderCheck::coverage);
            run("two-foot glyphs rotate from real north/east/south/west movement",ExplorationRenderCheck::directions);
            run("latest recorded travel survives anchors; gaps never fabricate paths",ExplorationRenderCheck::latestAndGaps);
            run("fog rejects all hidden geometry, including adjacent hidden-side doors",ExplorationRenderCheck::fog);
            run("visited walls and doors remain above footprints",ExplorationRenderCheck::geometryAboveFeet);
            run("manual symbols and party markers retain their opaque foreground pixels",ExplorationRenderCheck::markers);
            run("small maps, clipping, translation and scale preserve canvas ownership",ExplorationRenderCheck::bounds);
            run("original note geometry remains unchanged after live exploration rendering",ExplorationRenderCheck::legacyGeometry);
            run("door bits without wall surfaces never create phantom edges",ExplorationRenderCheck::noPhantomDoors);
            run("all real doorway states use the same neutral symbol",ExplorationRenderCheck::neutralDoors);
            run("a door seen from one side and not gone through is marked, and only then",ExplorationRenderCheck::unwalkedExits);
            run("older footprints are drawn smaller, and standing still changes nothing",ExplorationRenderCheck::footprintAges);
            run("a square where a fight started is marked, and only that square",ExplorationRenderCheck::ambushMarks);
            System.out.println("PASS "+passed+" exploration Android software-Canvas checks; synthetic data only, no live/GPU/e-ink acceptance.");
        } catch(Throwable failure) {failure.printStackTrace(System.err);System.exit(1);}
    }

    private static void coverage() {
        GeoMap map=map(new byte[1024]);ExplorationTrail trail=arriving(DEST+16,DEST);
        Bitmap seen=render(map,trail,false,false),empty=render(map,ExplorationTrail.empty(),false,false);
        check(dark(tile(seen,DEST,2))>4,"Visited tile has no stipple");
        check(dark(tile(empty,DEST,2))==0,"Empty trail marks a tile as visited");
        equalTile(seen,empty,DEST-2,"Unobserved tile received visited stipple");
        equal(seen,render(map,trail.clearTrail(),false,true),"Clear trail removed visited coverage");
        check(trail.visitedCount()==2&&trail.steps.size()==2,"Rendering mutated the trail");monochrome(seen);
    }

    private static void directions() {
        GeoMap map=map(new byte[1024]);int[] from={DEST+16,DEST-1,DEST-16,DEST+1};
        int[] expected=null;Set<Integer> distinct=new HashSet<>();
        for(int direction=0;direction<4;direction++) {
            ExplorationTrail trail=arriving(from[direction],DEST);
            Bitmap image=render(map,trail,false,true),without=render(map,trail,false,false);
            int[] actual=tile(image,DEST,2);
            check(changes(actual,tile(without,DEST,2))>80,"Missing pair of feet for direction "+direction);
            check(distinct.add(Arrays.hashCode(actual)),"Different movement directions have identical footprints");
            if(expected==null)expected=actual;
            else {
                expected=clockwise(expected,CELL-4);
                check(changes(expected,actual)<=48,"Footprint rotation disagrees with movement direction "+direction);
            }
        }
    }

    private static int[] clockwise(int[] before,int width) {
        int[] after=new int[before.length];
        for(int y=0;y<width;y++)for(int x=0;x<width;x++)after[y*width+x]=before[(width-1-x)*width+y];
        return after;
    }

    private static void latestAndGaps() {
        GeoMap map=map(new byte[1024]);
        ExplorationTrail loop=arriving(DEST+16,DEST).record(DEST+1,DEST).record(DEST,DEST+1);
        Bitmap latest=render(map,loop,false,true);
        equalTile(latest,render(map,arriving(DEST+1,DEST),false,true),DEST,"Old and new arrival glyphs piled up on one tile");
        ExplorationTrail anchor=loop.record(DEST,-1);
        equal(latest,render(map,anchor,false,true),"Stationary observation gap erased a genuine recorded footprint");
        ExplorationTrail anchorOnly=ExplorationTrail.empty().record(DEST,-1).record(DEST,-1);
        equal(render(map,anchorOnly,false,true),render(map,anchorOnly,false,false),"Anchor-only tile invented a travel direction");
        ExplorationTrail unknownReturn=loop.record(DEST-50,-1).record(DEST,-1);
        equalTile(latest,render(map,unknownReturn,false,true),DEST,"Unknown return replaced the last recorded travel direction");
        equal(render(map,anchor,false,false),render(map,anchor.clearTrail(),false,true),
                "Clear trail retained footprint pixels after a stationary anchor");
        ExplorationTrail jump=arriving(DEST+16,DEST).record(DEST-50,DEST);
        equalTile(render(map,jump,false,true),render(map,jump,false,false),DEST-50,"Unobserved jump invented footprints");
        ExplorationTrail wrapped=ExplorationTrail.empty().record(15,-1).record(16,15);
        equalTile(render(map,wrapped,false,true),render(map,wrapped,false,false),16,"Row wrapping invented an adjacent step");
        check(loop.record(DEST,DEST)==loop,"Repeated position sample appended a false movement");
    }

    private static void fog() {
        byte[] hidden=new byte[1024];Arrays.fill(hidden,(byte)0xff);
        for(int plane=0;plane<4;plane++)hidden[plane*256+DEST]=0;
        ExplorationTrail trail=ExplorationTrail.empty().record(DEST,-1);
        Bitmap blank=render(map(new byte[1024]),trail,true,false);
        equal(blank,render(map(hidden),trail,true,false),"Unvisited wall/door data leaked through fog");
        check(changes(pixels(blank),pixels(render(map(hidden),trail,false,false)))>500,"Full-map option failed to reveal geometry");
        byte[] adjacent=new byte[1024];adjacent[DEST]=1;adjacent[768+DEST+1]=(byte)0xc0;
        Bitmap wall=render(map(adjacent),trail,true,false);
        int x=PAD+(DEST%16+1)*CELL,y=PAD+(DEST/16)*CELL+CELL/2;
        check(Color.red(wall.getPixel(x,y))<32,"Hidden neighbor's door erased a known wall");
        Bitmap reused=render(map(hidden),trail,false,false);
        ART.drawExploration(new Canvas(reused),map(hidden),ExplorationTrail.empty(),true,false,PAD,PAD,CELL,1);
        equal(render(map(hidden),ExplorationTrail.empty(),true,false),reused,"Fog left previously drawn hidden geometry behind");
    }

    private static void geometryAboveFeet() {
        byte[] geometry=new byte[1024];geometry[DEST]=0x11;geometry[256+DEST]=0x11;geometry[768+DEST]=0x55;
        GeoMap map=map(geometry);ExplorationTrail trail=arriving(DEST+16,DEST);
        Bitmap feet=render(map,trail,true,true),without=render(map,trail,true,false);
        int[] first=tile(feet,DEST,0),second=tile(without,DEST,0);
        for(int y=0;y<CELL;y++)for(int x=0;x<CELL;x++)if(x<6||x>=CELL-6||y<6||y>=CELL-6)
            check(first[y*CELL+x]==second[y*CELL+x],"Footprint obscured a wall or door outline");
        check(changes(first,second)>60,"No footprint inside the known walled tile");
        int x=PAD+(DEST%16)*CELL+CELL/2,y=PAD+(DEST/16)*CELL;
        check(feet.getPixel(x,y)==Color.WHITE,"Visited door opening was closed by trail artwork");
    }

    private static void noPhantomDoors() {
        byte[] geometry=new byte[1024];Arrays.fill(geometry,768,1024,(byte)0xff);
        GeoMap blank=map(new byte[1024]),bitsOnly=map(geometry);
        ExplorationTrail trail=arriving(DEST+16,DEST);
        for(boolean fog:new boolean[]{false,true})
            equal(render(blank,trail,fog,true),render(bitsOnly,trail,fog,true),"Phantom door from unused bits");
        Bitmap plain=bitmap(SIZE,SIZE,Color.WHITE),actual=bitmap(SIZE,SIZE,Color.WHITE);
        ART.drawGeometry(new Canvas(plain),blank,PAD,PAD,CELL,1);
        ART.drawGeometry(new Canvas(actual),bitsOnly,PAD,PAD,CELL,1);
        equal(plain,actual,"Flag-note map invented a doorway from unused bits");
    }

    private static void neutralDoors() {
        byte[] geometry=new byte[1024];geometry[DEST]=0x35;geometry[256+DEST]=0x7a;
        ExplorationTrail trail=ExplorationTrail.empty().record(DEST,-1);
        Bitmap baseline=null;
        for(int state=1;state<=3;state++) {
            geometry[768+DEST]=(byte)(state*85);
            Bitmap actual=render(map(geometry),trail,true,false);
            if(baseline==null)baseline=actual;
            else equal(baseline,actual,"Door state advertised an unverified lock/secret/passability claim");
        }
    }

    private static void ambushMarks() {
        /*
         * Drawn from what happened to this party: a square is marked because a
         * fight started there, and a neighbouring square is not. Marking the
         * same square twice must look the same as marking it once -- the mark
         * says a fight happened here, not how many.
         */
        byte[] geometry=new byte[1024];
        GeoMap map=map(geometry);
        ExplorationTrail walked=arriving(DEST+16,DEST);
        Bitmap plain=render(map,walked,false,false);
        ExplorationTrail jumped=walked.recordAmbush(DEST);
        Bitmap marked=render(map,jumped,false,false);

        check(dark(tile(marked,DEST,2))>dark(tile(plain,DEST,2)),
                "The square where the fight started drew no mark");
        equalTile(plain,marked,DEST+1,"A neighbouring square was marked too");
        equalTile(plain,marked,DEST-1,"A neighbouring square was marked too");

        equal(marked,render(map,jumped.recordAmbush(DEST),false,false),
                "Marking the same square twice drew something different");

        // It is a fact about the place: forgetting the route keeps it.
        check(dark(tile(render(map,jumped.clearTrail(),false,false),DEST,2))>0,
                "Clearing the trail forgot where the fight was");

        // Too small to draw a creature rather than a smudge: draw nothing.
        Bitmap tinyPlain=bitmap(SIZE,SIZE,Color.WHITE),tinyMarked=bitmap(SIZE,SIZE,Color.WHITE);
        ART.drawExploration(new Canvas(tinyPlain),map,walked,false,false,PAD,PAD,6,1);
        ART.drawExploration(new Canvas(tinyMarked),map,jumped,false,false,PAD,PAD,6,1);
        equal(tinyPlain,tinyMarked,"A cell too small for the mark drew one anyway");
        monochrome(marked);
    }

    private static void footprintAges() {
        /*
         * A long walk should not draw identical prints: the earliest square of
         * the walk carries less ink than the latest. Measured on the tiles
         * themselves rather than on a scale factor, so it is the drawing being
         * checked and not the arithmetic.
         */
        byte[] geometry=new byte[1024];
        GeoMap map=map(geometry);
        int first=DEST, second=DEST+1, third=DEST+2, fourth=DEST+3;
        ExplorationTrail walk=ExplorationTrail.empty().record(first,-1)
                .record(second,first).record(third,second).record(fourth,third);
        Bitmap drawn=render(map,walk,false,true);
        int oldest=dark(tile(drawn,second,2)), newest=dark(tile(drawn,fourth,2));
        check(oldest>0 && newest>0,"A walked square drew no footprint at all");
        check(newest>oldest,"The newest footprint should carry more ink than the oldest: "
                + oldest + " then " + newest);

        // Standing still is an observation, not travel, and must change nothing.
        ExplorationTrail stood=walk.record(fourth,-1).record(fourth,-1);
        equal(drawn,render(map,stood,false,true),"Standing still resized the footprints");

        // And walking back over a square makes it the newest again.
        ExplorationTrail back=walk.record(third,fourth);
        check(dark(tile(render(map,back,false,true),third,2))>dark(tile(drawn,third,2)),
                "Walking a square again did not make its footprint the newest");
        monochrome(drawn);
    }

    private static void unwalkedExits() {
        /*
         * DEST has a real doorway east. Standing on DEST and never on the
         * square beyond it must draw something extra; walking through must take
         * it away again; and never standing on DEST at all must draw nothing,
         * because the party has not seen that door.
         */
        byte[] geometry=new byte[1024];
        geometry[DEST]=0x05;                 // an east wall surface on DEST
        geometry[768+DEST]=0x04;             // with door bits over it (direction 1)
        GeoMap map=map(geometry);

        // The same wall with no door bits: a wall is never an exit, so whatever
        // this map does not draw and the other does is the mark itself.
        byte[] walled=new byte[1024]; walled[DEST]=0x05;
        GeoMap wall=map(walled);

        ExplorationTrail beside=ExplorationTrail.empty().record(DEST,-1);
        ExplorationTrail through=beside.record(DEST+1,DEST);
        ExplorationTrail elsewhere=ExplorationTrail.empty().record(0,-1);

        int besideDiff=changes(tile(render(map,beside,false,false),DEST+1,2),
                               tile(render(wall,beside,false,false),DEST+1,2));
        int throughDiff=changes(tile(render(map,through,false,false),DEST+1,2),
                                tile(render(wall,through,false,false),DEST+1,2));
        int unseenDiff=changes(tile(render(map,elsewhere,false,false),DEST+1,2),
                               tile(render(wall,elsewhere,false,false),DEST+1,2));

        check(besideDiff>throughDiff,
                "The mark survived the party walking through the door: "
                + besideDiff + " beside vs " + throughDiff + " through");
        check(besideDiff>unseenDiff,
                "A door nobody has stood beside was marked as much as one they had: "
                + besideDiff + " beside vs " + unseenDiff + " unseen");

        Bitmap marked=render(map,beside,false,false);
        equalTile(marked,render(map,beside,false,false),DEST,"Unstable rendering of the same state");

        /*
         * Too small to be an arrow rather than a smudge, so nothing is drawn.
         * Measured the same way: at this size having a door beside you should
         * differ from having a wall by exactly as much as having already walked
         * through it does -- that is, by the doorway symbol alone.
         */
        check(smallDiff(map,wall,beside)==smallDiff(map,wall,through),
                "A cell too small to draw an arrow drew one anyway");
        monochrome(marked);
    }

    private static int smallDiff(GeoMap door,GeoMap wall,ExplorationTrail trail) {
        Bitmap withDoor=bitmap(SIZE,SIZE,Color.WHITE),withWall=bitmap(SIZE,SIZE,Color.WHITE);
        ART.drawExploration(new Canvas(withDoor),door,trail,false,false,PAD,PAD,6,1);
        ART.drawExploration(new Canvas(withWall),wall,trail,false,false,PAD,PAD,6,1);
        return changes(pixels(withDoor),pixels(withWall));
    }

    private static void markers() {
        byte[] geometry=new byte[1024];ExplorationTrail trail=arriving(DEST+16,DEST);
        Bitmap composed=render(map(geometry),trail,true,true),foreground=bitmap(SIZE,SIZE,Color.TRANSPARENT);
        Map<Integer,NoteIcon> flags=new HashMap<>();flags.put(5*16+5,NoteIcon.FLAG);flags.put(DEST+1,NoteIcon.TREASURE);
        ART.drawMarkers(new Canvas(foreground),flags,state(geometry),true,PAD,PAD,CELL,1);
        ART.drawMarkers(new Canvas(composed),flags,state(geometry),true,PAD,PAD,CELL,1);
        int[] mask=pixels(foreground),actual=pixels(composed);int opaque=0;
        for(int i=0;i<mask.length;i++)if(Color.alpha(mask[i])==255){opaque++;check(mask[i]==actual[i],"Exploration obscured an opaque marker pixel");}
        check(opaque>100,"Marker fixture is missing foreground ink/halos");
        check(dark(tile(composed,5*16+5,3))>20,"Manually placed flag disappeared on an unvisited tile");
        monochrome(composed);
    }

    private static void bounds() {
        GeoMap map=map(new byte[1024]);ExplorationTrail trail=arriving(DEST+16,DEST);
        Bitmap tiny=bitmap(40,40,Color.WHITE),plain=bitmap(40,40,Color.WHITE);
        ART.drawExploration(new Canvas(tiny),map,trail,true,true,3,3,2,1);
        ART.drawExploration(new Canvas(plain),map,trail,true,false,3,3,2,1);
        equal(tiny,plain,"Tiny map drew unreadable footprints");monochrome(tiny);
        int untouched=Color.rgb(21,62,93);Bitmap image=bitmap(300,300,untouched);Canvas canvas=new Canvas(image);
        canvas.translate(20,30);canvas.scale(.75f,.75f);canvas.clipRect(12,14,220,225);
        Matrix before=new Matrix();canvas.getMatrix(before);Rect clip=canvas.getClipBounds();
        ART.drawExploration(canvas,map,trail,true,true,10,12,16,1);
        Matrix after=new Matrix();canvas.getMatrix(after);float[] a=new float[9],b=new float[9];before.getValues(a);after.getValues(b);
        check(Arrays.equals(a,b),"Exploration changed caller's matrix");check(clip.equals(canvas.getClipBounds()),"Exploration changed caller's clipping");
        int painted=0;
        for(int y=0;y<300;y++)for(int x=0;x<300;x++) {
            if(x<29||x>=185||y<40||y>=199)check(image.getPixel(x,y)==untouched,"Exploration painted outside its transformed clip at "+x+","+y);
            else if(image.getPixel(x,y)!=untouched)painted++;
        }
        check(painted>1000,"Transformed map did not draw");
    }

    private static void legacyGeometry() {
        byte[] geometry=new byte[1024];Arrays.fill(geometry,0,512,(byte)0x11);geometry[768+DEST]=0x55;
        GeoMap map=map(geometry);Bitmap before=bitmap(SIZE,SIZE,Color.WHITE),after=bitmap(SIZE,SIZE,Color.WHITE);
        ART.drawGeometry(new Canvas(before),map,PAD,PAD,CELL,1);
        render(map,arriving(DEST+16,DEST),true,true);
        ART.drawGeometry(new Canvas(after),map,PAD,PAD,CELL,1);
        equal(before,after,"Live exploration state changed unfiltered note-sheet geometry");
    }

    private static GeoMap map(byte[] geometry) {return state(geometry).map;}
    private static PoolRadState state(byte[] geometry) {
        byte[] sample=new byte[1200];sample[0]='P';sample[1]='R';sample[2]='M';sample[3]='1';
        sample[130]=(byte)(DEST%16);sample[131]=(byte)(DEST/16);sample[132]=2;
        System.arraycopy(geometry,0,sample,176,1024);return PoolRadState.parse(sample);
    }
    private static ExplorationTrail arriving(int from,int to) {
        return ExplorationTrail.empty().record(from,-1).record(to,from);
    }
    private static Bitmap bitmap(int width,int height,int color) {
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);image.eraseColor(color);BITMAPS.add(image);return image;
    }
    private static Bitmap render(GeoMap map,ExplorationTrail trail,boolean fog,boolean feet) {
        Bitmap image=bitmap(SIZE,SIZE,Color.WHITE);
        ART.drawExploration(new Canvas(image),map,trail,fog,feet,PAD,PAD,CELL,1);return image;
    }
    private static int[] tile(Bitmap image,int tile,int margin) {
        int size=CELL-2*margin;int[] pixels=new int[size*size];
        image.getPixels(pixels,0,size,PAD+tile%16*CELL+margin,PAD+tile/16*CELL+margin,size,size);return pixels;
    }
    private static int[] pixels(Bitmap image) {
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return pixels;
    }
    private static int changes(int[] first,int[] second) {
        check(first.length==second.length,"Different image sizes");int count=0;
        for(int i=0;i<first.length;i++)if(first[i]!=second[i])count++;return count;
    }
    private static void equal(Bitmap first,Bitmap second,String message){check(changes(pixels(first),pixels(second))==0,message);}
    private static void equalTile(Bitmap first,Bitmap second,int tile,String message){check(changes(tile(first,tile,2),tile(second,tile,2))==0,message);}
    private static int dark(int[] pixels){int count=0;for(int value:pixels)if(Color.red(value)<180)count++;return count;}
    private static void monochrome(Bitmap image){for(int p:pixels(image))check(Color.alpha(p)==255&&Color.red(p)==Color.green(p)&&Color.green(p)==Color.blue(p),"Non-monochrome map pixel");}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void run(String name,Runnable test){try{test.run();passed++;System.out.println("PASS "+passed+": "+name);}finally{for(Bitmap image:BITMAPS)image.recycle();BITMAPS.clear();}}
}
