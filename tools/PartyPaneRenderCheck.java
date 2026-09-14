import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.MapViewport;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.mapper.PartyState;

/**
 * Focused checks of the actual LiveMapView on Android software Canvas.
 * Synthetic PRP1/PRP2/PRM1 data only: not RAM-probe, combat, hardware-GPU or e-ink acceptance.
 * Reuses CompositeSheetRenderCheck's system-context harness and PartyStateTest's packet layout.
 *
 * Compile from the repository root (no Gradle or running app required):
 *   check_dir=$(mktemp -d scratch/party-pane-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/PartyPaneRenderCheck.java \
 *     "$source_dir/LiveMapView.java" "$source_dir/MapArtwork.java" \
 *     "$source_dir/notebook/NoteIcon.java" \
 *     "$source_dir/mapper/PartyState.java" "$source_dir/mapper/PartyPaneLayout.java" \
 *     "$source_dir/mapper/MapViewport.java" "$source_dir/mapper/PoolRadState.java" \
 *     "$source_dir/mapper/GeoMap.java" "$source_dir/mapper/AreaIdentity.java"
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/party-pane-check.zip" "$check_dir/classes.jar"
 *
 * Only the agent/user owning the emulator should run these next commands:
 *   adb -s emulator-5584 push "$check_dir/party-pane-check.zip" /data/local/tmp/
 *   adb -s emulator-5584 shell \
 *     'CLASSPATH=/data/local/tmp/party-pane-check.zip app_process /system/bin PartyPaneRenderCheck'
 * This creates no app window and injects no guest/device input. Touch checks below
 * call a detached synthetic View directly, never Android's input manager.
 */
public final class PartyPaneRenderCheck {
    private static final int MEMBERS = 6;
    private static final String[] NAMES = {
            "First Hero", "Second Hero", "Third Hero", "Fourth Hero", "Fifth Hero", "Sixth Hero"};
    private static final List<Bitmap> BITMAPS = new ArrayList<>();
    private static Context context;
    private static float density;
    private static int passed;

    public static void main(String[] args) {
        try { runChecks(); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static void runChecks() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        initializeSystemFonts();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        density = context.getResources().getDisplayMetrics().density;
        System.out.println("Actual LiveMapView software rendering; density=" + density);

        run("six wide-pane rows render full, half and zero health with current/max labels", () -> {
            byte[] sample = packet(true);
            LiveMapView view = view(sample, 960, 480);
            PartyPaneLayout pane = pane(view, MEMBERS);
            check(pane.columns == 1 && pane.partyLeft == pane.mapWidth, "Expected right-side party strip");
            Bitmap image = render(view);
            checkBars(image, pane, sample);
            checkDescription(view, sample);
            checkMonochrome(image);
        });

        run("damage and healing change only the party region and retain immutable snapshots", () -> {
            byte[] full = packet(false), mixed = packet(true);
            LiveMapView view = view(full, 960, 480);
            Bitmap before = render(view);
            view.showPartySample(mixed);
            Bitmap damaged = render(view);
            PartyPaneLayout pane = pane(view, MEMBERS);
            checkBars(damaged, pane, mixed);
            check(changedPixels(before, damaged) > 100, "Damage did not alter the rendered health strip");
            equalOutsideParty(before, damaged, pane);
            Arrays.fill(mixed, (byte) 0);
            equal(damaged, render(view), "Mutating the caller's packet changed the displayed snapshot");
            view.showPartySample(full);
            equal(before, render(view), "Healing did not restore the exact full-health display");
        });

        run("narrow portrait and short panes collapse the sidebar and preserve the full map", () -> {
            byte[] sample = packet(true);
            LiveMapView view = view(sample, 360, 320);
            PartyPaneLayout pane = pane(view, MEMBERS);
            check(pane.rows == 0 && pane.mapWidth == view.getWidth() && pane.mapHeight == view.getHeight(),
                    "Portrait sidebar must collapse without taking any guest/map height");
            equal(render(view(null,360,320)),render(view),"Collapsed rows left pixels in the map");
            checkDescription(view, sample);
            resize(view,960,200);
            check(pane(view,MEMBERS).rows==0,"Short wide window must not cram six rows together");
            equal(render(view(null,960,200)),render(view),"Short pane left clipped party labels");
        });

        run("actual map taps follow the resized viewport and party taps create no flag", () -> {
            LiveMapView view = view(packet(true), 960, 480);
            final int[] tapped = {-1, 0};
            view.setListener(new LiveMapView.Listener() {
                @Override public void onAreaChanged(AreaIdentity area) { }
                @Override public void onTileTapped(AreaIdentity area, int x, int y) {
                    tapped[0] = y * 16 + x; tapped[1]++;
                }
            });
            checkTapLayout(view, tapped);
            resize(view, 360, 320);
            checkTapLayout(view, tapped);
            PartyPaneLayout pane = pane(view, MEMBERS);
            MapViewport map = new MapViewport(pane.mapWidth, pane.mapHeight, density);
            event(view, MotionEvent.ACTION_DOWN, map.left + 4.5f * map.cell, map.top + 3.5f * map.cell);
            byte[] changed = packet(true); changed[24]--;
            view.showPartySample(changed); // Layout/data invalidation must cancel an in-flight map tap.
            int previous = tapped[1];
            event(view, MotionEvent.ACTION_UP, map.left + 4.5f * map.cell, map.top + 3.5f * map.cell);
            check(tapped[1] == previous, "Party refresh completed a stale map tap");
        });

        run("party reorder moves names and their corresponding bar fractions together", () -> {
            byte[] sample = packet(true);
            LiveMapView view = view(sample, 960, 480);
            Bitmap before = render(view);
            byte[] first = Arrays.copyOfRange(sample, 8, 8 + PartyState.ROW_SIZE);
            System.arraycopy(sample, 8 + 2 * PartyState.ROW_SIZE, sample, 8, PartyState.ROW_SIZE);
            System.arraycopy(first, 0, sample, 8 + 2 * PartyState.ROW_SIZE, PartyState.ROW_SIZE);
            view.showPartySample(sample);
            Bitmap reordered = render(view);
            checkBars(reordered, pane(view, MEMBERS), sample);
            checkDescription(view, sample);
            check(changedPixels(before, reordered) > 100, "Reorder left old name/bar rows displayed");
            equalOutsideParty(before, reordered, pane(view, MEMBERS));
        });

        run("null or invalid health removes stale rows and restores the full map allocation", () -> {
            LiveMapView view = view(packet(true), 960, 480);
            LiveMapView empty = view(null, 960, 480);
            Bitmap baseline = render(empty);
            view.showPartySample(null);
            equal(baseline, render(view), "Unavailable sample left old party pixels or a narrowed map");
            for (String name : NAMES) check(!view.getContentDescription().toString().contains(name),
                    "Unavailable sample left stale accessible health for " + name);
            view.showPartySample(packet(false));
            byte[] invalid = packet(true); invalid[24] = 100; // Current > verified maximum 20.
            view.showPartySample(invalid);
            equal(baseline, render(view), "Rejected HP packet left previous health displayed");
            view.showPartySample(packet(true));
            checkBars(render(view), pane(view, MEMBERS), packet(true));
        });

        run("tiny panes collapse impossible rows without clipping failures or invented HP", () -> {
            LiveMapView view = view(packet(false), 96, 72);
            PartyPaneLayout pane = pane(view, MEMBERS);
            check(pane.rows == 0 && pane.partyWidth == 0,"Tiny pane reserved unusable sidebar space");
            Bitmap before = render(view);
            view.showPartySample(packet(true));
            equal(before, render(view), "Tiny pane drew health rows that cannot fit its allocation");
            checkDescription(view, packet(true)); // Hidden visuals do not erase the accessible facts.
            resizePixels(view, 1, 1);
            Bitmap pixel = render(view);
            check(pixel.getWidth() == 1 && pixel.getHeight() == 1, "Minimum-size View allocation changed");
            checkMonochrome(pixel);
            resize(view, 960, 480);
            checkBars(render(view), pane(view, MEMBERS), packet(true));
        });

        run("party row taps select exact members and cancel on stale, drag, palm, focus or resize", () -> {
            LiveMapView view=view(packet(true),960,480);
            final List<PartyState.Member> selected=new ArrayList<>(); final int[] mapTaps={0};
            view.setListener(new LiveMapView.Listener(){
                @Override public void onAreaChanged(AreaIdentity area){}
                @Override public void onTileTapped(AreaIdentity area,int x,int y){mapTaps[0]++;}
                @Override public void onPartyMemberTapped(PartyState.Member member){selected.add(member);}
            });
            PartyPaneLayout p=pane(view,MEMBERS); float x=p.partyLeft+p.partyWidth/2f,y=p.rowTop(2)+p.rowHeight/2;
            pointer(view,MotionEvent.ACTION_DOWN,0,new int[]{7},x,y);
            pointer(view,MotionEvent.ACTION_UP,0,new int[]{7},x,y);
            check(selected.size()==1 && selected.get(0).name.equals(NAMES[2]),"Nonzero pointer ID selected wrong member");
            pointer(view,MotionEvent.ACTION_DOWN,0,new int[]{7},x,y);
            byte[] reordered=packet(true); swap(reordered,0,2);view.showPartySample(reordered);
            pointer(view,MotionEvent.ACTION_UP,0,new int[]{7},x,y);
            check(selected.size()==1,"Reordered party completed a stale row tap");
            tap(view,x,y);check(selected.size()==2 && selected.get(1).name.equals(NAMES[0]),"Fresh reordered row has wrong details");
            int expected=selected.size();
            pointer(view,MotionEvent.ACTION_DOWN,0,new int[]{7},x,y);
            pointer(view,MotionEvent.ACTION_UP,MotionEvent.FLAG_CANCELED,new int[]{7},x,y);
            check(selected.size()==expected,"FLAG_CANCELED pointer UP opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);event(view,MotionEvent.ACTION_MOVE,x+100*density,y);
            event(view,MotionEvent.ACTION_MOVE,x,y);event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"Drag out and back opened details");
            pointer(view,MotionEvent.ACTION_DOWN,0,new int[]{7},x,y);
            pointer(view,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),0,new int[]{7,12},x,y);
            pointer(view,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),0,new int[]{7,12},x,y);
            pointer(view,MotionEvent.ACTION_UP,0,new int[]{7},x,y);
            check(selected.size()==expected,"Additional pointer/palm opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);event(view,MotionEvent.ACTION_CANCEL,x,y);event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"ACTION_CANCEL opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);view.onWindowFocusChanged(false);event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"Window focus loss opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);
            visibilityCallback(view,View.GONE);visibilityCallback(view,View.VISIBLE);
            event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"Visibility callback opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);resize(view,360,320);resize(view,960,480);event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"Resize out and back opened details");
            event(view,MotionEvent.ACTION_DOWN,x,y);view.showPartySample(null);event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"Unavailable party opened details");
            check(mapTaps[0]==0,"Party gesture leaked into map-note creation");
        });

        run("named accessibility detail actions stay available when collapsed and reject stale IDs", () -> {
            LiveMapView view=view(packet(true),360,320); final List<String> selected=new ArrayList<>();
            view.setListener(new LiveMapView.Listener(){
                @Override public void onAreaChanged(AreaIdentity area){}
                @Override public void onTileTapped(AreaIdentity area,int x,int y){}
                @Override public void onPartyMemberTapped(PartyState.Member member){selected.add(member.name);}
            });
            // Directly verifies our explicit custom-action projection. Detached framework
            // attachment/selected-node behavior is not inferred from this software probe.
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();
            view.onInitializeAccessibilityNodeInfo(info);
            List<AccessibilityNodeInfo.AccessibilityAction> actions=info.getActionList();
            check(actions.size()==MEMBERS,"Expected one named action per member");
            int action=actions.get(2).getId();
            check(actions.get(2).getLabel().toString().contains(NAMES[2]),"Missing member label on accessible action");
            check(view.performAccessibilityAction(action,null) && selected.equals(Collections.singletonList(NAMES[2])),
                    "Collapsed sidebar lost accessible details");
            byte[] changed=packet(true);swap(changed,0,2);view.showPartySample(changed);
            check(!view.performAccessibilityAction(action,null) && selected.size()==1,"Stale accessibility action selected reordered member");
            info.recycle();view.showPartySample(null);info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info);
            check(info.getActionList().isEmpty(),"Unavailable party retained stale detail actions");info.recycle();
            check(!view.isFocusable(),"Details stole physical-key focus from the guest");
        });

        run("all 18 verified classes have distinct original monochrome symbols and unknown stays honest", () -> {
            byte[] sample=packet(true);LiveMapView view=view(sample,960,480);Set<Long> glyphs=new HashSet<>();
            for(int kind=-1;kind<18;kind++) {
                sample[8+19]=(byte)kind;view.showPartySample(sample);Bitmap image=render(view);
                PartyPaneLayout p=pane(view,MEMBERS);float top=p.rowTop(0)+(p.rowHeight-48*density)/2;
                long hash=1125899906842597L;int dark=0;
                for(int y=(int)(top+7*density);y<top+37*density;y++)
                    for(int x=(int)(p.partyLeft+8*density);x<p.partyLeft+38*density;x++) {
                        int pixel=image.getPixel(x,y);hash=31*hash+pixel;if(Color.red(pixel)<128)dark++;
                    }
                check(dark>10,"Blank class glyph "+kind);check(glyphs.add(hash),"Duplicate class glyph "+kind);
                checkMonochrome(image);image.recycle();BITMAPS.remove(image);
            }
            byte[] legacy=packet(true);legacy[3]='1';for(int i=0;i<MEMBERS;i++){legacy[8+i*20+18]=0;legacy[8+i*20+19]=0;}
            view.showPartySample(legacy);checkDescription(view,legacy);checkBars(render(view),pane(view,MEMBERS),legacy);
            check(view.getContentDescription().toString().contains("AC unavailable; Class unavailable"),"Legacy data invented new metadata");
        });

        run("joining and leaving change row targets together with names, bars and details", () -> {
            byte[] smaller=packet(true);smaller[4]=5;Arrays.fill(smaller,8+5*20,smaller.length,(byte)0);
            LiveMapView view=view(smaller,960,480);checkBars(render(view),pane(view,5),smaller);
            check(!view.getContentDescription().toString().contains(NAMES[5]),"Departed member remained accessible");
            view.showPartySample(packet(true));checkBars(render(view),pane(view,6),packet(true));
            checkDescription(view,packet(true));
            byte[] maximum=packet(true);maximum[24]=(byte)255;maximum[25]=(byte)255;maximum[26]=(byte)-127;
            view.showPartySample(maximum);checkBars(render(view),pane(view,6),maximum);
        });

        System.out.println("PASS " + passed + " party-pane actual Android View/software-Canvas checks; "
                + "synthetic samples only, no live-RAM/combat/GPU/e-ink/stylus acceptance.");
    }

    private static void initializeSystemFonts() throws Exception {
        // Unlike an APK, app_process never runs ActivityThread.handleBindApplication(),
        // which fills the lazy Typeface system map on modern Android. The vector-only
        // CompositeSheetRenderCheck did not need it, but default Paint text otherwise
        // aborts in native Typeface::resolveDefault (gDefaultTypeface is null).
        // Use Android's own preinstalled-font fallback, before any View/Paint creation.
        // Older releases initialize DEFAULT eagerly, so leave their font map alone.
        // AOSP: frameworks/base, android-14.0.0_r1, Typeface.java:
        // https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/graphics/java/android/graphics/Typeface.java
        if (Typeface.DEFAULT == null) {
            Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        }
        check(Typeface.DEFAULT != null, "Android default typeface did not initialize");
        System.out.println("Android system fonts initialized for standalone text rendering");
    }

    private static byte[] packet(boolean mixed) {
        byte[] sample = new byte[PartyState.PACKET_SIZE];
        sample[0]='P'; sample[1]='R'; sample[2]='P'; sample[3]='2'; sample[4]=MEMBERS;
        for (int i=0; i<MEMBERS; i++) {
            int at = 8 + i * PartyState.ROW_SIZE, maximum = 20 + i * 10;
            for (int c=0; c<NAMES[i].length(); c++) sample[at+c] = (byte) NAMES[i].charAt(c);
            sample[at+16] = (byte) (!mixed || i%3==0 ? maximum : i%3==1 ? maximum/2 : 0);
            sample[at+17] = (byte) maximum;
            sample[at+18] = (byte) new int[]{0,-1,60,-127,1,3}[i];
            sample[at+19] = (byte) i;
        }
        check(PartyState.parse(sample) != null, "Broken synthetic health fixture");
        return sample;
    }

    private static byte[] mapPacket() {
        byte[] sample = new byte[1200];
        sample[0]='P'; sample[1]='R'; sample[2]='M'; sample[3]='1';
        sample[130]=12; sample[131]=9; sample[132]=2;
        for (int tile=0; tile<256; tile++)
            sample[176+tile]=(byte)((tile%16%4==0 ? 1 : 0) | (tile/16%3==0 ? 0x10 : 0));
        return sample;
    }

    private static LiveMapView view(byte[] party, int widthDp, int heightDp) {
        LiveMapView view = new LiveMapView(context, null);
        // Detached parent is needed by the View's real touch-cancellation contract.
        FrameLayout parent = new FrameLayout(context); parent.addView(view);
        view.showSample(mapPacket()); view.showNotebook("Synthetic notebook", Collections.emptyMap());
        view.showPartySample(party); resize(view, widthDp, heightDp);
        return view;
    }

    private static int px(int dp) { return Math.max(1, Math.round(dp * density)); }

    private static void resize(LiveMapView view, int widthDp, int heightDp) {
        resizePixels(view, px(widthDp), px(heightDp));
    }

    private static void resizePixels(LiveMapView view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static PartyPaneLayout pane(LiveMapView view, int count) {
        return new PartyPaneLayout(view.getWidth(), view.getHeight(), density, count);
    }

    private static Bitmap render(LiveMapView view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        BITMAPS.add(bitmap); view.draw(new Canvas(bitmap)); return bitmap;
    }

    private static void checkBars(Bitmap bitmap, PartyPaneLayout pane, byte[] packet) {
        PartyState party = PartyState.parse(packet);
        check(party != null, "Expected a valid party fixture");
        float rowHeight = pane.rowHeight;
        Paint metrics = new Paint(Paint.ANTI_ALIAS_FLAG); metrics.setTextSize(11 * density);
        check(rowHeight >= 25 * density, "Fixture needs room for labels and bar interiors");
        for (int i=0; i<party.members.size(); i++) {
            PartyState.Member member = party.members.get(i);
            float left = pane.partyLeft + 44 * density;
            float right = pane.partyLeft + pane.partyWidth - 10 * density;
            float rowTop = pane.rowTop(i)+(rowHeight-48*density)/2;
            int y = (int) (rowTop + 37 * density);
            int start = (int) Math.ceil(left + 2 * density), end = (int) Math.floor(right - 2 * density);
            check(start >= 0 && end < bitmap.getWidth() && y < bitmap.getHeight() && end > start,
                    "Health bar is outside its View allocation: " + member.name);
            int dark=0;
            for (int x=start; x<=end; x++) if (Color.red(bitmap.getPixel(x,y)) < 32) dark++;
            float painted = dark / (float) (end-start+1);
            check(Math.abs(painted - member.healthFraction()) < .035f,
                    member.name + " expected bar " + member.healthFraction() + ", painted " + painted);
            int labelInk = 0;
            for (int yy=(int)rowTop; yy<rowTop+16*density; yy++)
                for (int x=start; x<=end; x++) if (Color.red(bitmap.getPixel(x,yy)) < 128) labelInk++;
            check(labelInk > 12, "Name/current-max label is missing for " + member.name);
            String hp = "HP " + member.currentHp + "/" + member.maxHp;
            String ac = "AC " + (member.armorClass==null ? "—" : member.armorClass);
            int hpEnd = (int)Math.ceil(left+metrics.measureText(hp)), hpInk = 0, acInk=0;
            int acStart = (int)Math.floor(right-metrics.measureText(ac));
            check(hpEnd+2*density < acStart,"HP and AC labels collide: "+hp+" / "+ac);
            for (int yy=(int)(rowTop+16*density); yy<rowTop+30*density; yy++) {
                for (int x=start; x<hpEnd; x++) if (Color.red(bitmap.getPixel(x,yy)) < 128) hpInk++;
                for (int x=acStart; x<right; x++) if (Color.red(bitmap.getPixel(x,yy)) < 128) acInk++;
            }
            check(hpInk > 8, "The visible current/max number region is blank for " + member.name);
            check(acInk > 8,"The visible AC region is blank for "+member.name);
        }
    }

    private static void checkDescription(LiveMapView view, byte[] packet) {
        String description = view.getContentDescription().toString(); int previous = -1;
        for (PartyState.Member member : PartyState.parse(packet).members) {
            String expected = member.name + ": " + member.currentHp + " of " + member.maxHp + " HP; AC "
                    +(member.armorClass==null ? "unavailable" : member.armorClass)+"; "+member.classLabel()+".";
            int at = description.indexOf(expected);
            check(at > previous, "Missing or reordered accessible current/max health: " + expected);
            previous = at;
        }
    }

    private static void checkTapLayout(LiveMapView view, int[] tapped) {
        PartyPaneLayout pane = pane(view, MEMBERS);
        MapViewport map = new MapViewport(pane.mapWidth, pane.mapHeight, density);
        check(map.cell >= 3, "Test map should remain usable beside/before the party strip");
        int previous = tapped[1];
        if(pane.rows>0) {
            tap(view, pane.partyLeft + pane.partyWidth / 2f, pane.rowTop(0)+pane.rowHeight/2);
            check(tapped[1] == previous, "Party-strip tap was interpreted as a map flag");
        }
        tap(view, map.left + 4.5f * map.cell, map.top + 3.5f * map.cell);
        check(tapped[1] == previous + 1 && tapped[0] == 3*16+4,
                "Resized map hit-testing disagrees with the rendered tile");
    }

    private static void tap(LiveMapView view, float x, float y) {
        event(view, MotionEvent.ACTION_DOWN, x, y); event(view, MotionEvent.ACTION_UP, x, y);
    }

    private static void event(LiveMapView view, int action, float x, float y) {
        long now=SystemClock.uptimeMillis(); MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);
        try { check(view.onTouchEvent(event), "Synthetic companion-pane touch escaped the View"); }
        finally { event.recycle(); }
    }

    private static void swap(byte[] packet,int first,int second) {
        byte[] row=Arrays.copyOfRange(packet,8+first*20,8+(first+1)*20);
        System.arraycopy(packet,8+second*20,packet,8+first*20,20);System.arraycopy(row,0,packet,8+second*20,20);
    }

    private static void visibilityCallback(LiveMapView view,int visibility) {
        view.setVisibility(visibility);
        // Detached View.setVisibility does not dispatch onVisibilityChanged on
        // API30: AOSP View.setFlags guards that dispatch with mAttachInfo!=null.
        // Invoke our actual callback explicitly, like the focus-loss check above.
        // This proves callback cancellation, not attached hierarchy dispatch;
        // root's real Map/Info switching check covers the latter. No fake attach.
        // https://android.googlesource.com/platform/frameworks/base/+/ed841cb/core/java/android/view/View.java
        try {
            java.lang.reflect.Method callback=LiveMapView.class.getDeclaredMethod("onVisibilityChanged",View.class,int.class);
            callback.setAccessible(true);callback.invoke(view,view,visibility);
        } catch(ReflectiveOperationException failure) { throw new AssertionError("Cannot exercise visibility callback",failure); }
    }

    private static void pointer(LiveMapView view,int action,int flags,int[] ids,float x,float y) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++) {
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=x+i*10;coords[i].y=y;coords[i].pressure=1;coords[i].size=1;
        }
        long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,ids.length,properties,coords,
                0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,flags);
        try{check(view.onTouchEvent(event),"Multipointer touch escaped the companion View");}finally{event.recycle();}
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    private static int changedPixels(Bitmap before, Bitmap after) {
        check(before.getWidth()==after.getWidth() && before.getHeight()==after.getHeight(), "Different image sizes");
        int[] first=pixels(before), second=pixels(after); int changed=0;
        for (int i=0; i<first.length; i++) if (first[i]!=second[i]) changed++;
        return changed;
    }

    private static void equalOutsideParty(Bitmap before, Bitmap after, PartyPaneLayout pane) {
        int[] first = pixels(before), second = pixels(after);
        for (int y=0; y<before.getHeight(); y++) for (int x=0; x<before.getWidth(); x++) {
            if (x>=pane.partyLeft && x<pane.partyLeft+pane.partyWidth
                    && y>=pane.partyTop && y<pane.partyTop+pane.partyHeight) continue;
            int index = y * before.getWidth() + x;
            if (first[index] != second[index]) throw new AssertionError("HP change altered map/outside pixel at " + x + "," + y);
        }
    }

    private static void checkMonochrome(Bitmap bitmap) {
        for (int color : pixels(bitmap)) check(Color.alpha(color)==255 && Color.red(color)==Color.green(color)
                && Color.green(color)==Color.blue(color), "Unexpected color or transparent pixel in monochrome pane");
    }

    private static void equal(Bitmap before, Bitmap after, String message) { check(changedPixels(before,after)==0,message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void run(String name, Runnable check) {
        try { check.run(); passed++; System.out.println("PASS " + passed + ": " + name); }
        finally { for (Bitmap bitmap : BITMAPS) bitmap.recycle(); BITMAPS.clear(); }
    }
}
