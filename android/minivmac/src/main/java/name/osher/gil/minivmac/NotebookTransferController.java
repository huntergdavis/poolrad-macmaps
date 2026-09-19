package name.osher.gil.minivmac;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import name.osher.gil.minivmac.notebook.NotebookStore;

/** Local user-owned notebook documents only; no emulator or network dependency.
 * Reuses ScreenshotController's Activity-owned picker and immutable-source pattern.
 */
public final class NotebookTransferController {
    private static final String PENDING = "notebook.transfer.file";
    private static final String IMPORTING = "notebook.transfer.importPicker";
    private final AppCompatActivity activity;
    private final Context context;
    private final File directory;
    private final NotebookStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> saveArchive, savePng, savePdf;
    private final ActivityResultLauncher<String[]> openArchive;
    private File pending;
    private boolean importPicker, busy, destroyed;
    private AlertDialog retry;

    // Called unconditionally in onCreate: registration order survives recreation.
    public NotebookTransferController(AppCompatActivity activity, Bundle state) {
        this.activity = activity; context = activity.getApplicationContext();
        directory = new File(context.getCacheDir(), "notebook-transfers");
        store = new NotebookStore(new File(context.getFilesDir(), "notebooks"));
        if (state != null) {
            try { pending = NotebookTransferFiles.restore(directory, state.getString(PENDING)); }
            catch (IOException failure) { Log.w("PoolRad.Backup", "Pending export unavailable", failure); }
            importPicker = state.getBoolean(IMPORTING, false);
        }
        saveArchive = activity.registerForActivityResult(localCreate("application/octet-stream"), this::saveResult);
        savePng = activity.registerForActivityResult(localCreate("image/png"), this::saveResult);
        savePdf = activity.registerForActivityResult(localCreate("application/pdf"), this::saveResult);
        openArchive = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument() {
            @Override public Intent createIntent(Context context, String[] types) {
                return super.createIntent(context, types).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }
        }, this::importResult);
    }

    private static ActivityResultContracts.CreateDocument localCreate(String mime) {
        return new ActivityResultContracts.CreateDocument(mime) {
            @Override public Intent createIntent(Context context, String name) {
                return super.createIntent(context, name).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }
        };
    }

    public void onSaveInstanceState(Bundle state) {
        if (pending != null) state.putString(PENDING, pending.getName());
        state.putBoolean(IMPORTING, importPicker);
    }
    public void onDestroy() {
        destroyed = true;
        if (retry != null) retry.dismiss();
        // Pending picker sources and already queued IO survive Activity replacement.
    }
    private boolean start() {
        if (destroyed || activity.isFinishing()) return false;
        if (busy || pending != null || importPicker) {
            toast("Finish the current notebook transfer first."); return false;
        }
        if (retry != null) retry.dismiss();
        busy = true; return true;
    }
    private void toast(String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }

    public void exportNotebook(NotebookStore.Notebook book) {
        if (book == null || !start()) return;
        toast("Preparing " + book.label() + " backup…");
        prepare("prnb", out -> store.exportNotebook(book.id(), out), () -> { });
    }

    /** Save an already-built notes PDF (F53); the document is closed once written. */
    public void exportPdf(String label, android.graphics.pdf.PdfDocument document) {
        if (document == null) return;
        if (!start()) { document.close(); return; }
        toast("Preparing " + label + "…");
        prepare("pdf", document::writeTo, document::close);
    }

    public interface PageRenderer { Bitmap render(); }
    public void exportPage(PageRenderer renderer) {
        if (!start()) return;
        final Bitmap page;
        try { page = renderer.render(); }
        catch (RuntimeException | OutOfMemoryError failure) {
            busy = false; Log.w("PoolRad.Backup", "Cannot render note page", failure);
            toast("Could not prepare the page image. Your note is unchanged."); return;
        }
        prepare("png", out -> {
            if (!page.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG encoding failed");
        }, page::recycle);
    }

    private interface Writer { void write(OutputStream out) throws IOException; }
    private void prepare(String extension, Writer writer, Runnable release) {
        NotebookController.IO.execute(() -> {
            File file = null;
            try {
                NotebookTransferFiles.discardExpired(directory, pending, System.currentTimeMillis());
                file = NotebookTransferFiles.create(directory, extension);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    writer.write(out); out.flush(); out.getFD().sync();
                }
                final File ready = file;
                main.post(() -> { busy = false; if (!destroyed) launchSave(ready); });
            } catch (IOException | RuntimeException | OutOfMemoryError failure) {
                if (file != null) file.delete(); // Only our incomplete cache export.
                Log.w("PoolRad.Backup", "Could not prepare export", failure);
                main.post(() -> { busy = false; toast("Backup/image was not prepared. Your notebook is unchanged. " + failure.getMessage()); });
            } finally {
                release.run();
            }
        });
    }

    private void launchSave(File file) {
        if (destroyed || activity.isFinishing()) return;
        pending = file;
        try {
            (file.getName().endsWith(".png") ? savePng
                    : file.getName().endsWith(".pdf") ? savePdf : saveArchive).launch(file.getName());
        }
        catch (RuntimeException failure) {
            pending = null; Log.w("PoolRad.Backup", "No save picker", failure);
            showRetry(file, "Android could not open the save picker. Nothing was exported.");
        }
    }

    private void saveResult(Uri uri) {
        final File source = pending; pending = null;
        if (uri == null) { toast("Export cancelled; no backup was saved."); return; }
        if (source == null || !source.isFile()) {
            toast("Prepared export is no longer available. Export the notebook/page again."); return;
        }
        busy = true;
        NotebookController.IO.execute(() -> {
            boolean success = false;
            try (OutputStream destination = context.getContentResolver().openOutputStream(uri, "wt")) {
                NotebookTransferFiles.copy(source, destination);
                success = true;
            } catch (IOException | RuntimeException failure) {
                success = false; Log.w("PoolRad.Backup", "Document save failed", failure);
            }
            final boolean saved = success;
            main.post(() -> {
                busy = false;
                if (saved) {
                    toast(source.getName().endsWith(".png") ? "Page image saved. PNG is not an editable notebook backup." : "Notebook backup saved to your chosen file.");
                } else {
                    String message = "Save failed; the destination may be incomplete. Your original notebook is unchanged.";
                    toast(message); if (!destroyed) showRetry(source, message);
                }
            });
        });
    }

    private void showRetry(File source, String message) {
        if (destroyed || activity.isFinishing()) return;
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(activity); text.setTextColor(Color.BLACK); text.setText(message); content.addView(text);
        Button again = new Button(activity); again.setText("Retry save"); content.addView(again);
        retry = UpperHalfReferenceDialog.show(activity, "Export not saved", content);
        again.setOnClickListener(v -> { retry.dismiss(); launchSave(source); });
    }

    public void importNotebook() {
        if (!start()) return;
        busy = false; importPicker = true;
        try { openArchive.launch(new String[]{"*/*"}); }
        catch (RuntimeException failure) {
            importPicker = false; Log.w("PoolRad.Backup", "No import picker", failure);
            toast("Android could not open the backup picker. Existing notebooks are unchanged.");
        }
    }

    private void importResult(Uri uri) {
        importPicker = false;
        if (uri == null) return;
        busy = true; toast("Checking notebook backup…");
        NotebookController.IO.execute(() -> {
            NotebookStore.Notebook restored = null;
            String problem = null;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Backup file is unavailable");
                restored = store.importNotebook(in);
            } catch (IOException | RuntimeException failure) {
                Log.w("PoolRad.Backup", "Notebook import failed", failure); problem = failure.getMessage();
            }
            final NotebookStore.Notebook result = restored;
            final String detail = problem;
            main.post(() -> {
                busy = false;
                // A close failure after committed import must not falsely say it was rolled back.
                if (result != null) toast("Restored " + result.label() + ". Select it in PoolRad → Notebooks. Your current campaign was not switched.");
                else toast("Backup not imported; existing notebooks unchanged. " + detail);
            });
        });
    }
}
