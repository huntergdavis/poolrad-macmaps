import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import java.io.FileOutputStream;
import name.osher.gil.minivmac.LiveMapView;

/**
 * Renders the party pane at a chosen screen density so the high-density strip
 * can be looked at, not just asserted. Synthetic packets only.
 *
 * Build and run as CombatMapRenderCheck documents, adding this class; it writes
 * PNGs to /data/local/tmp for the emulator owner to pull.
 */
public final class PartyStripRenderCheck {
    public static void main(String[] args) {
        try { run(args.length == 0 ? "com.hunterdavis.poolradmacmaps.ii" : args[0]); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    /*
     * A PRM1 legacy packet, not PRM5. The current format only reports a live
     * map for an area whose fingerprint is in the shipped catalog, which no
     * synthetic map can be; the legacy path accepts geometry on its own, so the
     * harness draws a real grid, trail and caption instead of the bare
     * "position unavailable" header it used to.
     */
    private static byte[] mapPacket() {
        byte[] p = new byte[1200];
        p[0]='P';p[1]='R';p[2]='M';p[3]='1';
        p[24]=1;p[25]=1;p[26]=1;p[27]=4;p[31]=42;p[32]=1;p[33]=1;p[35]=20;
        p[130]=15;p[131]=1;p[132]=6;p[176]=0x12;p[432]=0x34;p[944]=(byte)0xe4;
        return p;
    }
    private static byte[] partyPacket(int count) {
        byte[] b = new byte[8 + 8 * 20];
        b[0]='P';b[1]='R';b[2]='P';b[3]='1';b[4]=(byte) count;
        String[] names = {"Arax the Bold","Lara Spellsword","Tanarakis","Hogarth",
                          "Shara the Grey","Zarram","Ohlo","Skullcrusher"};
        for (int i = 0; i < count; i++) {
            byte[] n = names[i].getBytes();
            System.arraycopy(n, 0, b, 8 + i * 20, Math.min(n.length, 15));
            b[8 + i * 20 + 16] = (byte) (7 + i);
            b[8 + i * 20 + 17] = (byte) 20;
        }
        return b;
    }

    private static void run(String pkg) throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null)
            Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
        Context app = system.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);

        /*
         * Hunter's own tablet, measured off his 2026-09-15 screenshots: a
         * 1440x1742 panel, companion pane 1440x684, density exactly 2.0
         * (320 dpi). The font scales are swept because that, not the density,
         * is what emptied the pane on the build he was running.
         */
        for (int dpi : new int[]{320, 480}) {
          for (float fontScale : new float[]{1f, 1.15f, 1.8f}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.densityDpi = dpi;
            config.fontScale = fontScale;
            Context dense = new ContextThemeWrapper(app.createConfigurationContext(config),
                    android.R.style.Theme_Material_Light_NoActionBar);
            // -1 stands for "the probe refused": a PRPX packet, which must put
            // the reason in the caption rather than leaving a silent empty pane.
            for (int count : new int[]{6, 7, 0, -1}) {
                LiveMapView view = new LiveMapView(dense, null);
                int w = 1440, h = 684;
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, w, h);
                view.showSample(mapPacket());
                if (count > 0) view.showPartySample(partyPacket(count));
                if (count < 0) view.showPartySample(new byte[]{'P','R','P','X',
                        7, 2, (byte) 0x82, 0, 1, 0x40});
                view.setExplorationStyle(false, count == 0);
                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                view.draw(canvas);
                String path = "/data/local/tmp/party-" + dpi + "dpi-x" + fontScale + "-" + count + ".png";
                try (FileOutputStream out = new FileOutputStream(path)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                System.out.println("wrote " + path + "  density="
                        + dense.getResources().getDisplayMetrics().density
                        + " scaledDensity=" + dense.getResources().getDisplayMetrics().scaledDensity);
            }
          }
        }
    }
}
