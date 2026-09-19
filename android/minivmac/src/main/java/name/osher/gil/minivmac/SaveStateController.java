package name.osher.gil.minivmac;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The companion's own save and load: capture the whole emulated machine and
 * swap it back later, without the game's menus and without restarting.
 *
 * The save is asynchronous. Asking the core to save returns at once; the bytes
 * arrive later on the emulation thread, at {@link #onState}. So the target for
 * the pending save is remembered when the request goes out, and the compress
 * and file write happen on a background thread so the emulation thread is never
 * blocked. Loading is the mirror: read and decompress off-thread, then hand the
 * raw bytes to the core, which applies them at its next safe boundary.
 */
public final class SaveStateController {
    /** How the controller reaches the live emulator, which is recreated per session. */
    public interface CoreAccess { Core current(); }

    /**
     * The companion's notebook, so a save can be paired with the notebook that
     * was open when it was taken (F93) and a load can bring that notebook back.
     * Referenced by id; the notes themselves stay in the notebook store.
     */
    public interface NotebookLink {
        String currentNotebookId();
        void selectNotebook(String notebookId);
    }

    private final Activity activity;
    private final CoreAccess access;
    private final SaveStateStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Where the in-flight save should land, set before the request, read in the callback. */
    private final AtomicReference<File> pendingTarget = new AtomicReference<>();
    private final AtomicReference<String> pendingLabel = new AtomicReference<>();
    /** The notebook to pair with the in-flight save, captured at request time. */
    private final AtomicReference<String> pendingBinding = new AtomicReference<>();
    /** Whether the in-flight save is an automatic one (rotated, silent). */
    private final AtomicBoolean pendingAuto = new AtomicBoolean(false);
    private NotebookLink notebook;

    /** How many automatic saves to keep before rotating the oldest out. */
    private static final int AUTO_KEEP = 20;
    /** A readable, am/pm, colon-free label for an automatic save. */
    private static final SimpleDateFormat AUTO_STAMP = new SimpleDateFormat("MMM d h-mm a", Locale.US);

    public SaveStateController(Activity activity, CoreAccess access) {
        this.activity = activity;
        this.access = access;
        this.store = new SaveStateStore(new File(activity.getFilesDir(), "savestates"));
    }

    /** Wire the companion's notebook so saves and loads can carry it. */
    public void setNotebookLink(NotebookLink link) { this.notebook = link; }

    private String currentNotebookId() {
        NotebookLink link = notebook;
        try { return link == null ? null : link.currentNotebookId(); }
        catch (RuntimeException ignored) { return null; }
    }

    public void dispose() { io.shutdown(); }

    /** Wire this as the core's save-state listener when the session starts. */
    public void onState(byte[] state) {
        final File target = pendingTarget.getAndSet(null);
        final String label = pendingLabel.getAndSet(null);
        final String binding = pendingBinding.getAndSet(null);
        final boolean auto = pendingAuto.getAndSet(false);
        if (state == null) { if (!auto) toast("The machine could not be captured."); return; }
        final byte[] bytes = state;   // a Java array; safe to keep past the native call
        io.execute(() -> {
            String said;
            try {
                File written;
                if (auto) written = store.writeAuto(label, bytes, AUTO_KEEP);
                else written = (target != null) ? writeTo(target, bytes) : store.write(label, bytes);
                store.writeBinding(written, binding);
                said = auto ? null : "Saved " + SaveStateStore.label(written);
            } catch (IOException | RuntimeException failure) {
                said = auto ? null : "Could not save: " + failure.getMessage();
            }
            final String message = said;   // auto-saves are silent
            if (message != null) main.post(() -> toast(message));
        });
    }

    /**
     * Capture an automatic, rotated save without a word to the player. A no-op
     * if the machine is not ready, so the caller can fire it on a plain timer.
     */
    public void autoSave() {
        Core core = access.current();
        if (core == null || ! core.isReady()) return;
        pendingTarget.set(null);
        pendingLabel.set(SaveStateStore.AUTO_PREFIX + AUTO_STAMP.format(new Date()));
        pendingBinding.set(currentNotebookId());
        pendingAuto.set(true);
        if (! core.requestSaveState()) { pendingAuto.set(false); pendingLabel.set(null); pendingBinding.set(null); }
    }

    private File writeTo(File target, byte[] bytes) throws IOException {
        store.write(target, bytes);
        return target;
    }

    public void quickSave() {
        Core core = access.current();
        if (notReady(core)) return;
        pendingTarget.set(store.quickFile());
        pendingLabel.set(null);
        pendingBinding.set(currentNotebookId());
        if (! core.requestSaveState()) { pendingTarget.set(null); pendingBinding.set(null); toast("The machine is not ready to save."); }
    }

    public void quickLoad() {
        File quick = store.quickFile();
        if (! quick.isFile()) { toast("There is no quick save yet."); return; }
        load(quick);
    }

    /** Ask for a name, then capture. */
    public void saveNamed() {
        Core core = access.current();
        if (notReady(core)) return;
        final EditText field = new EditText(activity);
        field.setHint("Save name");
        new AlertDialog.Builder(activity)
                .setTitle("New save state")
                .setView(field)
                .setPositiveButton("Save", (d, w) -> {
                    String label = field.getText().toString().trim();
                    pendingTarget.set(null);
                    pendingLabel.set(label.isEmpty() ? "Save state" : label);
                    pendingBinding.set(currentNotebookId());
                    if (! core.requestSaveState()) {
                        pendingLabel.set(null);
                        pendingBinding.set(null);
                        toast("The machine is not ready to save.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** List saved states to load or delete. */
    public void chooseSave() {
        List<File> saves = store.saves();
        if (saves.isEmpty()) { toast("No save states yet. Use Quick save or New save state."); return; }
        String[] labels = new String[saves.size()];
        for (int i = 0; i < saves.size(); i++) labels[i] = SaveStateStore.label(saves.get(i));
        new AlertDialog.Builder(activity)
                .setTitle("Load a save state")
                .setItems(labels, (d, which) -> confirmLoad(saves.get(which)))
                .setNeutralButton("Delete…", (d, w) -> chooseDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmLoad(File file) {
        new AlertDialog.Builder(activity)
                .setTitle("Load " + SaveStateStore.label(file) + "?")
                .setMessage("This replaces the machine you are running now with the saved one. "
                        + "Anything since your last save is lost.")
                .setPositiveButton("Load", (d, w) -> load(file))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseDelete() {
        List<File> saves = store.saves();
        if (saves.isEmpty()) return;
        String[] labels = new String[saves.size()];
        for (int i = 0; i < saves.size(); i++) labels[i] = SaveStateStore.label(saves.get(i));
        new AlertDialog.Builder(activity)
                .setTitle("Delete which save state?")
                .setItems(labels, (d, which) -> {
                    File file = saves.get(which);
                    if (store.delete(file)) toast("Deleted " + SaveStateStore.label(file));
                    else toast("Could not delete " + SaveStateStore.label(file));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void load(File file) {
        Core core = access.current();
        if (notReady(core)) return;
        io.execute(() -> {
            String said;
            try {
                byte[] bytes = store.read(file);
                final String boundNotebook = store.readBinding(file);
                main.post(() -> {
                    if (core.restoreState(bytes)) {
                        NotebookLink link = notebook;
                        if (link != null && boundNotebook != null) link.selectNotebook(boundNotebook);
                        toast("Loaded " + SaveStateStore.label(file));
                    } else {
                        toast("The machine would not accept that save.");
                    }
                });
                return;
            } catch (IOException | RuntimeException failure) {
                said = "Could not load: " + failure.getMessage();
            }
            final String message = said;
            main.post(() -> toast(message));
        });
    }

    private boolean notReady(Core core) {
        if (core == null || ! core.isReady()) { toast("The emulator is not running."); return true; }
        return false;
    }

    private void toast(String message) {
        if (! activity.isFinishing() && message != null)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
