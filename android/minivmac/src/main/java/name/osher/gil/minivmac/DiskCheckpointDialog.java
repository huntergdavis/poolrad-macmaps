package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import name.osher.gil.minivmac.checkpoint.DiskCheckpointStore;
import name.osher.gil.minivmac.desktop.DiskAccessGate;
import name.osher.gil.minivmac.personal.PersonalPackage;

/**
 * Explicit copies of a writable disk, taken only while the Mac is shut down.
 * A checkpoint is a plain file copy, never an emulator save state, so it can
 * neither capture a running game nor resume one mid-turn.
 */
public final class DiskCheckpointDialog {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final String SHUTDOWN = "Save and quit the game, then use the Mac's Special > Shut Down. "
            + "Wait for Restart Emulator, then reopen this panel. Do not use Force Power Off.";
    /** Supplied by the map so a checkpoint records where the companion last was. */
    public interface Context {
        String areaLabel();
        String notebookLabel();
        byte[] thumbnailPng();
    }

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DiskCheckpointStore store;
    private final Context context;
    private final File disksRoot;
    private File[] disks;
    private int diskIndex;
    private boolean busy, closed;
    private List<DiskCheckpointStore.Checkpoint> checkpoints;
    private AlertDialog dialog, picker;
    private TextView status;
    private LinearLayout list;
    private Button diskButton, saveButton;

    private DiskCheckpointDialog(Activity activity, Context context) throws IOException {
        this.activity = activity; this.context = context;
        File files = activity.getFilesDir().getCanonicalFile();
        disksRoot = new File(PersonalPackage.dataRoot(files), "disks");
        if (!disksRoot.equals(disksRoot.getCanonicalFile())
                || !disksRoot.equals(FileManager.getInstance().getDisksDir().getCanonicalFile()))
            throw new IOException("The active disk folder changed; reopen the app before saving a checkpoint.");
        store = new DiskCheckpointStore(new File(files, "checkpoints"), DiskAccessGate.GLOBAL);
    }

    public static void show(Activity activity, Context context) {
        try { new DiskCheckpointDialog(activity, context).open(); }
        catch (IOException unavailable) {
            TextView message = new TextView(activity);
            message.setText(unavailable.getMessage()); message.setTextColor(Color.BLACK);
            UpperHalfReferenceDialog.show(activity, "Save checkpoints unavailable", message);
        }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextSize(size); text.setTextColor(Color.BLACK);
        text.setPadding(0, dp(3), 0, dp(3));
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener action) {
        Button button = new Button(activity);
        button.setText(label); button.setTextColor(Color.BLACK); button.setTextSize(14);
        button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        button.setBackgroundTintList(ColorStateList.valueOf(0xffeeeeee));
        button.setOnClickListener(action);
        parent.addView(button, parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void open() {
        File[] available = FileManager.getInstance().getAvailableDisks();
        disks = available == null ? new File[0] : available;
        for (int i = 0; i < disks.length; i++) disks[i] = new File(disksRoot, disks[i].getName());
        Arrays.sort(disks, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(12), dp(4), dp(12), dp(8));
        text(content, "A checkpoint is a complete copy of one writable disk, taken while the Mac is shut down. "
                + "It is not an emulator save state: it cannot capture a running game or resume one mid-turn.", 13);
        diskButton = button(content, "", v -> chooseDisk());
        saveButton = button(content, "Save a checkpoint", v -> save());
        status = text(content, "", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        text(content, "Restoring replaces the whole disk, including every save inside it, and first keeps a safety "
                + "copy of the disk as it stands. Checkpoints live in this app's private storage and are removed "
                + "if you uninstall or clear its data. They never touch your original supplied files.", 12);

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.WHITE); scroll.addView(content);
        dialog = UpperHalfReferenceDialog.show(activity, "Save checkpoints", scroll, () -> closed = true);
        reload();
    }

    private boolean stopped() {
        return !DiskAccessGate.GLOBAL.isEmulationActive() && !DiskAccessGate.GLOBAL.isMaintenanceBusy();
    }

    private void refresh() {
        boolean hasDisk = disks.length != 0;
        diskButton.setText(hasDisk ? "Disk: " + disks[diskIndex].getName() : "No imported disks");
        diskButton.setEnabled(!busy && disks.length > 1);
        saveButton.setEnabled(!busy && hasDisk && stopped() && checkpoints != null);
        dialog.setCancelable(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        renderList();
    }

    private void renderList() {
        list.removeAllViews();
        if (checkpoints == null) return;
        if (checkpoints.isEmpty()) { text(list, "No checkpoints yet.", 14); return; }
        DateFormat when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        text(list, checkpoints.size() + " of " + DiskCheckpointStore.MAX_CHECKPOINTS + " saved", 13);
        for (final DiskCheckpointStore.Checkpoint checkpoint : checkpoints) {
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            text(row, when.format(new Date(checkpoint.createdAt)) + " · " + checkpoint.diskName
                    + " · " + (checkpoint.diskBytes / (1024 * 1024)) + " MB · " + checkpoint.shortDigest(), 13);
            text(row, "Last shown area: " + checkpoint.areaLabel + " · " + checkpoint.notebookLabel, 13);
            byte[] png = checkpoint.thumbnail();
            if (png != null) {
                Bitmap image = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (image != null) {
                    ImageView view = new ImageView(activity);
                    view.setImageBitmap(image); view.setScaleType(ImageView.ScaleType.FIT_START);
                    view.setContentDescription("Map as last shown when this checkpoint was saved");
                    row.addView(view, new LinearLayout.LayoutParams(-1, dp(90)));
                }
            }
            LinearLayout actions = new LinearLayout(activity);
            row.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            Button restore = button(actions, "Restore…", v -> confirmRestore(checkpoint));
            Button remove = button(actions, "Delete…", v -> confirmDelete(checkpoint));
            restore.setEnabled(!busy && stopped() && disks.length != 0);
            remove.setEnabled(!busy && stopped());
        }
    }

    private void chooseDisk() {
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < disks.length; i++) {
            final int selected = i;
            button(choices, disks[i].getName(), v -> {
                diskIndex = selected; if (picker != null) picker.dismiss(); refresh();
            });
        }
        ScrollView scroll = new ScrollView(activity); scroll.addView(choices);
        picker = UpperHalfReferenceDialog.show(activity, "Choose disk", scroll);
    }

    private void reload() {
        busy = true; refresh();
        WORKER.execute(() -> {
            List<DiskCheckpointStore.Checkpoint> loaded = null; String problem = null;
            try { loaded = store.list(); }
            catch (IOException | RuntimeException failure) { problem = failure.getMessage(); }
            final List<DiskCheckpointStore.Checkpoint> result = loaded; final String error = problem;
            main.post(() -> {
                busy = false;
                if (closed || activity.isFinishing()) return;
                if (result != null) checkpoints = result;
                status.setText(error != null ? "Checkpoints could not be listed: " + error
                        : disks.length == 0 ? "Import a disk first."
                        : stopped() ? "The Mac is shut down; checkpoints can be saved." : SHUTDOWN);
                refresh();
            });
        });
    }

    private void save() {
        if (busy || disks.length == 0) return;
        if (!stopped()) { status.setText(SHUTDOWN); refresh(); return; }
        final File disk = disks[diskIndex];
        final String area = context == null ? null : context.areaLabel();
        final String notebook = context == null ? null : context.notebookLabel();
        final byte[] thumbnail = context == null ? null : context.thumbnailPng();
        busy = true; status.setText("Copying and verifying the stopped disk… keep the Mac shut down."); refresh();
        WORKER.execute(() -> {
            String problem = null;
            try { store.create(disk, area, notebook, thumbnail); }
            catch (IOException | RuntimeException failure) { problem = failure.getMessage(); }
            finish(problem, "Checkpoint saved and verified.");
        });
    }

    private void confirmRestore(DiskCheckpointStore.Checkpoint checkpoint) {
        if (busy || disks.length == 0) return;
        final File disk = disks[diskIndex];
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(12), dp(4), dp(12), dp(8));
        text(content, "Replace ALL of " + disk.getName() + " with this checkpoint of "
                + checkpoint.diskName + "? Every save currently inside that disk is replaced.", 15);
        text(content, "The disk as it stands now is checkpointed first, so this can be undone. "
                + "The Mac must stay shut down until it finishes.", 13);
        button(content, "Replace the disk", v -> {
            if (picker != null) picker.dismiss();
            if (!stopped()) { status.setText(SHUTDOWN); refresh(); return; }
            busy = true; status.setText("Keeping a safety copy, then restoring… keep the Mac shut down."); refresh();
            WORKER.execute(() -> {
                String problem = null;
                try { store.restore(checkpoint.id, disk); }
                catch (IOException | RuntimeException failure) { problem = failure.getMessage(); }
                finish(problem, "Disk restored and verified; the previous disk was kept as a checkpoint.");
            });
        });
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        picker = UpperHalfReferenceDialog.show(activity, "Replace this disk?", scroll);
    }

    private void confirmDelete(DiskCheckpointStore.Checkpoint checkpoint) {
        if (busy) return;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(12), dp(4), dp(12), dp(8));
        text(content, "Delete the checkpoint of " + checkpoint.diskName + " taken "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(checkpoint.createdAt))
                + "? This cannot be undone. Your current disk is not changed.", 15);
        button(content, "Delete this checkpoint", v -> {
            if (picker != null) picker.dismiss();
            busy = true; status.setText("Removing the checkpoint…"); refresh();
            WORKER.execute(() -> {
                String problem = null;
                try { store.delete(checkpoint.id); }
                catch (IOException | RuntimeException failure) { problem = failure.getMessage(); }
                finish(problem, "Checkpoint deleted; the current disk is unchanged.");
            });
        });
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        picker = UpperHalfReferenceDialog.show(activity, "Delete this checkpoint?", scroll);
    }

    private void finish(String problem, String success) {
        List<DiskCheckpointStore.Checkpoint> loaded = null;
        try { loaded = store.list(); } catch (IOException | RuntimeException ignored) { }
        final List<DiskCheckpointStore.Checkpoint> result = loaded;
        main.post(() -> {
            busy = false;
            if (closed || activity.isFinishing()) return;
            if (result != null) checkpoints = result;
            status.setText(problem == null ? success : problem);
            refresh();
        });
    }
}
