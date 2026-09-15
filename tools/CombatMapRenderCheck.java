import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import name.osher.gil.minivmac.LiveMapView;

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

        System.out.println(passed + " combat overview checks passed");
    }
}
