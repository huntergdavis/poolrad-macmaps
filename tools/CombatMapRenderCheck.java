import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.View;
import name.osher.gil.minivmac.LiveMapView;
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
        byte[] p = new byte[8 + 71 * 4];
        p[0]='P'; p[1]='R'; p[2]='C'; p[3]='1'; p[4]=1; p[5]=(byte) rows.length;
        for (int i = 0; i < rows.length; i++) {
            p[8+i*4] = (byte) rows[i][0]; p[8+i*4+1] = (byte) rows[i][1]; p[8+i*4+2] = (byte) rows[i][2];
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

        run("A fallen party member draws a cross, not a circle", () -> {
            // The ones worth walking to. A dead monster is not sent at all, but
            // one of your own who is down is exactly what you are looking for.
            LiveMapView standing = map(context, 900, 520);
            standing.showSample(mapPacket(2, 5));
            standing.showCombatSample(combatPacket(new int[][]{{1, 10, 10}}));
            int circle = inkPixels(draw(standing), 0, 40, 900, 500);
            LiveMapView down = map(context, 900, 520);
            down.showSample(mapPacket(2, 5));
            down.showCombatSample(combatPacket(new int[][]{{4, 10, 10}}));
            Bitmap drawn = draw(down);
            int cross = inkPixels(drawn, 0, 40, 900, 500);
            check(cross > 0, "A fallen character drew nothing at all");
            check(cross != circle, "A cross is indistinguishable from a circle: "
                    + cross + " vs " + circle);
            check(String.valueOf(down.getContentDescription()).contains("Battle overview"),
                    "A battle of one fallen character stopped being a battle");
        });

        System.out.println(passed + " combat overview checks passed");
    }
}
