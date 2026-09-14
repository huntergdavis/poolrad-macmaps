package name.osher.gil.minivmac;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captures the activity's app composition, then offers Android save and share. */
public final class ScreenshotController {
    private static final String TAG = "PoolRad.Screenshot";
    private static final String STATE_PENDING_FILE = "screenshot.pendingSaveFile";
    private final AppCompatActivity activity;
    private final Context appContext;
    private final File directory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> saveDocument;
    private File pendingSaveFile;
    private AlertDialog actions;
    private boolean busy;
    private boolean destroyed;

    /** Construct unconditionally during Activity.onCreate, before it is started. */
    public ScreenshotController(AppCompatActivity activity, Bundle savedInstanceState) {
        this.activity = activity;
        appContext = activity.getApplicationContext();
        directory = new File(appContext.getCacheDir(), "screenshots");
        if (savedInstanceState != null) {
            try {
                pendingSaveFile = ScreenshotFiles.restore(directory,
                        savedInstanceState.getString(STATE_PENDING_FILE));
            } catch (IOException failure) {
                Log.w(TAG, "Cannot restore pending screenshot", failure);
            }
        }
        saveDocument = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"), this::saveToDocument);
    }

    public void onSaveInstanceState(Bundle outState) {
        if (pendingSaveFile != null) outState.putString(STATE_PENDING_FILE, pendingSaveFile.getName());
    }

    public void onDestroy() {
        destroyed = true;
        if (actions != null) actions.dismiss();
        io.shutdown(); // A selected document already being written is allowed to finish.
    }

    public void captureAfterMenuDismissed() {
        if (destroyed) return;
        if (busy || pendingSaveFile != null) {
            toast(R.string.screenshot_busy);
            return;
        }
        busy = true;
        activity.closeOptionsMenu();
        View composition = activity.findViewById(android.R.id.content);
        // Leave the menu callback first, then capture at the next UI frame. Menus
        // are separate windows and are never part of this content view's drawing.
        composition.post(() -> composition.postOnAnimation(() -> capture(composition)));
    }

    private void capture(View composition) {
        if (destroyed || activity.isFinishing()) {
            busy = false;
            return;
        }
        ScreenView guest = composition.findViewById(R.id.screen);
        if (!composition.isAttachedToWindow() || composition.getWidth() < 1
                || composition.getHeight() < 1 || guest == null || !guest.isShown()
                || !guest.hasScreenFrame()) {
            busy = false;
            toast(R.string.screenshot_guest_not_ready);
            return;
        }
        Bitmap frame = null;
        try {
            // ScreenView is an ordinary View backed by mScreenBits, not a
            // SurfaceView. Both it and LiveMapView receive updates on the main
            // handler. One synchronous draw cannot interleave those updates,
            // and includes keyboard, map ink/flags, and future child panels.
            // Fail honestly if a future renderer adds an uncapturable surface.
            if (containsVisibleSurface(composition)) throw new IllegalStateException("Surface renderer needs capture support");
            frame = Bitmap.createBitmap(composition.getWidth(), composition.getHeight(), Bitmap.Config.ARGB_8888);
            frame.setDensity(activity.getResources().getDisplayMetrics().densityDpi);
            Canvas canvas = new Canvas(frame);
            canvas.drawColor(Color.BLACK);
            composition.draw(canvas);
            final Bitmap captured = frame;
            io.execute(() -> writeCapture(captured));
        } catch (RuntimeException | OutOfMemoryError failure) {
            if (frame != null) frame.recycle();
            busy = false;
            Log.w(TAG, "Screenshot capture failed", failure);
            toast(R.string.screenshot_failed);
        }
    }

    private static boolean containsVisibleSurface(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof SurfaceView) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsVisibleSurface(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private void writeCapture(Bitmap frame) {
        File file = null;
        try {
            ScreenshotFiles.discardExpired(directory, null, System.currentTimeMillis());
            file = ScreenshotFiles.create(directory);
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!frame.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG encoding failed");
                out.flush();
                out.getFD().sync();
            }
            final File ready = file;
            main.post(() -> {
                busy = false;
                if (!destroyed && !activity.isFinishing()) showActions(ready);
            });
        } catch (IOException | RuntimeException | OutOfMemoryError failure) {
            if (file != null) file.delete();
            Log.w(TAG, "Cannot prepare screenshot PNG", failure);
            main.post(() -> {
                busy = false;
                if (!destroyed) toast(R.string.screenshot_failed);
            });
        } finally {
            frame.recycle();
        }
    }

    private void showActions(File file) {
        if (actions != null) actions.dismiss();
        actions = new AlertDialog.Builder(activity)
                .setTitle(R.string.screenshot_title)
                .setMessage(R.string.screenshot_ready)
                .setPositiveButton(R.string.screenshot_save, (dialog, which) -> {
                    pendingSaveFile = file;
                    try {
                        saveDocument.launch(file.getName());
                    } catch (RuntimeException failure) {
                        pendingSaveFile = null;
                        Log.w(TAG, "Cannot launch document picker", failure);
                        toast(R.string.screenshot_save_failed);
                        main.post(() -> { if (!destroyed) showActions(file); });
                    }
                })
                .setNeutralButton(R.string.screenshot_share, (dialog, which) -> share(file))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveToDocument(Uri uri) {
        final File source = pendingSaveFile;
        pendingSaveFile = null;
        if (uri == null) return; // Cancelling a picker never reports a save.
        if (source == null || !source.isFile()) {
            toast(R.string.screenshot_missing);
            return;
        }
        busy = true;
        io.execute(() -> {
            boolean success = false;
            try (OutputStream destination = appContext.getContentResolver().openOutputStream(uri, "wt")) {
                ScreenshotFiles.copy(source, destination);
                success = true;
            } catch (IOException | RuntimeException failure) {
                success = false;
                Log.w(TAG, "Cannot save screenshot to selected document", failure);
            }
            final boolean saved = success;
            main.post(() -> {
                busy = false;
                // Application-context feedback also survives activity recreation.
                toast(saved ? R.string.screenshot_saved : R.string.screenshot_save_failed);
                if (!saved && !destroyed && !activity.isFinishing()) showActions(source);
            });
        });
    }

    private void share(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".screenshots", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(activity.getContentResolver(), "Screenshot", uri));
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.screenshot_share)));
        } catch (RuntimeException failure) {
            Log.w(TAG, "Cannot share screenshot", failure);
            toast(R.string.screenshot_share_failed);
            main.post(() -> { if (!destroyed) showActions(file); });
        }
    }

    private void toast(int message) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
    }
}
