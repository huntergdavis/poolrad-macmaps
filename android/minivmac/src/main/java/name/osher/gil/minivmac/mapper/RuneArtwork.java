package name.osher.gil.minivmac.mapper;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Bundled artwork: available on first launch, with no cache or network request. */
public final class RuneArtwork {
    private final AssetManager assets;
    private final Bitmap[] images = new Bitmap[72];

    public RuneArtwork(AssetManager assets) { this.assets = assets; }

    public Bitmap read(boolean espruar, int id) {
        if (id < 1 || id > 36) throw new IllegalArgumentException("Rune ID outside 1–36");
        int index = (espruar ? 0 : 36) + id - 1;
        if (images[index] == null) {
            String name = String.format(Locale.ROOT, "codewheel/%s%02d.gif", espruar ? "esp" : "det", id);
            try (InputStream in = assets.open(name)) {
                images[index] = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                // Build validation normally prevents this; retain the numbered fallback.
                return null;
            }
        }
        return images[index];
    }
}
