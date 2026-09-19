package name.osher.gil.minivmac;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Small optional guest-screen sidecars. Both methods belong on the IO worker. */
final class SavePreview {
    static byte[] encode(int[] pixels, int width, int height) throws IOException {
        if (pixels == null) return null;
        if (width < 1 || width > 384 || height < 1 || height > 288 || pixels.length != width * height)
            throw new IOException("Invalid preview dimensions");
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Preview encoding failed");
            return out.toByteArray();
        } finally { bitmap.recycle(); }
    }

    static Bitmap read(File save) {
        File file = SaveStateStore.previewFile(save);
        if (!file.isFile() || file.length() > SaveStateStore.MAX_PREVIEW_BYTES) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth < 1 || bounds.outWidth > 384 || bounds.outHeight < 1 || bounds.outHeight > 288) return null;
        return BitmapFactory.decodeFile(file.getPath());
    }
}
