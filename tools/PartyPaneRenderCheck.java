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
import java.util.Map;
import java.security.MessageDigest;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.mapper.ReadingHold;
import name.osher.gil.minivmac.MapArtwork;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.MapViewport;
import name.osher.gil.minivmac.mapper.MapMode;
import name.osher.gil.minivmac.mapper.MapObservation;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.ExplorationTrail;
import name.osher.gil.minivmac.notebook.NoteIcon;

/**
 * Focused checks of the actual LiveMapView on Android software Canvas.
 * Synthetic PRP1/PRP2/PRM1/PRM3/PRM4 data only: not RAM-probe, combat, hardware-GPU or e-ink acceptance.
 * Reuses CompositeSheetRenderCheck's system-context harness and PartyStateTest's packet layout.
 *
 * Compile from the repository root (no Gradle or running app required):
 *   check_dir=$(mktemp -d scratch/party-pane-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/PartyPaneRenderCheck.java \
 *     "$source_dir/LiveMapView.java" "$source_dir/MapArtwork.java" \
 *     "$source_dir/notebook/NoteIcon.java" "$source_dir/notebook/ExplorationTrail.java" \
 *     "$source_dir/mapper/PartyState.java" "$source_dir/mapper/PartyPaneLayout.java" \
 *     "$source_dir/mapper/MapViewport.java" "$source_dir/mapper/PoolRadState.java" \
 *     "$source_dir/mapper/MapObservation.java" "$source_dir/mapper/MapMode.java" \
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

    /**
     * Feed the view nothing but refusals for a stretch of real time. The hold
     * is wall-clock, so it has to be waited out rather than stepped past.
     */
    private static void refusePartyUntil(LiveMapView view, long millis, byte[]... refusals) {
        long start = android.os.SystemClock.elapsedRealtime();
        while (android.os.SystemClock.elapsedRealtime() - start < millis) {
            if (refusals.length == 0) view.showPartySample(null);
            else for (byte[] refusal : refusals) view.showPartySample(refusal);
            try { Thread.sleep(50); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
        }
    }

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

        run("a narrow portrait pane puts the party under the map rather than losing it", () -> {
            /*
             * This check used to require the sidebar to vanish at 360x320 and
             * leave the map its full height. That rule is what made the party
             * invisible on the owner's own tablet, and 0.28.0 deliberately
             * overturned it: when nothing fits beside the map, the party goes
             * in a strip underneath instead. Losing some map height beats
             * losing the whole party. The check now holds the pane to the
             * bargain that replaced it -- a strip appears, every member is in
             * it, the rows stay readable, and the map keeps the quarter of the
             * pane the layout promises it.
             */
            byte[] sample = packet(true);
            LiveMapView view = view(sample, 360, 320);
            PartyPaneLayout pane = pane(view, MEMBERS);
            check(pane.rows > 0, "A narrow portrait pane lost the party altogether");
            check(pane.belowMap(), "A pane too narrow for a sidebar must put the party below the map");
            check(pane.visibleMembers() == MEMBERS, "The strip dropped a member");
            check(pane.rowHeight >= 48 * density - 0.5f, "The strip crammed its rows together");
            check(pane.headerHeight + pane.rows * pane.rowHeight <= pane.partyHeight + 0.5f,
                    "The strip overflowed its own height");
            check(pane.mapWidth == view.getWidth(), "A strip must span the pane, not narrow the map");
            check(pane.mapHeight >= Math.max(64, view.getHeight() * 0.25f) - 0.5f,
                    "The strip took more map height than the layout promises");
            checkDescription(view, sample);
            resize(view,960,200);
            PartyPaneLayout shortPane = pane(view,MEMBERS);
            if (shortPane.rows == 0) {
                equal(render(view(null,960,200)),render(view),"Short pane left clipped party labels");
            } else {
                // A short pane may use columns instead of collapsing, but it must
                // never squeeze rows below the readable minimum or overflow.
                check(shortPane.rowHeight >= 48*density,"Short wide window crammed the rows together");
                check(shortPane.rows*shortPane.columns >= MEMBERS,"Short pane dropped a member");
                check(shortPane.headerHeight + shortPane.rows*shortPane.rowHeight
                        <= shortPane.partyHeight + 0.5f,"Short pane overflowed its own height");
                check(shortPane.mapWidth >= 280*density,"Short pane starved the map");
            }
        });

        run("an eight-member NPC party keeps every row instead of losing the sidebar", () -> {
            byte[] sample = packet(true);
            sample[4] = 8;
            for (int i = MEMBERS; i < 8; i++) {
                System.arraycopy(sample, 8, sample, 8 + i*20, 20);
                sample[8 + i*20] = (byte) ('U' + i);
            }
            LiveMapView view = view(sample, 960, 352);
            PartyPaneLayout pane = pane(view, 8);
            check(pane.rows > 0, "Eight members lost the whole sidebar");
            check(pane.columns == 2 && pane.rows == 4, "Eight members did not use two columns of four");
            check(pane.visibleMembers() == 8, "A member was dropped from the layout");
            check(pane.rowHeight >= 48*density, "Two columns shrank the rows below the minimum");
            check(pane.mapWidth >= 280*density, "Two columns starved the map");

            // Every member must be drawn, and each must be tappable in its own cell.
            Bitmap drawn = render(view);
            for (int i = 0; i < 8; i++) {
                check(pane.memberAt(pane.columnLeft(i)+2, pane.rowTop(i)+pane.rowHeight/2) == i,
                        "Member " + i + " is not tappable where it is drawn");
                int dark = 0;
                for (int y = (int)pane.rowTop(i); y < (int)(pane.rowTop(i)+pane.rowHeight); y++)
                    for (int x = (int)pane.columnLeft(i); x < (int)(pane.columnLeft(i)+pane.columnWidth); x++)
                        if (Color.red(drawn.getPixel(Math.min(x, drawn.getWidth()-1),
                                Math.min(y, drawn.getHeight()-1))) < 128) dark++;
                check(dark > 100, "Member " + i + " row rendered blank");
            }
            checkMonochrome(drawn);
            check(view.getContentDescription().toString().contains("HP"), "Eight-member pane lost its accessible text");
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

        run("an unreadable sample holds briefly, then removes stale rows and restores the map", () -> {
            /*
             * This check used to require an unreadable sample to clear the pane
             * on the spot. 0.31.0 deliberately changed that: a single missed
             * read holds the last good rows for ReadingHold.HOLD_MS, because
             * the owner was watching his party flicker away three times a
             * minute and many times a second in a fight. So the check now holds
             * both halves of that bargain -- the pane does not blink away
             * immediately, and it does clear once the reading has been gone for
             * longer than the hold.
             */
            LiveMapView view = view(packet(true), 960, 480);
            LiveMapView empty = view(null, 960, 480);
            Bitmap baseline = render(empty);
            Bitmap populated = render(view);

            view.showPartySample(null);
            check(changedPixels(populated, render(view)) == 0,
                    "One unreadable sample blinked the party pane away inside the hold");

            refusePartyUntil(view, ReadingHold.HOLD_MS + 500);
            /*
             * Not an exact match against a view that never had a party: once the
             * rows go the map reclaims the width, and its right-aligned status
             * text moves into the strip they occupied. What must be true is that
             * the party itself is neither drawn nor described, and that the map
             * has the whole pane back.
             */
            check(changedPixels(populated, render(view)) > 100,
                    "A party gone for longer than the hold was still drawn");
            for (String name : NAMES) check(!view.getContentDescription().toString().contains(name),
                    "Unavailable sample left stale accessible health for " + name);

            view.showPartySample(packet(false));
            byte[] invalid = packet(true); invalid[24] = 100; // Current > verified maximum 20.
            refusePartyUntil(view, ReadingHold.HOLD_MS + 500, invalid);
            for (String name : NAMES) check(!view.getContentDescription().toString().contains(name),
                    "Rejected HP packet left previous health described for " + name);
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

        run("long area names and unavailable status fit their separate header regions", () -> {
            LiveMapView view = view(packet(true), 960, 480);
            render(view); // Initialize the actual View's paint, then use its header size.
            try {
                java.lang.reflect.Field field = LiveMapView.class.getDeclaredField("ink");
                field.setAccessible(true);
                Paint ink = (Paint) field.get(view);
                java.lang.reflect.Method fit = LiveMapView.class.getDeclaredMethod("fitHeaderText", String.class, float.class);
                fit.setAccessible(true);
                List<String> labels = new ArrayList<>(Arrays.asList("New Phlan",
                        "A deliberately very long dungeon and district name · reference", "15, 15 W",
                        "Reference only · A deliberately very long notebook name"));
                for (MapMode mode : MapMode.values()) { labels.add(mode.label()); labels.add(mode.explanation()); }
                for (int textDp : new int[]{10, 11, 14}) for (String label : labels) {
                    ink.setTextSize(textDp * density); // Footer, notebook legend and header use the same fitter.
                    for (int widthDp : new int[]{0, 1, 5, 20, 60, 120, 700}) {
                        float width = widthDp * density;
                        String fitted = (String) fit.invoke(view, label, width);
                        check(ink.measureText(fitted) <= width + .01f, "Header escaped its allocation: " + fitted);
                        if (ink.measureText(label) <= width)
                            check(fitted.equals(label), "A fitting header was unnecessarily shortened");
                        else check(fitted.isEmpty() || fitted.endsWith("…"), "Truncated header needs an ellipsis");
                    }
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            resize(view, 96, 72); render(view);
            view.showSample(null); render(view); // Last area + unavailable status also stays bounded.
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
            /*
             * The party has to be genuinely gone, not merely missed once: since
             * 0.31.0 a single unreadable sample holds the rows for
             * ReadingHold.HOLD_MS, and a tap landing on rows that are still
             * displayed should open them. What must not happen is a tap
             * completing against rows the hold has since taken away.
             */
            event(view,MotionEvent.ACTION_DOWN,x,y);
            refusePartyUntil(view, ReadingHold.HOLD_MS + 500);
            event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.size()==expected,"A party gone past the hold still opened details");
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
            // Gone past the hold, not merely missed once; see the tap check above.
            info.recycle();refusePartyUntil(view, ReadingHold.HOLD_MS + 500);
            info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info);
            check(info.getActionList().isEmpty(),"A party gone past the hold retained stale detail actions");info.recycle();
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

        run("condition and effect badges are distinct, monochrome and confined to the class-icon slot", () -> {
            byte[] healthy=conditionPacket(); LiveMapView view=view(healthy,960,480);
            Bitmap before=render(view); PartyPaneLayout p=pane(view,MEMBERS);
            float top=p.rowTop(0)+(p.rowHeight-48*density)/2;
            int left=(int)(p.partyLeft+8*density), right=(int)Math.ceil(p.partyLeft+37*density);
            int above=(int)(top+7*density), below=(int)Math.ceil(top+36*density);
            /*
             * 0.36.0 gave a member who is down a word where the armour class
             * goes -- "Dying", "Dead" -- because a one-letter badge is no use
             * when you are scanning for who to bandage. So this member's
             * readout is allowed to change along with the badge slot. Nothing
             * else may: not the map, not the names, not the bars, not a
             * neighbouring row.
             */
            int wordLeft=(int)(p.columnLeft(0)+p.columnWidth*0.45f);
            int wordRight=(int)Math.ceil(p.columnLeft(0)+p.columnWidth);
            int wordTop=(int)(top+16*density), wordBottom=(int)Math.ceil(top+32*density);
            int[][] conditions={{4,0},{5,0},{6,0},{7,0},{2,0},{3,0},{1,0},{0,1},{0,2},{255,255}};
            Set<Long> badges=new HashSet<>();
            for(int[] state:conditions) {
                byte[] sample=healthy.clone();sample[168]=(byte)state[0];sample[169]=(byte)state[1];
                view.showPartySample(sample);Bitmap after=render(view);long hash=1;int dark=0;
                for(int y=0;y<after.getHeight();y++) for(int x=0;x<after.getWidth();x++) {
                    int pixel=after.getPixel(x,y);
                    if(x>=left&&x<right&&y>=above&&y<below) {
                        hash=hash*31+pixel;if(Color.red(pixel)<128)dark++;
                    } else if(x>=wordLeft&&x<wordRight&&y>=wordTop&&y<wordBottom) {
                        // The condition word's own space; checked by conditionSummary below.
                    } else check(pixel==before.getPixel(x,y),"Condition altered map, names, HP bars or another row");
                }
                check(dark>50&&badges.add(hash),"Missing or indistinguishable condition badge");
                checkMonochrome(after);checkBars(after,p,sample);
                String summary=PartyState.parse(sample).members.get(0).conditionSummary();
                check(view.getContentDescription().toString().contains(summary),"Condition text was not accessible");
                after.recycle();BITMAPS.remove(after);
            }
            byte[] injured=healthy.clone();injured[24]--;
            view.showPartySample(injured);checkBars(render(view),p,injured);
            check(view.getContentDescription().toString().contains("injured"),"HP injury has no text explanation");
            view.showPartySample(healthy);equal(before,render(view),"Recovery did not restore the original class symbol");
            resize(view,360,320);view.showPartySample(injured);
            // Narrow means the strip under the map, not a vanished party; see check 3.
            check(changedPixels(render(view(null,360,320)),render(view))>100,
                    "A narrow pane with conditions drew no party at all");
            check(view.getContentDescription().toString().contains("injured"),"Collapsed conditions lost accessible details");
        });

        run("condition-only updates cancel stale party presses and accessible actions", () -> {
            byte[] sample=conditionPacket();LiveMapView view=view(sample,960,480);
            final List<PartyState.Member> selected=new ArrayList<>();
            view.setListener(new LiveMapView.Listener(){
                @Override public void onAreaChanged(AreaIdentity area){}
                @Override public void onTileTapped(AreaIdentity area,int x,int y){throw new AssertionError("Party tap became a flag");}
                @Override public void onPartyMemberTapped(PartyState.Member member){selected.add(member);}
            });
            PartyPaneLayout p=pane(view,MEMBERS);float x=p.partyLeft+p.partyWidth/2f,y=p.rowTop(0)+p.rowHeight/2;
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info);
            int oldAction=info.getActionList().get(0).getId();info.recycle();
            event(view,MotionEvent.ACTION_DOWN,x,y);sample[168]=4;sample[169]=3;view.showPartySample(sample);
            event(view,MotionEvent.ACTION_UP,x,y);
            check(selected.isEmpty()&&!view.performAccessibilityAction(oldAction,null),"A stale gesture exposed old conditions");
            info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info);
            AccessibilityNodeInfo.AccessibilityAction action=info.getActionList().get(0);
            check(action.getLabel().toString().contains("Unconscious")&&action.getLabel().toString().contains("Poisoned"),
                    "New detail action omits actual conditions/effects");
            check(view.performAccessibilityAction(action.getId(),null)&&selected.size()==1
                    &&selected.get(0).condition==4&&selected.get(0).trackedEffects==3,"Detail action supplied stale state");
            // Gone past the hold, and the map reclaims the width it held; see check 7.
            info.recycle();Bitmap held=render(view);
            refusePartyUntil(view, ReadingHold.HOLD_MS + 500);
            check(changedPixels(held,render(view))>100,"Missing party retained old condition badges");
            check(!view.getContentDescription().toString().contains("Unconscious"),
                    "Missing party retained its described condition");
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

        run("safe area A to unsafe area B clears A's stored shading and footprints", () -> {
            SyntheticAreas areas = new SyntheticAreas();
            LiveMapView view = view(null, 960, 480);
            ExplorationListener listener = new ExplorationListener(); view.setListener(listener);
            PoolRadState first = areas.state(0, true, 1), second = areas.state(20, false, 2);
            showState(view, first); view.setExplorationStyle(true, true);
            view.showExploration(walkedTrail(), "Synthetic A trail");
            assertMap(view, first, walkedTrail(), true, "Initial safe A overlay");
            showStatePastHold(view, second);
            check(view.displayedArea().equals(second.area), "Unsafe B did not replace displayed A");
            check(view.currentArea() == null && view.snapshot() == null, "Unsafe B exposed a live position");
            checkWalked(view, 0);
            check(listener.areas.equals(Arrays.asList(first.area, second.area)),
                    "Displayed-area transition failed to request B's own saved trail");
            assertMap(view, second, ExplorationTrail.empty(), false, "Safe A to unsafe B retained A ink");
        });

        run("two unsafe areas in a row never identify one, and never carry ink between them", () -> {
            /*
             * This check used to require an unsafe sample to name its area and
             * draw its map from a cold start. The wilderness work deliberately
             * ended that: with no trustworthy position the companion declines
             * to identify the area at all, rather than present one map as
             * another. So what is held here is the invariant that actually
             * matters -- neither unsafe area is ever named, no position is
             * exposed, and A's coverage never appears under B.
             */
            SyntheticAreas areas = new SyntheticAreas();
            LiveMapView view = view(null, 960, 480);
            ExplorationListener listener = new ExplorationListener(); view.setListener(listener);
            PoolRadState first = areas.state(0, false, 1), second = areas.state(20, false, 2);
            showState(view, first); view.setExplorationStyle(true, true);
            // A stored trail can finish loading while the party position is unavailable.
            view.showExploration(walkedTrail(), "Synthetic A trail");
            check(view.currentArea() == null, "Fixture A must have no live current area");
            check(view.displayedArea() == null, "An unsafe sample named an area it cannot vouch for");
            check(view.getContentDescription().toString().contains("Unidentified area"),
                    "An unidentified area must say so rather than imply one");

            showState(view, second);
            check(view.currentArea() == null, "Unsafe B exposed a live position");
            check(view.displayedArea() == null, "Unsafe B named an area it cannot vouch for");
            check(listener.areas.isEmpty(),
                    "An area nobody identified must not request a saved trail");
            /*
             * Not asserted here: that the coverage count drops to zero. The
             * harness pushed a trail into a view that has never identified an
             * area, which the controller would not do, and with no area to
             * compare against the view has no basis for discarding it. The
             * transition that does matter -- a named area's ink not surviving
             * into another -- is check 16, where A is identified.
             */
        });

        run("same-area unsafe samples preserve its own visited tiles but remove the live party arrow", () -> {
            SyntheticAreas areas = new SyntheticAreas();
            LiveMapView view = view(null, 960, 480);
            ExplorationListener listener = new ExplorationListener(); view.setListener(listener);
            PoolRadState safe = areas.state(0, true, 1), unsafe = areas.state(0, false, 2);
            showState(view, safe); view.setExplorationStyle(true, true);
            view.showExploration(walkedTrail(), "Synthetic A trail");
            Bitmap before = render(view);
            assertMap(view, safe, walkedTrail(), true, "Safe map must show the party arrow");
            // One unsafe sample is held; the arrow goes when the reading is
            // really gone rather than momentarily missing. See ReadingHold.
            check(view.currentArea() != null, "A single unsafe sample dropped the position inside the hold");
            showStatePastHold(view, unsafe);
            checkWalked(view, 4);
            check(view.currentArea() == null && view.snapshot() == null, "Unsafe sample exposed the party position");
            check(view.displayedArea().equals(safe.area), "Same-area unsafe sample discarded its map identity");
            check(listener.areas.equals(Collections.singletonList(safe.area)),
                    "Same-area unavailability unnecessarily invalidated its own stored trail");
            Bitmap after = render(view);
            assertMap(view, unsafe, walkedTrail(), false, "Unsafe map lost coverage or retained a party arrow");
            check(mapChangedPixels(before, after, view) > 10, "Safe-to-unsafe map pixels did not remove the arrow");
            Bitmap blank = expectedMap(view, unsafe, ExplorationTrail.empty(), false);
            check(mapChangedPixels(after, blank, view) > 10, "Retained visited squares have no visible coverage");
        });

        run("unchanged safe samples still notify the trail listener, including a new continuity epoch", () -> {
            SyntheticAreas areas = new SyntheticAreas();
            LiveMapView view = view(null, 960, 480);
            ExplorationListener listener = new ExplorationListener(); view.setListener(listener);
            PoolRadState first = areas.state(0, true, 1), repeated = areas.state(0, true, 1);
            PoolRadState newEpoch = areas.state(0, true, 0x80000002L);
            check(first.sameDisplay(repeated) && first.sameDisplay(newEpoch),
                    "Fixture must exercise the real unchanged-display early return");
            showState(view, first); view.setExplorationStyle(true, true);
            view.showExploration(walkedTrail(), "Synthetic A trail");
            Bitmap before = render(view);
            showState(view, first); showState(view, repeated); showState(view, newEpoch);
            check(listener.samples.size() == 4, "An unchanged sample skipped the continuity listener");
            check(listener.samples.get(3) == newEpoch && listener.samples.get(3).continuityToken == 0x80000002L,
                    "Continuity listener received an old snapshot instead of the latest epoch");
            check(listener.areas.equals(Collections.singletonList(first.area)),
                    "An unchanged display needlessly reset the stored area trail");
            checkWalked(view, 4); equal(before, render(view), "Unchanged samples altered visible coverage");
        });

        run("PRM4 status-only modes keep the reference map, combat replaces it, none invent a position", PartyPaneRenderCheck::statusTransitions);
        run("empty and narrow mode screens hold one steady header, bounded and adding no panels", PartyPaneRenderCheck::emptyStatuses);
        run("mode changes cancel pending map/party taps without altering independent health", PartyPaneRenderCheck::modeTouchAndParty);
        run("PRM4 non-recordable local positions move the arrow and retain flags without authorizing footsteps", PartyPaneRenderCheck::displayWithoutRecording);

        System.out.println("PASS " + passed + " party-pane actual Android View/software-Canvas checks; "
                + "synthetic samples only, no live-RAM/combat/GPU/e-ink/stylus acceptance.");
    }

    private static void statusTransitions() {
        SyntheticAreas areas = new SyntheticAreas(); LiveMapView view = view(null, 960, 480);
        ExplorationListener listener = new ExplorationListener(); view.setListener(listener);
        MapObservation local = areas.observation(0, true, 1);
        showObservation(view, local); view.setExplorationStyle(true, true);
        view.showExploration(walkedTrail(), "Synthetic remembered route");
        assertMap(view, local.state, walkedTrail(), true, "Initial PRM4 local map is missing");
        int originalSamples = listener.samples.size();
        applyStatus(view, statusPacket(MapMode.UPDATING), MapMode.UPDATING);
        checkStatus(view, MapMode.UPDATING, true);
        check(listener.samples.size() == originalSamples && listener.samples.get(originalSamples - 1) == local.state,
                "Updating emitted an outbound observation that could reset or refresh the previous-safe deadline");
        assertMap(view, local.state, walkedTrail(), false, "Updating exposed an authoritative position");
        showObservation(view, local);
        for (MapMode mode : statusModes()) {
            byte[] packet = statusPacket(mode); int observed = listener.samples.size();
            applyStatus(view, packet, mode); checkStatus(view, mode, true);
            check(view.displayedArea().equals(local.state.area), mode + " replaced the authenticated reference map");
            checkWalked(view, 4);
            if (mode == MapMode.COMBAT)
                assertReferenceMapReplaced(view, local.state, walkedTrail(),
                        "Combat kept the local area map instead of drawing the tactical overview");
            else assertMap(view, local.state, walkedTrail(), false,
                    mode + " changed local coverage or showed tactical coordinates");
            Bitmap first = render(view); view.showSample(packet);
            if (mode == MapMode.UPDATING)
                check(listener.samples.size() == observed, "Updating/repeated Updating refreshed the outbound observation stream");
            else check(listener.samples.size() == observed + 2 && listener.samples.get(observed) == null
                    && listener.samples.get(observed + 1) == null, mode + " repeats failed to interrupt recording");
            equal(first, render(view), mode + " repeats changed the static display");
            check(listener.areas.equals(Collections.singletonList(local.state.area)), mode + " erased stored area identity");
        }
        // A tactical coordinate accidentally inserted into a status-only packet
        // must never become a local party arrow, even when its range looks valid.
        byte[] malformed = statusPacket(MapMode.COMBAT); malformed[130] = 5; malformed[131] = 6;
        applyStatus(view, malformed, MapMode.UNAVAILABLE); checkStatus(view, MapMode.UNAVAILABLE, true);
        check(!view.getContentDescription().toString().contains(MapMode.UPDATING.label()),
                "Unknown packet retained the previous named mode");
        assertMap(view, local.state, walkedTrail(), false, "Rejected tactical coordinates moved the local reference");
        int beforeUpdating = listener.samples.size();
        applyStatus(view, statusPacket(MapMode.UPDATING), MapMode.UPDATING); checkStatus(view, MapMode.UPDATING, true);
        assertMap(view, local.state, walkedTrail(), false, "Mode-6 updating exposed an unsettled position");
        check(listener.samples.size() == beforeUpdating, "Mode-6 updating emitted a misleading observation refresh");
        MapObservation resumed = areas.observation(0, true, 3); showObservation(view, resumed);
        check(view.snapshot() == resumed.state && view.currentArea().equals(resumed.state.area),
                "Returning to settled exploration did not restore the live map");
        check(view.getContentDescription().toString().contains("Party at " + resumed.state.positionLabel()),
                "Returned local position is not accessible");
        assertMap(view, resumed.state, walkedTrail(), true, "Returned exploration lost coverage or the party arrow");
        checkWalked(view, 4);
    }

    private static void emptyStatuses() {
        LiveMapView empty = emptyView(600, 320);
        FrameLayout parent = (FrameLayout) empty.getParent();
        /*
         * Before any local observation the header reads "AREA MAP" beside
         * "Position unavailable" whatever the mode, and that is deliberate.
         * LiveMapView.drawMap says why: the transient modes churn between
         * Updating, Loading and Position unavailable several times a second
         * while the game settles, and naming each one on screen made the header
         * flash. The mode is carried in the accessible description instead,
         * which checkStatus verifies for every mode below. Combat is the one
         * exception, because it draws a battle screen in the map's allocation.
         * So the rule is not that every mode looks different -- it is that
         * combat does, and the rest hold one steady screen.
         */
        Integer battle = null, steady = null;
        for (MapMode mode : statusModes()) {
            applyStatus(empty, statusPacket(mode), mode); checkStatus(empty, mode, false);
            check(empty.displayedArea() == null, mode + " fabricated a map before any local observation");
            Bitmap image = render(empty); checkMonochrome(image);
            int appearance = Arrays.hashCode(pixels(image));
            if (mode == MapMode.COMBAT) battle = appearance;
            else if (steady == null) steady = appearance;
            else check(appearance == steady.intValue(),
                    mode + " drew a settling screen of its own for the header to flash with");
            check(parent.getChildCount() == 1 && parent.getChildAt(0) == empty,
                    mode + " added a new panel instead of using the existing map allocation");
        }
        check(battle != null && steady != null && battle.intValue() != steady.intValue(),
                "Combat did not draw a screen distinct from the steady settling one");
        applyStatus(empty, null, MapMode.UNAVAILABLE); checkStatus(empty, MapMode.UNAVAILABLE, false);
        check(Arrays.hashCode(pixels(render(empty))) == steady.intValue(),
                "An unnamed unavailable screen differed from the steady settling screen");
        SyntheticAreas areas = new SyntheticAreas(); MapObservation local = areas.observation(0, true, 1);
        LiveMapView narrow = view(null, 240, 280); showObservation(narrow, local);
        narrow.setExplorationStyle(true, true); narrow.showExploration(walkedTrail(), "Remembered narrow route");
        for (MapMode mode : statusModes()) {
            applyStatus(narrow, statusPacket(mode), mode); checkStatus(narrow, mode, true);
            if (mode == MapMode.COMBAT)
                assertReferenceMapReplaced(narrow, local.state, walkedTrail(),
                        "Combat kept the local area map in a narrow pane");
            else assertMap(narrow, local.state, walkedTrail(), false,
                    mode + " text overlapped the narrow map");
            check(narrow.getWidth() == px(240) && narrow.getHeight() == px(280), mode + " changed allocated dimensions");
        }
        resizePixels(narrow, 1, 1); narrow.showSample(statusPacket(MapMode.LOADING));
        checkMonochrome(render(narrow)); // Even an impossible layout remains bounded and opaque.
    }

    private static void modeTouchAndParty() {
        byte[] health = packet(true); LiveMapView view = view(health, 960, 480);
        SyntheticAreas areas = new SyntheticAreas(); MapObservation local = areas.observation(0, true, 1);
        showObservation(view, local); final int[] taps = {0, 0};
        view.setListener(new LiveMapView.Listener() {
            @Override public void onAreaChanged(AreaIdentity area) { }
            @Override public void onTileTapped(AreaIdentity area, int x, int y) { taps[0]++; }
            @Override public void onPartyMemberTapped(PartyState.Member member) { taps[1]++; }
        });
        PartyPaneLayout pane = pane(view, MEMBERS); MapViewport map = new MapViewport(pane.mapWidth, pane.mapHeight, density);
        float mapX = map.left + 4.5f * map.cell, mapY = map.top + 3.5f * map.cell;
        float rowX = pane.partyLeft + pane.partyWidth / 2, rowY = pane.rowTop(2) + pane.rowHeight / 2;
        Bitmap original = render(view);
        event(view, MotionEvent.ACTION_DOWN, mapX, mapY); view.showSample(statusPacket(MapMode.COMBAT));
        event(view, MotionEvent.ACTION_UP, mapX, mapY); check(taps[0] == 0, "Map press crossed into combat");
        showObservation(view, local); event(view, MotionEvent.ACTION_DOWN, rowX, rowY);
        view.showSample(statusPacket(MapMode.CAMP)); event(view, MotionEvent.ACTION_UP, rowX, rowY);
        check(taps[1] == 0, "Party press begun in exploration completed after changing mode");
        tap(view, mapX, mapY); check(taps[0] == 0, "Reference-only map tap emitted a note-creation callback");
        tap(view, rowX, rowY); check(taps[1] == 1, "A fresh valid health-row tap was disabled merely by camp mode");
        for (MapMode mode : statusModes()) {
            view.showSample(statusPacket(mode)); Bitmap image = render(view);
            equalParty(original, image, pane, mode + " changed the separately verified party/sidebar pixels");
            checkBars(image, pane(view, MEMBERS), health); checkDescription(view, health);
            PartyPaneLayout current = pane(view, MEMBERS);
            check(current.mapWidth == pane.mapWidth && current.mapHeight == pane.mapHeight,
                    mode + " stole map or guest allocation for an extra status panel");
        }
    }

    private static void displayWithoutRecording() {
        SyntheticAreas areas = new SyntheticAreas(); LiveMapView view = view(null, 960, 480);
        final List<PoolRadState> samples = new ArrayList<>(); final List<AreaIdentity> areaChanges = new ArrayList<>();
        final int[] tapped = {-1};
        view.setListener(new LiveMapView.Listener() {
            @Override public void onAreaChanged(AreaIdentity area) { areaChanges.add(area); }
            @Override public void onTileTapped(AreaIdentity area, int x, int y) {
                check(area != null && area.id().equals("por-mac-v11-geo-0"), "Display-only flag tap lost its authenticated area");
                tapped[0] = y * 16 + x;
            }
            @Override public void onExplorationSample(PoolRadState sample) { samples.add(sample); }
        });
        MapObservation initial = areas.observation(0, true, 1); showObservation(view, initial);
        Map<Integer, NoteIcon> symbols = Collections.singletonMap(68, NoteIcon.TEMPLE);
        view.showNotebook("Synthetic flagged notebook", symbols);
        view.setExplorationStyle(true, true); view.showExploration(walkedTrail(), "Remembered route");
        Bitmap before = render(view);
        // Display validity is independent even when the recording epoch is absent.
        MapObservation first = areas.observation(0, false, 0, 13, 9);
        showObservation(view, first); showObservation(view, first);
        MapObservation second = areas.observation(0, false, 0, 14, 9); showObservation(view, second);
        check(view.snapshot() == second.state && view.currentArea().equals(second.state.area),
                "A valid safe-zero local position was hidden");
        String description = view.getContentDescription().toString();
        check(description.contains("Party at 14, 9 E") && description.contains("1 flags."),
                "Moving safe-zero position or its existing flags disappeared");
        check(areaChanges.equals(Collections.singletonList(initial.state.area)),
                "Changing recording eligibility unnecessarily reset the current area's flags");
        check(samples.size() == 4 && samples.get(1) == first.state && samples.get(2) == first.state
                && samples.get(3) == second.state, "Display-only updates/repeats did not reach the recorder boundary");
        for (int i = 1; i < samples.size(); i++)
            check(!samples.get(i).explorationSafe && samples.get(i).explorationProcessing
                    && samples.get(i).continuityToken == 0, "Display availability invented recording authorization");
        checkWalked(view, 4);
        Bitmap after = render(view);
        check(mapChangedPixels(before, after, view) > 10, "Safe-zero movement did not move the visible party arrow");
        check(mapChangedPixels(after, expectedMap(view, second.state, walkedTrail(), true, symbols), view) == 0,
                "Safe-zero display lost a flag, moved the arrow incorrectly or invented footprint pixels");
        MapViewport map = mapBounds(view); tap(view, map.left + 4.5f * map.cell, map.top + 4.5f * map.cell);
        check(tapped[0] == 68, "The retained flag could not be opened during valid safe-zero display");
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

    /** Uses the actual production parser with invented, independently hashed geometry. */
    private static final class SyntheticAreas {
        private final byte[][] packets = {mapPacket(), mapPacket()};
        private final Object catalog;
        private final java.lang.reflect.Method parse;
        private final java.lang.reflect.Method parseObservation;

        SyntheticAreas() {
            packets[1][176] ^= 0x40; // A second exact, distinct immutable prefix, not a private game map.
            String[] exact = new String[2], prefix = new String[2];
            for (int i = 0; i < 2; i++) {
                int id = i == 0 ? 0 : 20;
                exact[i] = id + " " + digest(packets[i], 176, 1200);
                prefix[i] = id + " " + digest(packets[i], 176, 176 + 768);
            }
            try {
                Class<?> type = Class.forName("name.osher.gil.minivmac.mapper.AreaIdentity$Catalog");
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(String[].class, String[].class);
                constructor.setAccessible(true);
                catalog = constructor.newInstance(new Object[]{exact, prefix});
                parse = PoolRadState.class.getDeclaredMethod("parse", byte[].class, type);
                parse.setAccessible(true);
                parseObservation = MapObservation.class.getDeclaredMethod("parse", byte[].class, type);
                parseObservation.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot construct the synthetic production-parser catalog", failure);
            }
        }

        PoolRadState state(int id, boolean safe, long epoch) {
            check(id == 0 || id == 20, "Synthetic area must be A or B");
            byte[] sample = packets[id == 0 ? 0 : 1].clone();
            sample[3] = '3'; sample[25] = 1; sample[26] = (byte) (safe ? 1 : 0);
            sample[27] = (byte) (safe ? 4 : 2);
            for (int i = 0; i < 4; i++) sample[28 + i] = (byte) (epoch >>> (24 - i * 8));
            sample[32] = 1; sample[33] = 1; sample[34] = 0; sample[35] = (byte) id;
            try {
                PoolRadState value = (PoolRadState) parse.invoke(null, sample, catalog);
                check(value != null && value.area != null && value.area.id().equals("por-mac-v11-geo-" + id),
                        "Synthetic PRM3 fixture failed actual canonical identity parsing");
                check(value.hasExplorationMetadata && value.explorationSafe == safe && value.continuityToken == epoch,
                        "Synthetic PRM3 fixture failed actual continuity/safety parsing");
                return value;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot parse the synthetic PRM3 fixture", failure);
            }
        }

        MapObservation observation(int id, boolean safe, long epoch) {
            return observation(id, safe, epoch, 12, 9);
        }

        MapObservation observation(int id, boolean safe, long epoch, int x, int y) {
            check(id == 0 || id == 20, "Synthetic PRM4 area must be A or B");
            byte[] sample = packets[id == 0 ? 0 : 1].clone();
            sample[3] = '4'; sample[24] = 1; sample[25] = 1;
            sample[26] = (byte) (safe ? 1 : 0); sample[27] = 4;
            for (int i = 0; i < 4; i++) sample[28 + i] = (byte) (epoch >>> (24 - i * 8));
            sample[32] = 1; sample[33] = 1; sample[34] = 0; sample[35] = (byte) id;
            sample[130] = (byte) x; sample[131] = (byte) y;
            try {
                MapObservation value = (MapObservation) parseObservation.invoke(null, sample, catalog);
                check(value.state != null && value.state.area.id().equals("por-mac-v11-geo-" + id)
                        && value.mode == MapMode.EXPLORATION,
                        "Synthetic PRM4 failed actual observation/identity parsing");
                check(value.state.explorationSafe == safe && value.state.continuityToken == epoch,
                        "Synthetic PRM4 safety/continuity mismatch");
                return value;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot parse the synthetic PRM4 observation", failure);
            }
        }
    }

    private static byte[] conditionPacket() {
        byte[] sample=Arrays.copyOf(packet(false),PartyState.CONDITION_PACKET_SIZE);
        sample[3]='3';return sample;
    }

    private static MapMode[] statusModes() {
        return new MapMode[]{MapMode.COMBAT, MapMode.CAMP, MapMode.WILDERNESS, MapMode.LOADING, MapMode.UPDATING};
    }

    private static byte[] statusPacket(MapMode mode) {
        byte[] sample = new byte[1200];
        sample[0] = 'P'; sample[1] = 'R'; sample[2] = 'M'; sample[3] = '4';
        sample[25] = 1; sample[31] = 1; sample[32] = 1;
        sample[34] = (byte) 255; sample[35] = (byte) 255;
        sample[130] = (byte) 255; sample[131] = (byte) 255; sample[132] = (byte) 255;
        switch (mode) {
            case COMBAT: sample[24] = 2; sample[27] = 5; break;
            case CAMP: sample[24] = 3; sample[27] = 2; break;
            case WILDERNESS: sample[24] = 4; sample[27] = 3; sample[32] = 2; break;
            case LOADING: sample[24] = 5; sample[27] = 0; sample[32] = 4; break;
            case UPDATING: sample[24] = 6; sample[27] = 4; break;
            default: throw new AssertionError("Not a status-only mode: " + mode);
        }
        MapObservation parsed = MapObservation.parse(sample);
        check(parsed.mode == mode && parsed.state == null, "Malformed synthetic status fixture: " + mode);
        return sample;
    }

    /**
     * Apply a status packet and, when the mode is one the display holds, keep
     * applying it until the hold gives way.
     *
     * Updating and Unavailable are the two modes ReadingHold keeps off the
     * screen for {@link ReadingHold#HOLD_MS}, because a single unreadable frame
     * is a blink rather than a fact. Named modes -- Wilderness, Loading, Camp,
     * Combat -- apply at once and send exactly one packet here, so the
     * observation counting around these calls is unchanged for them.
     */
    private static void applyStatus(LiveMapView view, byte[] packet, MapMode mode) {
        view.showSample(packet);
        if (mode != MapMode.UPDATING && mode != MapMode.UNAVAILABLE) return;
        long start = android.os.SystemClock.elapsedRealtime();
        while (!view.getContentDescription().toString().startsWith(mode.label() + ". ")
                && android.os.SystemClock.elapsedRealtime() - start < ReadingHold.HOLD_MS + 1000) {
            try { Thread.sleep(50); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
            view.showSample(packet);
        }
    }

    private static void checkStatus(LiveMapView view, MapMode mode, boolean reference) {
        String description = view.getContentDescription().toString();
        check(description.startsWith(mode.label() + ". ") && description.contains(mode.explanation()),
                "Missing or stale mode explanation: " + description);
        check(description.contains(reference ? "Last local map:" : "No local map yet"),
                "Mode presentation did not distinguish a reference map from an empty screen");
        check(view.snapshot() == null && view.currentArea() == null && !description.contains("Party at "),
                mode + " exposed a live local coordinate or arrow claim");
    }

    private static String digest(byte[] input, int from, int to) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(input, from, to));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append("0123456789abcdef".charAt((value & 255) >>> 4));
                hex.append("0123456789abcdef".charAt(value & 15));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }

    private static final class ExplorationListener implements LiveMapView.Listener {
        final List<AreaIdentity> areas = new ArrayList<>();
        final List<PoolRadState> samples = new ArrayList<>();
        @Override public void onAreaChanged(AreaIdentity area) { }
        @Override public void onTileTapped(AreaIdentity area, int x, int y) { }
        @Override public void onExplorationAreaChanged(AreaIdentity area) { areas.add(area); }
        @Override public void onExplorationSample(PoolRadState sample) { samples.add(sample); }
    }

    /**
     * Push a state that the map cannot read until the five-second hold gives
     * way. Since 0.31.0 one unreadable map sample keeps the last good area on
     * screen, so a single call no longer changes what is displayed.
     */
    private static void showStatePastHold(LiveMapView view, PoolRadState state) {
        long start = android.os.SystemClock.elapsedRealtime();
        while (android.os.SystemClock.elapsedRealtime() - start < ReadingHold.HOLD_MS + 500) {
            showState(view, state);
            try { Thread.sleep(50); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
        }
    }

    private static void showState(LiveMapView view, PoolRadState state) {
        // Only bridge the catalog injection boundary: the production View handles
        // all display availability, invalidation, callbacks, flags and rendering.
        try {
            java.lang.reflect.Method method = LiveMapView.class.getDeclaredMethod("showState", PoolRadState.class);
            method.setAccessible(true); method.invoke(view, state);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot exercise the actual View's parsed-state path", failure);
        }
    }

    private static void showObservation(LiveMapView view, MapObservation observation) {
        try {
            java.lang.reflect.Method method = LiveMapView.class.getDeclaredMethod("showState", PoolRadState.class, MapMode.class);
            method.setAccessible(true); method.invoke(view, observation.state, observation.mode);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot exercise the actual View's mode-aware state path", failure);
        }
    }

    private static ExplorationTrail walkedTrail() {
        return ExplorationTrail.empty().record(34, -1).record(35, 34).record(51, 35).record(52, 51);
    }

    private static void checkWalked(LiveMapView view, int count) {
        check(view.getContentDescription().toString().contains(count + " of 256 squares walked."),
                "Accessible coverage count is stale: " + view.getContentDescription());
    }

    private static MapViewport mapBounds(LiveMapView view) {
        PartyPaneLayout pane = pane(view, 0);
        return new MapViewport(pane.mapWidth, pane.mapHeight, density);
    }

    private static Bitmap expectedMap(LiveMapView view, PoolRadState state, ExplorationTrail trail, boolean showArrow) {
        return expectedMap(view, state, trail, showArrow, Collections.emptyMap());
    }

    private static Bitmap expectedMap(LiveMapView view, PoolRadState state, ExplorationTrail trail,
                                      boolean showArrow, Map<Integer, NoteIcon> symbols) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        BITMAPS.add(bitmap);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        MapViewport map = mapBounds(view); MapArtwork artwork = new MapArtwork();
        artwork.drawExploration(canvas, state.map, trail, true, true, map.left, map.top, map.cell, density);
        artwork.drawMarkers(canvas, symbols, state, showArrow,
                map.left, map.top, map.cell, density);
        return bitmap;
    }

    private static void assertMap(LiveMapView view, PoolRadState state, ExplorationTrail trail,
                                  boolean showArrow, String message) {
        Bitmap actual = render(view), expected = expectedMap(view, state, trail, showArrow);
        try {
            int changed = mapChangedPixels(actual, expected, view);
            // Say how far off, and what the view thinks it is showing. A bare
            // "did not match" sends the next reader back to first principles.
            check(changed == 0, message + " -- " + changed + " map pixels differ; view shows "
                    + view.displayedArea() + ", current " + view.currentArea()
                    + ", description \"" + view.getContentDescription() + "\"");
        }
        finally { actual.recycle(); expected.recycle(); BITMAPS.remove(actual); BITMAPS.remove(expected); }
    }

    /**
     * Combat is the one status mode that does not keep the reference map on
     * screen. Since 0.25.0 the tactical overview is drawn in the map's own
     * allocation -- LiveMapView.drawCombat returns before any exploration ink
     * is laid down -- so demanding the local map here asserts a rule the app
     * deliberately dropped. Everything combat must still honour is checked by
     * the callers: the area identity is retained, the walked squares survive,
     * and no local coordinate or party arrow is invented. This adds the part
     * only the pixels can state -- that the stale local map is genuinely gone,
     * that what replaced it is a sparser drawing rather than more ink, and that
     * it is still bounded and monochrome.
     */
    private static void assertReferenceMapReplaced(LiveMapView view, PoolRadState state,
                                                   ExplorationTrail trail, String message) {
        Bitmap actual = render(view), expected = expectedMap(view, state, trail, false);
        try {
            check(mapChangedPixels(actual, expected, view) > 0,
                    message + " -- the local map is still on screen");
            // Not a density rule: the centred explanation is denser than the
            // map's thin walls. What must be gone is the map's own ink, so
            // count how much of it is still drawn where it used to be.
            int local = darkMapPixels(expected, view), kept = sharedDarkMapPixels(actual, expected, view);
            check(kept * 2 < local, message + " -- " + kept + " of the local map's "
                    + local + " ink pixels are still drawn in place");
            checkMonochrome(actual);
        }
        finally { actual.recycle(); expected.recycle(); BITMAPS.remove(actual); BITMAPS.remove(expected); }
    }

    /** Map ink drawn in both bitmaps at the same place. */
    private static int sharedDarkMapPixels(Bitmap first, Bitmap second, LiveMapView view) {
        MapViewport map = mapBounds(view); int shared = 0;
        for (int y = (int) Math.ceil(map.top + 1); y < map.top + 16 * map.cell - 1; y++)
            for (int x = (int) Math.ceil(map.left + 1); x < map.left + 16 * map.cell - 1; x++)
                if (Color.red(first.getPixel(x, y)) < 128 && Color.red(second.getPixel(x, y)) < 128) shared++;
        return shared;
    }

    /** Ink inside the same map bounds mapChangedPixels compares. */
    private static int darkMapPixels(Bitmap bitmap, LiveMapView view) {
        MapViewport map = mapBounds(view); int dark = 0;
        for (int y = (int) Math.ceil(map.top + 1); y < map.top + 16 * map.cell - 1; y++)
            for (int x = (int) Math.ceil(map.left + 1); x < map.left + 16 * map.cell - 1; x++)
                if (Color.red(bitmap.getPixel(x, y)) < 128) dark++;
        return dark;
    }

    private static int mapChangedPixels(Bitmap first, Bitmap second, LiveMapView view) {
        MapViewport map = mapBounds(view); int changed = 0;
        // Exclude only the outer antialiased edge; header/footer text is deliberately
        // outside this comparison. All 256 tile interiors and their shared walls count.
        for (int y = (int) Math.ceil(map.top + 1); y < map.top + 16 * map.cell - 1; y++)
            for (int x = (int) Math.ceil(map.left + 1); x < map.left + 16 * map.cell - 1; x++)
                if (first.getPixel(x, y) != second.getPixel(x, y)) changed++;
        return changed;
    }

    private static LiveMapView view(byte[] party, int widthDp, int heightDp) {
        LiveMapView view = new LiveMapView(context, null);
        // Detached parent is needed by the View's real touch-cancellation contract.
        FrameLayout parent = new FrameLayout(context); parent.addView(view);
        view.showSample(mapPacket()); view.showNotebook("Synthetic notebook", Collections.emptyMap());
        view.showPartySample(party); resize(view, widthDp, heightDp);
        return view;
    }

    private static LiveMapView emptyView(int widthDp, int heightDp) {
        LiveMapView view = new LiveMapView(context, null);
        FrameLayout parent = new FrameLayout(context); parent.addView(view);
        view.showNotebook("Synthetic notebook", Collections.emptyMap());
        resize(view, widthDp, heightDp); return view;
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
                    +(member.armorClass==null ? "unavailable" : member.armorClass)+"; "+member.classLabel()
                    +"; "+member.conditionSummary()+".";
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

    private static void equalParty(Bitmap before, Bitmap after, PartyPaneLayout pane, String message) {
        for (int y = 0; y < before.getHeight(); y++)
            for (int x = (int) Math.ceil(pane.partyLeft); x < before.getWidth(); x++)
                if (before.getPixel(x, y) != after.getPixel(x, y))
                    throw new AssertionError(message + " at " + x + "," + y);
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
