package name.osher.gil.minivmac;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Same native save boundary and reference/diff format; PNG and disk work stay off-thread. */
public final class SaveStateController {
    public interface CoreAccess { Core current(); }
    public interface NotebookLink {
        String currentNotebookId();
        boolean readyForLoad();
        void prepareAutoLoad(String notebookId, java.util.function.Consumer<NotebookRestore> ready);
        void prepareLoad(String notebookId, java.util.function.Consumer<NotebookRestore> ready);
    }
    public interface NotebookRestore {
        String notebookId();
        boolean begin();
        void finish(boolean restored, Runnable finished);
    }
    private static final String TAG = "PoolRad.SaveState";
    private static final String LOAD_WARNING = "Replaces the running session; unsaved progress is lost. The mounted disks must match the snapshot. Older snapshots are unsupported.";
    private final Activity activity;
    private final CoreAccess access;
    private final SaveStateStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SaveRequestGate requests = new SaveRequestGate();
    private volatile boolean disposed;
    private boolean loading;
    private NotebookLink notebook;
    private AlertDialog picker;
    private StartupLoad startup;

    public SaveStateController(Activity activity, CoreAccess access) {
        this.activity = activity; this.access = access;
        store = new SaveStateStore(new File(activity.getFilesDir(), "savestates"));
    }
    public void setNotebookLink(NotebookLink link) { notebook = link; }
    private String currentNotebookId() {
        try { return notebook == null ? null : notebook.currentNotebookId(); }
        catch (RuntimeException ignored) { return null; }
    }
    public void dispose() {
        if (startup != null) startup.cancel(null);
        disposed = true;
        if (picker != null) picker.dismiss();
        io.shutdown(); // An accepted write may finish; late native callbacks are ignored.
    }

    /** Called on the emulation thread: no bitmap creation, compression or storage here. */
    public void onState(byte[] state, int[] pixels, int width, int height, DiskSnapshotGuard.Ticket disks) {
        SaveRequestGate.Request request = requests.captured();
        if (disposed || request == null) return;
        long capturedAt = System.currentTimeMillis();
        long captureMs = SystemClock.elapsedRealtime() - request.started;
        if (state == null || disks == null) {
            requests.finish(request);
            if (request.kind != SaveRequestGate.Kind.AUTO) main.post(() -> toast("The machine could not be captured."));
            return;
        }
        try {
            io.execute(() -> {
                long began = SystemClock.elapsedRealtime();
                try {
                    DiskSnapshotGuard.Fingerprint fingerprint = disks.fingerprint();
                    File written;
                    if (request.kind == SaveRequestGate.Kind.QUICK) written = store.writeQuick(state, capturedAt, fingerprint);
                    else if (request.kind == SaveRequestGate.Kind.AUTO) {
                        String stamp = new SimpleDateFormat("MMM d h-mm-ss a", Locale.US).format(new Date(capturedAt));
                        written = store.writeAuto(SaveStateStore.AUTO_PREFIX + stamp, state, 20, fingerprint);
                    } else written = store.write(request.label, state, fingerprint);
                    store.writeBinding(written, request.notebook);
                    store.rememberLatest(written);
                    long previewStart = SystemClock.elapsedRealtime();
                    boolean preview = false;
                    try {
                        byte[] png = SavePreview.encode(pixels, width, height);
                        if (png != null) { store.writePreview(written, png); preview = true; }
                    } catch (IOException | RuntimeException | OutOfMemoryError failure) {
                        Log.w(TAG, "State saved; optional preview unavailable", failure);
                    }
                    Log.i(TAG, "Saved " + request.kind + ": capture=" + captureMs + "ms, worker="
                            + (SystemClock.elapsedRealtime() - began) + "ms, preview="
                            + (SystemClock.elapsedRealtime() - previewStart) + "ms");
                    if (request.kind != SaveRequestGate.Kind.AUTO) {
                        final String message = "Saved " + SaveStateStore.displayLabel(written)
                                + (preview ? "" : " (preview unavailable)");
                        main.post(() -> toast(message));
                    }
                } catch (IOException | RuntimeException failure) {
                    Log.w(TAG, "Save failed", failure);
                    if (request.kind != SaveRequestGate.Kind.AUTO)
                        main.post(() -> toast("Could not save: " + failure.getMessage()));
                } finally { requests.finish(request); }
            });
        } catch (RejectedExecutionException stopped) { requests.finish(request); }
    }

    private void request(SaveRequestGate.Kind kind, String label) {
        if (disposed) return;
        Core core = access.current(); boolean quiet = kind == SaveRequestGate.Kind.AUTO;
        if (core == null || !core.isReady()) { if (!quiet) toast("The emulator is not running."); return; }
        SaveRequestGate.Request request = new SaveRequestGate.Request(kind, label, currentNotebookId(), SystemClock.elapsedRealtime());
        if (loading || !requests.begin(request)) { if (!quiet) toast("Finishing the current save/load…"); return; }
        if (!core.requestSaveState()) {
            requests.finish(request);
            if (!quiet) toast("The machine is not ready to save.");
        }
    }
    public void autoSave() { request(SaveRequestGate.Kind.AUTO, null); }
    public void quickSave() { request(SaveRequestGate.Kind.QUICK, null); }
    /** One tap restores the newest successful quick save, not an autosave or named state. */
    public void quickLoad() {
        if (disposed) return;
        io.execute(() -> {
            List<File> saves = store.quickSaves();
            main.post(() -> {
                if (!alive()) return;
                if (saves.isEmpty()) toast("No quick saves yet. Use Quick save.");
                else load(saves.get(0));
            });
        });
    }

    public void saveNamed() {
        if (disposed) return;
        EditText field = new EditText(activity); field.setHint("Save name");
        picker = new AlertDialog.Builder(activity).setTitle("New save state").setView(field)
                .setPositiveButton("Save", (d,w) -> {
                    String label = field.getText().toString().trim();
                    request(SaveRequestGate.Kind.NAMED, label.isEmpty() ? "Save state" : label);
                }).setNegativeButton("Cancel", null).show();
    }
    public void chooseSave() {
        if (disposed) return;
        io.execute(() -> {
            List<File> saves = store.saves();
            main.post(() -> {
                if (!alive()) return;
                if (saves.isEmpty()) {
                    saveNamed();
                    return;
                }
                picker = showPicker(new AlertDialog.Builder(activity)
                        .setTitle("Load state · " + saves.size())
                        .setAdapter(new PreviewAdapter(saves), (d,which) -> confirmLoad(saves.get(which)))
                        .setNeutralButton("New save…", (d,w) -> saveNamed())
                        .setNegativeButton("Close", null));
            });
        });
    }

    private AlertDialog showPicker(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        if (!CompanionDialogBounds.prepare(activity, dialog)) return dialog;
        dialog.show();
        CompanionDialogBounds.Binding bounds = CompanionDialogBounds.track(activity, dialog);
        dialog.setOnDismissListener(ignored -> bounds.close());
        return dialog;
    }

    private final class PreviewAdapter extends BaseAdapter {
        private final List<File> saves;
        PreviewAdapter(List<File> saves) { this.saves = saves; }
        @Override public int getCount() { return saves.size(); }
        @Override public File getItem(int at) { return saves.get(at); }
        @Override public long getItemId(int at) { return at; }
        @Override public View getView(int at, View recycled, ViewGroup parent) {
            LinearLayout row = new LinearLayout(activity); row.setPadding(dp(12),dp(8),dp(12),dp(8));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(android.graphics.Color.LTGRAY);
            row.addView(image,new LinearLayout.LayoutParams(dp(96),dp(72)));
            TextView title = new TextView(activity); title.setText(SaveStateStore.displayLabel(getItem(at)));
            title.setTextSize(15); title.setTextColor(android.graphics.Color.BLACK); title.setPadding(dp(12),0,0,0);
            row.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            row.setBackgroundColor(android.graphics.Color.WHITE);
            readPreview(getItem(at), image, null);
            return row;
        }
    }
    private void confirmLoad(File file) {
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(4),dp(16),dp(8));
        ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(image,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView explanation = new TextView(activity); explanation.setTextSize(14);
        explanation.setText("Loading preview…\n" + LOAD_WARNING);
        content.addView(explanation);
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        picker = showPicker(new AlertDialog.Builder(activity).setTitle(SaveStateStore.displayLabel(file)).setView(scroll)
                .setPositiveButton("Load", (d,w) -> load(file))
                .setNeutralButton("Delete…", (d,w) -> confirmDelete(file))
                .setNegativeButton("Cancel", null));
        readPreview(file, image, explanation);
    }
    private void readPreview(File file, ImageView image, TextView explanation) {
        io.execute(() -> {
            Bitmap bitmap;
            try { bitmap = SavePreview.read(file); }
            catch (RuntimeException | OutOfMemoryError ignored) { bitmap = null; }
            final Bitmap ready = bitmap;
            main.post(() -> {
                if (!alive() || !image.isAttachedToWindow()) { if (ready != null) ready.recycle(); return; }
                image.setImageBitmap(ready);
                image.setContentDescription(ready == null ? "No preview for this save" : "Guest screen at save time");
                if (explanation != null) explanation.setText((ready == null ? "No preview for this save.\n" : "")
                        + LOAD_WARNING);
            });
        });
    }
    private void confirmDelete(File file) {
        picker = showPicker(new AlertDialog.Builder(activity).setTitle("Delete this save?")
                .setMessage(SaveStateStore.displayLabel(file) + "\nThis cannot be undone.")
                .setPositiveButton("Delete", (d,w) -> io.execute(() -> {
                    boolean deleted = store.delete(file);
                    main.post(() -> { toast(deleted ? "Save deleted" : "Could not delete save"); if (alive()) chooseSave(); });
                })).setNegativeButton("Cancel", null));
    }
    /** First guest tick is held by Core until this one launch attempt queues or declines. */
    public void autoLoadLatest(Core core) {
        StartupRestoreGate gate = core.startupGate();
        if (gate == null) return;
        if (!alive() || core != access.current() || loading || requests.busy()) {
            gate.cancel(); return;
        }
        loading = true;
        StartupLoad launch = new StartupLoad(core, gate);
        startup = launch;
        launch.dialog = showPicker(new AlertDialog.Builder(activity)
                .setTitle("Resuming last snapshot")
                .setMessage("Checking the saved machine and mounted disks…")
                .setCancelable(false)
                .setNegativeButton("Start normally", (d,w) -> launch.cancel("Automatic load skipped.")));
        main.postDelayed(launch.timeout, 30000);
        io.execute(() -> {
            try {
                File file = store.latestSave();
                if (file == null) main.post(() -> launch.cancel(null));
                else readForLoad(file, core, launch);
            } catch (IOException | RuntimeException failure) {
                main.post(() -> launch.cancel("Auto-load skipped: " + failure.getMessage()));
            }
        });
    }

    private final class StartupLoad {
        final Core core;
        final StartupRestoreGate gate;
        AlertDialog dialog;
        final Runnable timeout = () -> cancel("Auto-load timed out; starting normally.");
        StartupLoad(Core core, StartupRestoreGate gate) { this.core = core; this.gate = gate; }
        boolean active() { return startup == this && gate.waiting() && alive() && core == access.current(); }
        void close() {
            main.removeCallbacks(timeout);
            if (dialog != null) dialog.dismiss();
            if (startup == this) startup = null;
        }
        void cancel(String reason) {
            if (!gate.cancel()) return; // Once application begins, its real completion owns the outcome.
            close(); loading = false;
            Log.i(TAG, reason == null ? "No automatic launch restore" : reason);
            toast(reason);
        }
        void queued() { gate.release(); close(); }
    }

    private void load(File file) {
        Core core = access.current();
        if (!alive() || core == null || !core.isReady()) { toast("The emulator is not running."); return; }
        if (loading || requests.busy()) { toast("Finishing the current save/load…"); return; }
        loading = true;
        io.execute(() -> readForLoad(file, core, null));
    }

    /** Shared manual/launch path: exact disk verification before any notebook transition. */
    private void readForLoad(File file, Core core, StartupLoad launch) {
        try {
            SaveStateStore.Snapshot snapshot = store.readSnapshot(file);
            DiskSnapshotGuard.Verified disks = core.verifySnapshotDisks(snapshot.disks);
            String binding = store.readBinding(file);
            main.post(() -> prepareLoad(file, core, snapshot, disks, binding, launch));
        } catch (IOException | RuntimeException failure) {
            main.post(() -> {
                if (launch != null) launch.cancel("Auto-load skipped: " + failure.getMessage());
                else { loading = false; toast("Could not load: " + failure.getMessage()); }
            });
        }
    }

    private void prepareLoad(File file, Core core, SaveStateStore.Snapshot snapshot,
            DiskSnapshotGuard.Verified disks, String binding, StartupLoad launch) {
        if (launch != null && !launch.active()) { launch.cancel(null); return; }
        if (!alive() || core != access.current()) { loading = false; return; }
        if (notebook == null || (launch != null && !notebook.readyForLoad())) {
            if (launch != null) main.postDelayed(
                    () -> prepareLoad(file, core, snapshot, disks, binding, launch), 100);
            else { loading = false; toast("Open the companion notebook before loading."); }
            return;
        }
        java.util.function.Consumer<NotebookRestore> ready = transition -> {
            if (launch != null && !launch.active()) { launch.cancel(null); return; }
            if (transition == null || !alive() || core != access.current()) {
                if (launch != null) launch.cancel("Auto-load skipped: notebook unavailable. Load the snapshot manually to choose its campaign.");
                else loading = false;
                return;
            }
            if (launch != null && !launch.gate.beginApply()) return;
            if (!transition.begin()) {
                if (launch != null) launch.queued();
                loading = false; return;
            }
            Core.RestoreListener completed = restored -> main.post(() -> {
                Log.i(TAG, (launch == null ? "Restore completed: " : "Launch restore completed: ") + restored);
                transition.finish(restored, () -> {
                    loading = false;
                    if (!alive()) return;
                    if (restored) {
                        io.execute(() -> {
                            if (!store.writeBinding(file, transition.notebookId()))
                                main.post(() -> toast("Loaded, but notebook pairing could not be saved."));
                        });
                        toast("Loaded " + SaveStateStore.displayLabel(file));
                    } else toast("Snapshot refused: the machine or mounted disks changed. Try loading an original game save.");
                });
            });
            if (!core.restoreState(snapshot.state, disks, completed)) completed.completed(false);
            if (launch != null) launch.queued();
        };
        if (launch == null) notebook.prepareLoad(binding, ready);
        else notebook.prepareAutoLoad(binding, ready);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private boolean alive() { return !disposed && !activity.isFinishing(); }
    private void toast(String message) {
        if (alive() && message != null) Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();
    }
}
