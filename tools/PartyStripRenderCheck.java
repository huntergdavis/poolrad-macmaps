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

    private static byte[] mapPacket() {
        byte[] p = new byte[1204];
        p[0]='P';p[1]='R';p[2]='M';p[3]='5';
        p[24]=1;p[25]=1;p[26]=1;p[27]=4;p[31]=42;p[32]=1;p[33]=1;p[35]=20;
        p[130]=15;p[131]=1;p[132]=6;p[176]=0x12;p[432]=0x34;p[944]=(byte)0xe4;
        p[1200]=(byte)255;
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

        for (int dpi : new int[]{320, 420, 480}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.densityDpi = dpi;
            Context dense = new ContextThemeWrapper(app.createConfigurationContext(config),
                    android.R.style.Theme_Material_Light_NoActionBar);
            for (int count : new int[]{6, 8}) {
                LiveMapView view = new LiveMapView(dense, null);
                int w = 1440, h = 760;
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, w, h);
                view.showSample(mapPacket());
                view.showPartySample(partyPacket(count));
                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                view.draw(canvas);
                String path = "/data/local/tmp/party-" + dpi + "dpi-" + count + ".png";
                try (FileOutputStream out = new FileOutputStream(path)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                System.out.println("wrote " + path + "  density="
                        + dense.getResources().getDisplayMetrics().density);
            }
        }
    }
}
