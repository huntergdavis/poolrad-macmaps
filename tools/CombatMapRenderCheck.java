import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.mapper.CombatSnapshot;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.mapper.ReadingHold;

/**
 * Actual Android View checks for the tactical overview, detached from the
 * guest and the activity. Synthetic packets only; no captured battle appears
 * here. Does NOT verify fragment polling, hardware-GPU or e-ink rendering, or
 * any on-device interaction; the emulator owner checks the running app
 * separately.
 *
 * Build and run exactly as CompanionPaneCheck documents, substituting this
 * class and adding mapper/CombatSnapshot.java to the source list.
 */
public final class CombatMapRenderCheck {
    private static int passed;

    private static final class CountingMap extends LiveMapView {
        int redraws, descriptions;
        CountingMap(Context context) { super(context, null); }
        @Override public void invalidate() { redraws++; super.invalidate(); }
        @Override public void setContentDescription(CharSequence text) {
            descriptions++; super.setContentDescription(text);
        }
        void resetCounts() { redraws = descriptions = 0; }
    }

    public static void main(String[] args) {
        try { checks(args.length == 0 ? "com.hunterdavis.poolradmacmaps.ii" : args[0]); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    /** PRM5 status-only packet in the requested display mode. */
    private static byte[] mapPacket(int mode, int engine) {
        byte[] p = new byte[1204];
        p[0]='P'; p[1]='R'; p[2]='M'; p[3]='5';
        p[24]=(byte)mode; p[25]=1; p[26]=0; p[27]=(byte)engine; p[31]=42;
        p[32]=1; p[34]=(byte)255; p[35]=(byte)255;
        p[130]=p[131]=p[132]=(byte)255;
        p[1200]=(byte)255;
        return p;
    }
    /** A cross must be visibly different from both a circle and a square. */
    private static byte[] combatPacketOf(int[][] rows) { return combatPacket(rows); }

    private static byte[] combatPacket(int[][] rows) {
        byte[] p = new byte[CombatSnapshot.PACKET_SIZE];   // PRC3: entries, the actor, then the foes
        p[0]='P'; p[1]='R'; p[2]='C'; p[3]='3'; p[4]=1; p[5]=(byte) rows.length;
        for (int i = 0; i < rows.length; i++) {
            p[8+i*4] = (byte) rows[i][0]; p[8+i*4+1] = (byte) rows[i][1]; p[8+i*4+2] = (byte) rows[i][2];
            // The fourth column is the game's own condition. A fallen marker
            // has to carry one of the four ways of being down or the reader
            // rejects it, which is the point of the check.
            p[8+i*4+3] = (byte) (rows[i].length > 3 ? rows[i][3] : 0);
        }
        return p;
    }
    /* The shape of a real battle: six of the party east, four others west. */
    private static final int[][] BATTLE = {
        {1, 27, 12}, {1, 26, 12}, {1, 28, 12}, {1, 26, 11}, {1, 25, 9}, {1, 27, 11},
        {2, 20, 12}, {2, 21, 13}, {2, 19, 11}, {2, 22, 14},
    };

    /** Comfortably inside the hold, with room for a slow draw or two. */
    private static final long HALFWAY = ReadingHold.HOLD_MS / 2;

    /** Feed refused battlefields for a while; returns how many frames went in. */
    private static int refuseUntil(LiveMapView view, long millis, byte[]... refusals) {
        long start = SystemClock.elapsedRealtime();
        int polls = 0;
        while (SystemClock.elapsedRealtime() - start < millis) {
            for (byte[] refusal : refusals) view.showCombatSample(refusal);
            polls++;
            idle(50);
        }
        return polls;
    }

    private static int refusePartyUntil(LiveMapView view, long millis) {
        long start = SystemClock.elapsedRealtime();
        int polls = 0;
        while (SystemClock.elapsedRealtime() - start < millis) {
            view.showPartySample(null);
            // The refusal Hunter's own tablet reported, verbatim.
            view.showPartySample(new byte[]{'P','R','P','X', 7, 2, (byte) 0x80, 0, 1, 0x36});
            polls++;
            idle(50);
        }
        return polls;
    }

    private static void idle(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] partyPacket(int count) {
        byte[] b = new byte[8 + 8 * 20];
        b[0]='P';b[1]='R';b[2]='P';b[3]='1';b[4]=(byte) count;
        String[] names = {"Arax","Lara","Tanarakis","Hogarth","Shara","Zarram","Ohlo","Skull"};
        for (int i = 0; i < count; i++) {
            byte[] n = names[i].getBytes();
            System.arraycopy(n, 0, b, 8 + i * 20, Math.min(n.length, 15));
            b[8 + i * 20 + 16] = (byte) (7 + i);
            b[8 + i * 20 + 17] = (byte) 20;
        }
        return b;
    }

    /**
     * A PRP7 party whose members move at the given rates. The equipment block
     * is marked unavailable, which is the state the probe reports when the
     * item names do not resolve; movement and carried weight are plain record
     * fields and still read, which is exactly the case the "W" mark needs.
     */
    private static byte[] loadPacket(int... movement) {
        /*
         * Each name below is where the NEXT section begins, which is the same
         * convention the other harnesses use and the one that has already cost
         * this project a vanished party pane once. So: conditions live at
         * `rows`, spells at `conditions`, equipment at `spells` (stride 68),
         * training at `equip` (stride 23) and the quick bytes at `training`.
         */
        final int CONDITION_STRIDE = 2, SPELL_STRIDE = 8, EQUIP_STRIDE = 1 + 64 + 3, TRAIN_STRIDE = 1 + 4 + 18;
        int rows = 8 + 8 * 20;
        int conditions = rows + 8 * CONDITION_STRIDE;
        int spells = conditions + 8 * SPELL_STRIDE;
        int equip = spells + 8 * EQUIP_STRIDE;
        int training = equip + 8 * TRAIN_STRIDE;
        byte[] b = new byte[training + 8];
        b[0]='P';b[1]='R';b[2]='P';b[3]='7';b[4]=(byte) movement.length;
        String[] names = {"Arax","Lara","Tanarakis","Hogarth","Shara","Zarram","Ohlo","Skull"};
        for (int i = 0; i < movement.length; i++) {
            byte[] n = names[i].getBytes();
            System.arraycopy(n, 0, b, 8 + i * 20, Math.min(n.length, 15));
            b[8 + i * 20 + 16] = (byte) (7 + i);
            b[8 + i * 20 + 17] = (byte) 20;
            b[8 + i * 20 + 18] = (byte) 0x80;   // armour class unavailable
            b[8 + i * 20 + 19] = (byte) 0xff;   // class unavailable
            b[rows + i * CONDITION_STRIDE] = (byte) 0xff;
            b[rows + i * CONDITION_STRIDE + 1] = (byte) 0xff;
            b[conditions + i * SPELL_STRIDE] = (byte) 0xff;
            b[spells + i * EQUIP_STRIDE] = (byte) 0xff;          // item names unavailable
            b[spells + i * EQUIP_STRIDE + 1 + 64] = (byte) movement[i];
            b[equip + i * TRAIN_STRIDE] = (byte) 0xff;
            b[training + i] = 0;                                  // quick off
        }
        return b;
    }

    private static int mentions(LiveMapView view, String phrase) {
        String text = String.valueOf(view.getContentDescription());
        int found = 0, at = text.indexOf(phrase);
        while (at >= 0) { found++; at = text.indexOf(phrase, at + phrase.length()); }
        return found;
    }

    private static void tap(View view, float x, float y) {
        long when = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(when, when, MotionEvent.ACTION_DOWN, x, y, 0);
        view.dispatchTouchEvent(down); down.recycle();
        MotionEvent up = MotionEvent.obtain(when, when + 10, MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(up); up.recycle();
    }

    /**
     * Where drawCombat puts the marker for a battle square. This repeats the
     * production geometry, so the check that uses it first confirms there is
     * actually ink where this says there is -- otherwise the two could drift
     * apart and the tap would still appear to work.
     */
    private static float[] spotCentre(LiveMapView view, CombatSnapshotGeometry battle, int x, int y) {
        float density = view.getResources().getDisplayMetrics().density;
        PartyPaneLayout pane = new PartyPaneLayout(view.getWidth(), view.getHeight(), density, 6);
        float margin = 26 * density, caption = 22 * density;
        float usableWidth = pane.mapWidth - 2 * margin;
        float usableHeight = pane.mapHeight - margin - caption - 10 * density;
        float cell = Math.min(Math.min(usableWidth / battle.width, usableHeight / battle.height), 34 * density);
        float gridWidth = cell * battle.width, gridHeight = cell * battle.height;
        float left = (pane.mapWidth - gridWidth) / 2f, top = margin + (usableHeight - gridHeight) / 2f;
        return new float[]{left + (x - battle.left + .5f) * cell, top + (y - battle.top + .5f) * cell, cell};
    }

    /** The bounds the overview derives from a set of rows. */
    private static final class CombatSnapshotGeometry {
        final int left, top, width, height;
        CombatSnapshotGeometry(int[][] rows) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            for (int[] row : rows) {
                minX = Math.min(minX, row[1]); maxX = Math.max(maxX, row[1]);
                minY = Math.min(minY, row[2]); maxY = Math.max(maxY, row[2]);
            }
            left = minX; top = minY; width = maxX - minX + 1; height = maxY - minY + 1;
        }
    }

    private static LiveMapView map(Context context, int width, int height) {
        LiveMapView view = new LiveMapView(context, null);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        return view;
    }
    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        return bitmap;
    }
    private static int inkPixels(Bitmap bitmap, int left, int top, int right, int bottom) {
        int dark = 0;
        for (int y = Math.max(0, top); y < Math.min(bitmap.getHeight(), bottom); y++)
            for (int x = Math.max(0, left); x < Math.min(bitmap.getWidth(), right); x++)
                if (Color.red(bitmap.getPixel(x, y)) < 128) dark++;
        return dark;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void run(String name, Runnable body) {
        body.run(); passed++; System.out.println("PASS " + name);
    }

    private static void checks(String packageName) throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null)
            Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context app = system.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
        Context context = new ContextThemeWrapper(app, android.R.style.Theme_Material_Light_NoActionBar);

        run("Identical combat polls request no redraw or accessibility rebuild", () -> {
            CountingMap view = new CountingMap(context);
            view.layout(0, 0, 1440, 684);
            byte[] party = partyPacket(6), battle = combatPacket(BATTLE);
            view.showPartySample(party);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(battle);
            Bitmap before = draw(view);
            view.resetCounts();
            for (int i = 0; i < 100; i++) {
                view.showSample(mapPacket(2, 5));
                view.showPartySample(party.clone());
                view.showCombatSample(battle.clone());
            }
            check(view.redraws == 0 && view.descriptions == 0,
                    "Repeated readings refreshed the map: " + view.redraws + "/" + view.descriptions);
            check(before.sameAs(draw(view)), "Identical readings changed pixels");

            // Movement within unchanged bounds must still repaint.
            battle[9]++;
            view.showCombatSample(battle);
            check(view.redraws == 1 && !before.sameAs(draw(view)), "Movement did not repaint immediately");
            before = draw(view); view.resetCounts();
            byte[] actor = "Arax".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(actor, 0, battle, CombatSnapshot.ENTRIES_SIZE, actor.length);
            view.showCombatSample(battle);
            check(view.redraws == 1 && !before.sameAs(draw(view)), "Acting marker did not repaint immediately");
            check(String.valueOf(view.getContentDescription()).contains("Arax (acting)"), "Acting text is stale");

            before = draw(view); view.resetCounts();
            party[8 + 16]--; // HP changes while battlefield remains identical.
            view.showPartySample(party);
            view.showCombatSample(battle.clone());
            check(view.redraws == 1 && !before.sameAs(draw(view)), "HP change was swallowed by combat equality");
            check(String.valueOf(view.getContentDescription()).contains("6 of 20 HP"), "HP text is stale");

            before = draw(view); view.resetCounts();
            battle[8] = 4; battle[11] = 5;
            view.showCombatSample(battle);
            check(view.redraws == 1 && !before.sameAs(draw(view)), "Fallen marker did not repaint immediately");
            before = draw(view); view.resetCounts();
            battle[CombatSnapshot.FOES_OUT] = 1;
            byte[] foe = "GOBLIN".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(foe, 0, battle, CombatSnapshot.FOES_OUT + 1, foe.length);
            battle[CombatSnapshot.FOES_OUT + 1 + CombatSnapshot.FOE_NAME] = 4;
            view.showCombatSample(battle);
            check(view.redraws == 1 && !before.sameAs(draw(view)), "Foe header did not repaint immediately");
        });

        run("An identical good reading renews the unreadable-frame hold", () -> {
            CountingMap view = new CountingMap(context);
            view.layout(0, 0, 900, 520);
            byte[] battle = combatPacket(BATTLE);
            view.showSample(mapPacket(2, 5)); view.showCombatSample(battle);
            idle(ReadingHold.HOLD_MS / 2 + 100);
            view.resetCounts(); view.showCombatSample(battle.clone());
            check(view.redraws == 0, "Good repeat redrew the overview");
            refuseUntil(view, ReadingHold.HOLD_MS / 2 + 100, (byte[]) null);
            check(view.redraws == 0 && String.valueOf(view.getContentDescription()).contains("Battle overview"),
                    "Skipping the redraw failed to renew the hold");
            refuseUntil(view, ReadingHold.HOLD_MS / 2 + 100, (byte[]) null);
            check(view.redraws == 1 && !String.valueOf(view.getContentDescription()).contains("Battle overview"),
                    "Expired reading did not clear exactly once");
            view.showCombatSample(battle);
            check(view.redraws == 2, "Returning reading did not restore immediately");
            view.showSample(mapPacket(3, 2)); view.resetCounts();
            view.showCombatSample(battle);
            check(view.redraws == 0, "Out-of-combat reading redrew the view");
        });

        run("Game clock draws in the header and clears on loading or pane reset", () -> {
            for (int width : new int[]{1440,480}) {
                LiveMapView view = map(context,width,684);
                byte[] p = java.util.Arrays.copyOf(mapPacket(3,2),1212);
                p[3]='6'; p[1204]=1;p[1208]=2;p[1209]=12;p[1210]=5;
                view.showSample(p);
                check(String.valueOf(view.getContentDescription()).contains("Day 2 · 12:05 pm"),
                        "Clock absent from accessible header");
                Bitmap with=draw(view);
                p[1204]=0;view.showSample(p);Bitmap without=draw(view);
                int changes=0;
                for(int y=0;y<45;y++)for(int x=0;x<width;x++)
                    if(with.getPixel(x,y)!=without.getPixel(x,y)) changes++;
                check(changes>20,"Clock made no visible header change at width "+width);
                p[1204]=1;view.showSample(p);
                p[24]=2;p[27]=5;view.showSample(p);
                view.showCombatSample(combatPacketOf(new int[][]{{0,1,4,4},{1,0,6,6}}));
                draw(view);
                check(String.valueOf(view.getContentDescription()).contains("Day 2 · 12:05 pm"),
                        "Combat dropped valid clock");
                view.showSample(mapPacket(5,4));
                check(!String.valueOf(view.getContentDescription()).contains("Game time:"),
                        "Loading retained clock");
                view.showSample(p);view.clearReadings();
                check(!String.valueOf(view.getContentDescription()).contains("Game time:"),
                        "Pane reset retained clock");
            }
        });

        run("Selection marks both row layouts and combat actor takes precedence", () -> {
            for (boolean compact : new boolean[]{false, true}) {
                LiveMapView view = map(context, 1440, 600);
                view.setOneLineParty(compact);
                view.showSample(mapPacket(3, 2)); // camp is outside combat
                byte[] party = loadPacket(9, 9, 9, 9, 9, 9); party[3] = '8';
                view.showPartySample(party);
                int unselected = inkPixels(draw(view), 1000, 0, 1440, 600);
                party[5] = 2; view.showPartySample(party);
                check(String.valueOf(view.getContentDescription()).contains("Lara (selected)"),
                        "Selected Lara was not described");
                check(inkPixels(draw(view), 1000, 0, 1440, 600) > unselected,
                        "Selection drew no marker, compact=" + compact);
                view.showSample(mapPacket(2, 5));
                byte[] battle = combatPacket(BATTLE);
                System.arraycopy(new byte[]{'A','r','a','x'}, 0, battle, CombatSnapshot.ENTRIES_SIZE, 4);
                view.showCombatSample(battle);
                check(String.valueOf(view.getContentDescription()).contains("Arax (acting)"),
                        "Combat actor was not described");
                check(!String.valueOf(view.getContentDescription()).contains("(selected)"),
                        "General selection competed with combat actor");
                int acting = inkPixels(draw(view), 1000, 0, 1440, 600);
                party[5] = 0; view.showPartySample(party);
                check(inkPixels(draw(view), 1000, 0, 1440, 600) == acting,
                        "Combat marker depended on general selection");
                party[5] = 2; view.showPartySample(party);
                view.showSample(mapPacket(5, 4)); // loading
                check(!String.valueOf(view.getContentDescription()).contains("(selected)"),
                        "Loading retained a selected marker");
            }
        });

        run("A battle draws an overview that an empty battle does not", () -> {
            LiveMapView empty = map(context, 900, 520);
            empty.showSample(mapPacket(2, 5));
            int without = inkPixels(draw(empty), 0, 40, 900, 500);
            LiveMapView full = map(context, 900, 520);
            full.showSample(mapPacket(2, 5));
            full.showCombatSample(combatPacket(BATTLE));
            int with = inkPixels(draw(full), 0, 40, 900, 500);
            check(with > without + 200, "The battle overview drew nothing new: " + without + " -> " + with);
            check(full.getContentDescription().toString().contains("Battle overview"),
                    "Accessible text does not announce the overview");
            check(full.getContentDescription().toString().contains("Reference only"),
                    "Accessible text does not say the overview is reference only");
        });

        run("Party squares are filled and the others are hollow", () -> {
            LiveMapView party = map(context, 900, 520);
            party.showSample(mapPacket(2, 5));
            party.showCombatSample(combatPacket(new int[][]{{1, 10, 10}, {1, 14, 10}}));
            int filled = inkPixels(draw(party), 0, 40, 900, 500);
            LiveMapView others = map(context, 900, 520);
            others.showSample(mapPacket(2, 5));
            others.showCombatSample(combatPacket(new int[][]{{2, 10, 10}, {2, 14, 10}}));
            int hollow = inkPixels(draw(others), 0, 40, 900, 500);
            check(filled > hollow, "A filled party marker must use more ink than a hollow one: "
                    + filled + " vs " + hollow);
        });

        run("Leaving combat clears the battlefield immediately", () -> {
            LiveMapView view = map(context, 900, 520);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            int during = inkPixels(draw(view), 0, 40, 900, 500);
            view.showSample(mapPacket(3, 2)); // camp
            int after = inkPixels(draw(view), 0, 40, 900, 500);
            check(after < during - 200, "A stale battlefield survived the mode change");
            check(!String.valueOf(view.getContentDescription()).contains("Battle overview"),
                    "Accessible text still announces a finished battle");
        });

        run("A battle sample outside combat is ignored", () -> {
            LiveMapView view = map(context, 900, 520);
            view.showSample(mapPacket(3, 2)); // camp
            int before = inkPixels(draw(view), 0, 40, 900, 500);
            view.showCombatSample(combatPacket(BATTLE));
            check(inkPixels(draw(view), 0, 40, 900, 500) == before,
                    "A battlefield drew while the game was not in combat");
        });

        run("A malformed battle packet leaves the pane honest, not half drawn", () -> {
            LiveMapView view = map(context, 900, 520);
            view.showSample(mapPacket(2, 5));
            byte[] broken = combatPacket(BATTLE); broken[8 + 4] = 9; // impossible kind
            view.showCombatSample(broken);
            check(String.valueOf(view.getContentDescription()).contains("Combat"),
                    "A rejected packet must leave the ordinary combat wording");
            check(!String.valueOf(view.getContentDescription()).contains("Battle overview"),
                    "A rejected packet was announced as an overview");
        });

        run("The overview stays inside the map pane at a narrow size", () -> {
            LiveMapView view = map(context, 380, 300);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            Bitmap bitmap = draw(view);
            check(inkPixels(bitmap, 0, 40, 380, 290) > 100, "Nothing drew at a narrow size");
            for (int y = 0; y < bitmap.getHeight(); y++)
                check(Color.red(bitmap.getPixel(bitmap.getWidth() - 1, y)) >= 128
                        || y >= bitmap.getHeight() - 2,
                        "The overview painted the pane's right edge at row " + y);
        });

        /*
         * The flicker Hunter reported: several times a second in a fight, where
         * the game rewrites the very records these readers walk. Each refused
         * frame used to blank its part of the pane for one poll. Nothing on
         * screen may move while a reading is merely unreadable.
         */
        run("A refused battlefield does not blink the overview away", () -> {
            LiveMapView view = map(context, 900, 520);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            int drawn = inkPixels(draw(view), 0, 40, 900, 500);
            check(drawn > 200, "The battlefield never drew in the first place");
            /*
             * The hold is five seconds of real time, so the loop is bounded by
             * the clock rather than by a poll count: drawing and counting ink
             * here is slow enough to outlast the hold on its own, which is how
             * this check first failed.
             */
            int polls = refuseUntil(view, HALFWAY, null,
                    new byte[]{'P','R','C','9'}, new byte[4]);
            check(polls >= 3, "Too few refused frames to prove anything: " + polls);
            check(inkPixels(draw(view), 0, 40, 900, 500) == drawn,
                    "The battlefield changed while a refusal was still a blink");
            // A good frame still gets through the hold.
            view.showCombatSample(combatPacket(new int[][]{{1, 10, 10}, {2, 30, 18}}));
            check(inkPixels(draw(view), 0, 40, 900, 500) != drawn,
                    "A real battlefield update was swallowed by the hold");
        });

        run("A battlefield refused for longer than the hold does clear", () -> {
            LiveMapView view = map(context, 900, 520);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            int drawn = inkPixels(draw(view), 0, 40, 900, 500);
            refuseUntil(view, ReadingHold.HOLD_MS + 500, (byte[]) null);
            check(inkPixels(draw(view), 0, 40, 900, 500) < drawn - 200,
                    "A battlefield that has been gone for over " + ReadingHold.HOLD_MS
                            + "ms is not a blink, and must not still be drawn");
        });

        run("A refused party does not blink the party pane away", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showPartySample(partyPacket(6));
            int drawn = inkPixels(draw(view), 1000, 0, 1440, 600);
            check(drawn > 200, "The party never drew in the first place");
            int polls = refusePartyUntil(view, HALFWAY);
            check(polls >= 3, "Too few refused frames to prove anything: " + polls);
            check(inkPixels(draw(view), 1000, 0, 1440, 600) == drawn,
                    "The party changed while a refusal was still a blink");
            view.showPartySample(partyPacket(4));
            check(inkPixels(draw(view), 1000, 0, 1440, 600) != drawn,
                    "A real party update was swallowed by the hold");
        });

        run("A party refused for longer than the hold does clear", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showPartySample(partyPacket(6));
            int drawn = inkPixels(draw(view), 1000, 0, 1440, 600);
            check(drawn > 200, "The party never drew");
            check(String.valueOf(view.getContentDescription()).contains("Arax"),
                    "The party is not in the accessible text to begin with");
            refusePartyUntil(view, ReadingHold.HOLD_MS + 500);
            /*
             * Not zero ink: with the party gone the map reclaims the width and
             * its right-aligned status text moves into this strip. What must be
             * true is that the party itself is no longer drawn or described.
             */
            check(inkPixels(draw(view), 1000, 0, 1440, 600) < drawn - 200,
                    "A party unreadable for over " + ReadingHold.HOLD_MS
                            + "ms is not a blink, and must not still be drawn");
            check(!String.valueOf(view.getContentDescription()).contains("Arax"),
                    "The accessible text still lists a party that is no longer readable");
            check(String.valueOf(view.getContentDescription()).length() > 0,
                    "The pane went silent instead of saying what it knows");
        });

        run("Putting the pane away drops every held reading at once", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showPartySample(partyPacket(6));
            int drawn = inkPixels(draw(view), 1000, 0, 1440, 600);
            check(drawn > 200, "The party never drew");
            view.clearReadings();
            // Again, the map reclaims the strip; what must go is the party.
            check(inkPixels(draw(view), 1000, 0, 1440, 600) < drawn - 200,
                    "A held party survived the pane being put away");
            check(!String.valueOf(view.getContentDescription()).contains("Arax"),
                    "The accessible text still lists a party the pane has dropped");
        });

        run("Tapping a combatant lights that party row, and only for a while", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showPartySample(loadPacket(9, 9, 9, 9, 9, 9));
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            draw(view); // the grid geometry is recorded as it is drawn

            CombatSnapshotGeometry geometry = new CombatSnapshotGeometry(BATTLE);
            // BATTLE's third party entry is Tanarakis, at 28,12.
            float[] where = spotCentre(view, geometry, 28, 12);
            check(where[2] >= 3, "The battle grid drew too small to tap");
            Bitmap grid = draw(view);
            check(inkPixels(grid, (int) (where[0] - where[2] * .4f), (int) (where[1] - where[2] * .4f),
                            (int) (where[0] + where[2] * .4f), (int) (where[1] + where[2] * .4f)) > 0,
                    "No marker where the geometry says Tanarakis stands; the check and the view have drifted");

            check(mentions(view, "tapped on the battle overview") == 0, "A row was lit before anything was tapped");
            tap(view, where[0], where[1]);
            check(mentions(view, "tapped on the battle overview") == 1, "Tapping a combatant lit no row");
            String described = String.valueOf(view.getContentDescription());
            int at = described.indexOf("tapped on the battle overview");
            check(described.lastIndexOf("Tanarakis", at) > described.lastIndexOf("Hogarth", at),
                    "The tap lit somebody other than the combatant under it");
            check(inkPixels(draw(view), 1000, 0, 1440, 600) > inkPixels(grid, 1000, 0, 1440, 600),
                    "The lit row drew no box");

            // It identifies; it must not command, and it must not persist.
            idle(ReadingHold.HOLD_MS);
            check(mentions(view, "tapped on the battle overview") == 0,
                    "The highlight outlived its three seconds");

            // A monster has no row, so tapping one lights nothing.
            float[] monster = spotCentre(view, geometry, 20, 12);
            tap(view, monster[0], monster[1]);
            check(mentions(view, "tapped on the battle overview") == 0, "Tapping a monster lit a party row");
        });

        run("The header names what the party is fighting", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showSample(mapPacket(2, 5));
            view.showCombatSample(combatPacket(BATTLE));
            String plain = String.valueOf(view.getContentDescription());
            check(plain.contains("4 others"), "Without names the header should count: " + plain);

            byte[] named = combatPacket(BATTLE);
            named[CombatSnapshot.FOES_OUT] = 1;
            byte[] word = "GOBLIN".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(word, 0, named, CombatSnapshot.FOES_OUT + 1, word.length);
            named[CombatSnapshot.FOES_OUT + 1 + CombatSnapshot.FOE_NAME] = 4;
            view.showCombatSample(named);
            String said = String.valueOf(view.getContentDescription());
            check(said.contains("4 GOBLIN"), "The header did not name the monsters: " + said);
            check(!said.contains("4 others"), "It named them and counted them anyway: " + said);
        });

        run("Only the member load has slowed carries the W", () -> {
            LiveMapView view = map(context, 1440, 684);
            view.showPartySample(loadPacket(9, 9, 9, 9, 9, 9));
            int even = inkPixels(draw(view), 1000, 0, 1440, 600);
            check(mentions(view, "slowed by load") == 0,
                    "A party all moving alike must carry no W at all");

            view.showPartySample(loadPacket(9, 9, 6, 9, 9, 9));
            int one = inkPixels(draw(view), 1000, 0, 1440, 600);
            check(mentions(view, "slowed by load") == 1,
                    "Exactly the one hauling the loot should be marked");
            check(String.valueOf(view.getContentDescription()).contains("Tanarakis"),
                    "The marked member is not the one who is slow");
            check(one > even, "The W added no ink to the party pane: " + even + " -> " + one);

            /*
             * The case a purely relative test would miss: nobody is behind
             * anybody, but the game itself has stopped distinguishing degrees
             * of slow, so every one of them is marked.
             */
            view.showPartySample(loadPacket(3, 3, 3, 3, 3, 3));
            check(mentions(view, "slowed by load") == 6,
                    "A uniformly overloaded party went unmarked");
            check(inkPixels(draw(view), 1000, 0, 1440, 600) > one,
                    "Six W marks drew no more ink than one");
        });

        run("A fallen party member draws a cross, not a circle", () -> {
            // The ones worth walking to. A dead monster is not sent at all, but
            // one of your own who is down is exactly what you are looking for.
            LiveMapView standing = map(context, 900, 520);
            standing.showSample(mapPacket(2, 5));
            standing.showCombatSample(combatPacket(new int[][]{{1, 10, 10}}));
            int circle = inkPixels(draw(standing), 0, 40, 900, 500);
            LiveMapView down = map(context, 900, 520);
            down.showSample(mapPacket(2, 5));
            down.showCombatSample(combatPacket(new int[][]{{4, 10, 10, 5}}));   // dying
            Bitmap drawn = draw(down);
            int cross = inkPixels(drawn, 0, 40, 900, 500);
            check(cross > 0, "A fallen character drew nothing at all");
            check(cross != circle, "A cross is indistinguishable from a circle: "
                    + cross + " vs " + circle);
            // Dying and dead are different shapes, not the same one in a
            // different weight: weight alone does not read at this size.
            LiveMapView gone = map(context, 900, 520);
            gone.showSample(mapPacket(2, 5));
            gone.showCombatSample(combatPacket(new int[][]{{4, 10, 10, 6}}));   // dead
            int upright = inkPixels(draw(gone), 0, 40, 900, 500);
            check(upright > 0, "A dead character drew nothing at all");
            check(upright != cross, "Dead is indistinguishable from dying: "
                    + upright + " vs " + cross);
            check(String.valueOf(down.getContentDescription()).contains("Battle overview"),
                    "A battle of one fallen character stopped being a battle");
        });

        System.out.println(passed + " combat overview checks passed");
    }
}
