package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import name.osher.gil.minivmac.desktop.DesktopAppearanceStore;
import name.osher.gil.minivmac.desktop.DesktopDisk;
import name.osher.gil.minivmac.desktop.DiskAccessGate;
import name.osher.gil.minivmac.personal.PersonalPackage;

/** Preview-only while playing; real System desktop settings change only offline. */
public final class DesktopAppearanceDialog {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final String SHUTDOWN = "Save and quit the game, then use the Mac's Special > Shut Down. "
            + "Wait for Restart Emulator, then reopen this panel. Do not use Force Power Off.";
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DesktopAppearanceStore store;
    private final File disksRoot;
    private File[] disks;
    private int diskIndex;
    private DesktopDisk.Style style = DesktopDisk.Style.WHITE;
    private boolean restoring, busy, closed;
    private DesktopAppearanceStore.Snapshot snapshot;
    private AlertDialog dialog;
    private TextView status;
    private Button diskButton, inspectButton, applyButton, restoreButton;
    private final Button[] styles = new Button[3];
    private PatternPreview preview;

    private DesktopAppearanceDialog(Activity activity) throws IOException {
        this.activity = activity;
        // Normalize only Android's trusted anchor, never a selected disk leaf.
        File files = activity.getFilesDir().getCanonicalFile();
        disksRoot = new File(PersonalPackage.dataRoot(files), "disks");
        if (!disksRoot.equals(disksRoot.getCanonicalFile())
                || !disksRoot.equals(FileManager.getInstance().getDisksDir().getCanonicalFile()))
            throw new IOException("The active disk folder changed; reopen the app before changing the desktop.");
        store = new DesktopAppearanceStore(disksRoot, new File(files, "desktop-appearance"));
    }

    public static void show(Activity activity) {
        try { new DesktopAppearanceDialog(activity).open(); }
        catch (IOException unavailable) {
            TextView message = new TextView(activity);
            message.setText(unavailable.getMessage()); message.setTextColor(Color.BLACK);
            UpperHalfReferenceDialog.show(activity, "Desktop appearance unavailable", message);
        }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextSize(size); text.setTextColor(Color.BLACK);
        text.setPadding(0, dp(4), 0, dp(4));
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener action) {
        Button button = new Button(activity);
        button.setText(label); button.setTextColor(Color.BLACK); button.setTextSize(14);
        button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
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
        text(content, "A quiet background for the real Mac desktop. Game windows and saves stay unchanged.", 14);
        diskButton = button(content, "", v -> chooseDisk());
        LinearLayout row = new LinearLayout(activity);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        String[] labels = {"White", "Mist", "Stonework"};
        for (int i = 0; i < styles.length; i++) {
            final DesktopDisk.Style choice = DesktopDisk.Style.values()[i];
            styles[i] = button(row, labels[i], v -> { style = choice; restoring = false; refresh(); });
        }
        restoreButton = button(row, "Original", v -> { restoring = true; refresh(); });
        preview = new PatternPreview();
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(80)));
        status = text(content, "", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        inspectButton = button(content, "Check stopped disk", v -> inspect());
        applyButton = button(content, "Apply White desktop", v -> apply());
        text(content, "Supported: this project's System 7.5.5 boot disk. Unsupported or unclean disks are left alone. "
                + "Original restores only the first saved desktop settings, not an old disk or campaign.", 12);
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.WHITE); scroll.addView(content);
        dialog = UpperHalfReferenceDialog.show(activity, "Desktop appearance", scroll, () -> closed = true);
        refresh();
        if (disks.length == 0) status.setText("Import your boot disk first.");
        else if (DiskAccessGate.GLOBAL.isEmulationActive()) status.setText(SHUTDOWN);
        else inspect();
    }

    private String label() { return restoring ? "Original" : new String[]{"White", "Mist", "Stonework"}[style.ordinal()]; }

    private void refresh() {
        boolean hasDisk = disks.length != 0;
        diskButton.setText(hasDisk ? "Disk: " + disks[diskIndex].getName() : "No imported disks");
        diskButton.setEnabled(!busy && disks.length > 1);
        for (int i = 0; i < styles.length; i++) {
            boolean selected = !restoring && style.ordinal() == i;
            styles[i].setEnabled(!busy);
            styles[i].setTextColor(selected ? Color.WHITE : Color.BLACK);
            styles[i].setBackgroundTintList(ColorStateList.valueOf(selected ? Color.BLACK : 0xffeeeeee));
            styles[i].setSelected(selected);
        }
        restoreButton.setEnabled(!busy && snapshot != null && snapshot.originalPpat != null);
        restoreButton.setTextColor(restoring ? Color.WHITE : Color.BLACK);
        restoreButton.setBackgroundTintList(ColorStateList.valueOf(restoring ? Color.BLACK : 0xffeeeeee));
        boolean stopped = !DiskAccessGate.GLOBAL.isEmulationActive() && !DiskAccessGate.GLOBAL.isMaintenanceBusy();
        inspectButton.setEnabled(!busy && hasDisk && stopped);
        applyButton.setEnabled(!busy && snapshot != null && stopped);
        applyButton.setText(restoring ? "Restore original desktop" : "Apply " + label() + " desktop");
        dialog.setCancelable(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        preview.setContentDescription(label() + " desktop pattern preview; no disk change until Apply");
        preview.invalidate();
    }

    private void chooseDisk() {
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] picker = new AlertDialog[1];
        for (int i = 0; i < disks.length; i++) {
            final int selected = i;
            button(choices, disks[i].getName(), v -> {
                diskIndex = selected; snapshot = null; restoring = false;
                picker[0].dismiss(); refresh(); inspect();
            });
        }
        ScrollView scroll = new ScrollView(activity); scroll.addView(choices);
        picker[0] = UpperHalfReferenceDialog.show(activity, "Choose boot disk", scroll);
    }

    private void inspect() {
        if (busy || disks.length == 0) return;
        if (DiskAccessGate.GLOBAL.isEmulationActive()) { status.setText(SHUTDOWN); refresh(); return; }
        run(false);
    }

    private void apply() {
        if (busy || snapshot == null) return;
        run(true);
    }

    private void run(boolean apply) {
        final File disk = disks[diskIndex];
        final boolean restore = restoring;
        final DesktopDisk.Style selected = style;
        busy = true;
        status.setText(apply ? "Saving desktop settings… keep the guest shut down." : "Checking the stopped boot disk…");
        refresh();
        WORKER.execute(() -> {
            DesktopAppearanceStore.Snapshot next = null;
            String error = null;
            try {
                if (apply) {
                    next = restore ? store.restore(disk) : store.apply(disk, selected);
                } else next = store.inspect(disk);
            } catch (IOException | RuntimeException failure) {
                error = failure.getMessage() == null ? "Desktop settings are unavailable; no safe update could be completed." : failure.getMessage();
            }
            final DesktopAppearanceStore.Snapshot result = next;
            final String message = error;
            main.post(() -> {
                if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                busy = false; snapshot = result;
                if (message != null) status.setText(message + "\n" + SHUTDOWN);
                else status.setText(apply
                        ? "Desktop settings saved. Close this panel and tap Restart Emulator. Later saves are preserved."
                        : "Ready: " + result.disk.volumeName() + ". Preview a style, then Apply."
                            + (result.originalPpat == null ? " Original settings will be saved first." : " Original settings are available to restore."));
                refresh();
            });
        });
    }

    private final class PatternPreview extends View {
        private final Paint paint = new Paint();
        PatternPreview() { super(activity); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            byte[] bits = restoring && snapshot != null && snapshot.originalPat != null
                    ? snapshot.originalPat : DesktopDisk.patternBits(style);
            byte[] color = restoring && snapshot != null ? snapshot.originalPpat : null;
            int scale = dp(2);
            for (int y = 0; y < getHeight(); y += scale) {
                for (int x = 0; x < getWidth(); x += scale) {
                    int row = (y / scale) % 8, col = (x / scale) % 8;
                    int pixel = (bits[row] >> (7 - col)) & 1;
                    int rgb = pixel == 0 ? Color.WHITE : Color.BLACK;
                    if (color != null) {
                        // The inspected fixed profile uses 4-bit indices and its embedded CLUT.
                        int packed = color[78 + row * 4 + col / 2] & 255;
                        int index = col % 2 == 0 ? packed >> 4 : packed & 15;
                        int offset = 120 + index * 8;
                        if (offset + 4 < color.length)
                            rgb = Color.rgb(color[offset] & 255, color[offset + 2] & 255, color[offset + 4] & 255);
                    }
                    paint.setColor(rgb);
                    canvas.drawRect(x, y, x + scale, y + scale, paint);
                }
            }
            paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            canvas.drawRect(0, 0, getWidth() - 1, getHeight() - 1, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
