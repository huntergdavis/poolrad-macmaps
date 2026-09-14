package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.NoteIcon;
import name.osher.gil.minivmac.notebook.NotebookStore;
import name.osher.gil.minivmac.notebook.NotebookSelection;

/** User-owned notes only. This class has no reference to the emulator Core. */
public final class NotebookController implements LiveMapView.Listener {
    private static final String ACTIVE = "poolrad_notebook_id";
    private static final String PEN_ONLY = "poolrad_notes_pen_only";
    // One ordered queue also lets an old Activity finish its saves before a new one reads them.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final LiveMapView map;
    private final NotebookStore store;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookStore.Notebook notebook;
    private AreaIdentity area;
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private boolean disposed, opening, flagsReady;
    private int generation;
    private Session session;
    private AlertDialog picker;

    public NotebookController(Activity activity, LiveMapView map) {
        this.activity = activity; this.map = map;
        store = new NotebookStore(new File(activity.getFilesDir(), "notebooks"));
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        map.setListener(this);
        IO.execute(() -> {
            try {
                List<NotebookStore.Notebook> books = store.listNotebooks();
                String wanted = prefs.getString(ACTIVE, "");
                NotebookStore.Notebook selected = NotebookSelection.choose(books, wanted);
                if (selected == null) selected = store.createNotebook();
                selectOnDisk(selected);
            } catch (IOException | RuntimeException failure) {
                main.post(() -> { if (!disposed) map.showNotebook("Notebook unavailable · choose Notebooks", Collections.emptyMap()); });
                report("Cannot open selected notebook; choose Notebooks", failure);
            }
        });
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(12), dp(4), dp(12), dp(4)); return view;
    }
    private TextView text(String value) {
        TextView view = new TextView(activity); view.setTextColor(Color.BLACK); view.setText(value); return view;
    }
    private Button button(LinearLayout parent, String label) {
        Button view = new Button(activity); view.setText(label); view.setAllCaps(false);
        view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void toast(String message) { Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show(); }
    private void report(String message, Exception failure) {
        android.util.Log.w("PoolRad.Notebook", message, failure);
        main.post(() -> { if (!disposed) toast(message + ". Existing notes were not cleared. Try again."); });
    }

    private void selectOnDisk(NotebookStore.Notebook selected) throws IOException {
        if (!prefs.edit().putString(ACTIVE, selected.id()).commit()) throw new IOException("Notebook selection could not be saved");
        main.post(() -> {
            if (disposed) return;
            notebook = selected; opening = false; refreshFlags();
        });
    }

    @Override public void onAreaChanged(AreaIdentity next) { area = next; refreshFlags(); }

    private void refreshFlags() {
        int request = ++generation; flagsReady = false; flags = Collections.emptyMap();
        String label = notebook == null ? "Notebook unavailable" : notebook.label();
        map.showNotebook(label + (area == null ? " · notes unavailable for this area" : " · loading notes…"), flags);
        if (notebook == null || area == null) return;
        final String run = notebook.id(), key = area.id();
        IO.execute(() -> {
            try {
                Map<Integer, NoteIcon> loaded = store.listFlagIcons(run, key);
                main.post(() -> {
                    if (disposed || request != generation) return;
                    flags = loaded; flagsReady = true; map.showNotebook(notebook.label(), loaded);
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> {
                    if (!disposed && request == generation)
                        map.showNotebook(notebook.label() + " · notes unavailable", Collections.emptyMap());
                });
                report("Cannot read flag notes", failure);
            }
        });
    }

    @Override public void onTileTapped(AreaIdentity target, int x, int y) {
        if (target == null) { toast("Notes need an identified area. Unknown or changed geometry is never assigned to a notebook."); return; }
        if (notebook == null || !flagsReady || opening || session != null) { toast("Notebook is not ready. Please try again."); return; }
        final PoolRadState pinned = map.snapshot();
        if (pinned == null || pinned.area == null || !target.id().equals(pinned.area.id())) return;
        final NotebookStore.Notebook book = notebook;
        final boolean existing = flags.containsKey(y * 16 + x);
        final Map<Integer, NoteIcon> symbols = new HashMap<>(flags);
        opening = true;
        IO.execute(() -> {
            try {
                InkNote note = store.read(book.id(), target.id(), x, y);
                if (!existing) store.save(book.id(), target.id(), x, y, note);
                symbols.put(y * 16 + x, store.readIcon(book.id(), target.id(), x, y));
                main.post(() -> {
                    opening = false;
                    if (disposed) return;
                    // The editor pins the original notebook, area and tile, even if the game moves.
                    showSheet(book, target, x, y, note, pinned, symbols); refreshFlags();
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> opening = false); report("Cannot open this note", failure);
            }
        });
    }

    @Override public void onNearbyFlagsTapped(AreaIdentity target, int x, int y, int[] candidates) {
        if (disposed || target == null || notebook == null || !flagsReady || opening || session != null) return;
        final NotebookStore.Notebook book = notebook;
        final int request = generation;
        final boolean[] selected = {false};
        opening = true;
        LinearLayout choices = column();
        choices.addView(text("A symbol is close to your tap. Open its page, or create a flag on the tile you tapped."));
        for (int tile : candidates) {
            NoteIcon icon = flags.get(tile);
            if (icon == null) continue;
            button(choices, icon.label() + " · tile " + tile % 16 + ", " + tile / 16)
                    .setOnClickListener(v -> chooseNearby(book, target, request, tile % 16, tile / 16, selected));
        }
        button(choices, "New flag here · tile " + x + ", " + y)
                .setOnClickListener(v -> chooseNearby(book, target, request, x, y, selected));
        ScrollView scroll = new ScrollView(activity); scroll.addView(choices);
        picker = UpperHalfReferenceDialog.show(activity, "Choose a flag", scroll, () -> {
            if (!selected[0]) opening = false;
        });
    }

    private void chooseNearby(NotebookStore.Notebook book, AreaIdentity target, int request, int x, int y,
            boolean[] selected) {
        if (selected[0]) return;
        selected[0] = true;
        opening = false;
        picker.dismiss();
        if (disposed || request != generation || notebook != book || area == null
                || !area.id().equals(target.id())) {
            toast("The map or notebook changed. Tap the flag again."); return;
        }
        onTileTapped(target, x, y);
    }

    public void chooseNotebook() {
        if (opening || session != null || disposed) return;
        opening = true;
        IO.execute(() -> {
            try {
                List<NotebookStore.Notebook> books = store.listNotebooks();
                main.post(() -> {
                    opening = false; if (disposed) return;
                    LinearLayout list = column();
                    list.addView(text("Each notebook is a separate campaign. Switching Mac saves does not switch notebooks. Old notes are kept."));
                    for (NotebookStore.Notebook book : books) {
                        Button select = button(list, book.label() + (notebook != null && notebook.id().equals(book.id()) ? " · active" : ""));
                        select.setOnClickListener(v -> {
                            picker.dismiss(); opening = true;
                            IO.execute(() -> { try { selectOnDisk(book); }
                                catch (IOException failure) { main.post(() -> opening = false); report("Cannot select notebook", failure); } });
                        });
                    }
                    button(list, "New notebook…").setOnClickListener(v -> { picker.dismiss(); confirmNewNotebook(); });
                    ScrollView scroll = new ScrollView(activity); scroll.addView(list);
                    picker = UpperHalfReferenceDialog.show(activity, "Notebooks", scroll);
                });
            } catch (IOException | RuntimeException failure) { main.post(() -> opening = false); report("Cannot list notebooks", failure); }
        });
    }

    private void confirmNewNotebook() {
        LinearLayout content = column();
        content.addView(text("Start an empty notebook for another campaign? Your previous notebooks and the original game saves stay untouched."));
        Button create = button(content, "Create separate notebook");
        picker = UpperHalfReferenceDialog.show(activity, "New notebook", content);
        create.setOnClickListener(v -> {
            picker.dismiss(); opening = true;
            IO.execute(() -> { try { selectOnDisk(store.createNotebook()); }
                catch (IOException | RuntimeException failure) { main.post(() -> opening = false); report("Cannot create notebook", failure); } });
        });
    }

    public void dispose() {
        disposed = true; generation++; map.setListener(null);
        if (session != null) { session.sheet.cancelActiveStroke(); session.dialog.dismiss(); }
        if (picker != null) picker.dismiss();
        // Completed strokes already queued for autosave are allowed to finish.
    }

    private static final class Session {
        NotebookStore.Notebook book;
        AreaIdentity area;
        PoolRadState snapshot;
        Map<Integer, NoteIcon> symbols;
        NoteIcon icon;
        int x, y, revision;
        boolean closing, deleting;
        InkSheetView sheet;
        TextView status;
        Button pen, eraser, undo, redo, symbol, delete, fit;
        CheckBox penOnly;
        AlertDialog dialog;
    }

    private void showSheet(NotebookStore.Notebook book, AreaIdentity target, int x, int y, InkNote note,
            PoolRadState pinned, Map<Integer, NoteIcon> symbols) {
        Session current = new Session(); session = current;
        current.book = book; current.area = target; current.x = x; current.y = y;
        current.snapshot = pinned; current.symbols = new HashMap<>(symbols); current.icon = symbols.get(y * 16 + x);
        LinearLayout content = column();
        LinearLayout tools = new LinearLayout(activity);
        current.pen = tool(tools, "Pen"); current.eraser = tool(tools, "Eraser");
        current.undo = tool(tools, "Undo"); current.redo = tool(tools, "Redo");
        current.symbol = tool(tools, "Symbol");
        current.delete = tool(tools, "Delete…"); current.delete.setContentDescription("Delete flag and linked handwritten note");
        HorizontalScrollView toolScroll = new HorizontalScrollView(activity);
        toolScroll.setHorizontalScrollBarEnabled(true); toolScroll.addView(tools);
        content.addView(toolScroll);
        LinearLayout inputTools = new LinearLayout(activity);
        current.penOnly = new CheckBox(activity); current.penOnly.setText("Pen only");
        current.penOnly.setTextColor(Color.BLACK);
        current.penOnly.setButtonTintList(ColorStateList.valueOf(Color.BLACK));
        current.penOnly.setMinHeight(dp(48)); current.penOnly.setFocusable(false);
        current.penOnly.setChecked(prefs.getBoolean(PEN_ONLY, false));
        inputTools.addView(current.penOnly, new LinearLayout.LayoutParams(-2, dp(48)));
        current.fit = tool(inputTools, "Fit page"); content.addView(inputTools);
        TextView hint = text("Pinch to zoom; two fingers move the page. Pen only: one finger moves, without drawing.");
        hint.setTextSize(12); content.addView(hint);
        current.status = text("Saved locally · " + book.label()); current.status.setTextSize(12);
        current.status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(current.status);
        current.sheet = new InkSheetView(activity);
        current.sheet.setMap(pinned, symbols, x, y); current.sheet.setNote(note);
        current.sheet.setPenOnly(current.penOnly.isChecked());
        content.addView(current.sheet, new LinearLayout.LayoutParams(-1, 0, 1));
        current.dialog = UpperHalfReferenceDialog.show(activity,
                target.label() + " · tile " + x + ", " + y, content, () -> {
                    current.sheet.cancelActiveStroke();
                    if (session == current) session = null;
                });
        current.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Close & save");
        // Never let platform/predictive Back dismiss an unsaved sheet. The explicit
        // Close action (and key Back where delivered) finishes the save first.
        current.dialog.setCancelable(false);
        current.dialog.getOnBackPressedDispatcher().addCallback(current.dialog, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { save(current, true); }
        });
        current.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> save(current, true));
        current.dialog.setOnKeyListener((dialog, code, event) -> {
            if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) save(current, true);
            return true;
        });
        current.pen.setOnClickListener(v -> {
            current.sheet.cancelActiveStroke(); current.sheet.setEraser(false);
            current.pen.setText("Pen ✓"); current.eraser.setText("Eraser");
        });
        current.eraser.setOnClickListener(v -> {
            current.sheet.cancelActiveStroke(); current.sheet.setEraser(true);
            current.pen.setText("Pen"); current.eraser.setText("Eraser ✓");
        });
        current.pen.setText("Pen ✓");
        current.undo.setOnClickListener(v -> current.sheet.undo());
        current.redo.setOnClickListener(v -> current.sheet.redo());
        current.symbol.setOnClickListener(v -> chooseSymbol(current));
        current.delete.setOnClickListener(v -> confirmDelete(current));
        current.penOnly.setOnCheckedChangeListener((button, checked) -> {
            current.sheet.setPenOnly(checked);
            prefs.edit().putBoolean(PEN_ONLY, checked).apply();
        });
        current.fit.setOnClickListener(v -> current.sheet.fitPage());
        current.sheet.setOnChangeListener(() -> { current.revision++; updateTools(current); save(current, false); });
        updateTools(current);
    }

    private Button tool(LinearLayout row, String name) {
        Button button = new Button(activity); button.setText(name); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setTextSize(12);
        button.setPadding(dp(3), 0, dp(3), 0);
        row.addView(button, new LinearLayout.LayoutParams(dp(72), dp(48))); return button;
    }

    private void updateTools(Session current) {
        boolean enabled = !current.closing && !current.deleting;
        current.sheet.setEnabled(enabled);
        current.pen.setEnabled(enabled); current.eraser.setEnabled(enabled); current.delete.setEnabled(enabled);
        current.symbol.setEnabled(enabled);
        current.fit.setEnabled(enabled); current.penOnly.setEnabled(enabled);
        current.symbol.setText(current.icon.label());
        current.symbol.setContentDescription("Change map symbol. Current: " + current.icon.label());
        current.undo.setEnabled(enabled && current.sheet.canUndo()); current.redo.setEnabled(enabled && current.sheet.canRedo());
        current.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
    }

    private void save(Session current, boolean close) {
        if (disposed || current != session || current.closing || current.deleting) return;
        if (close) { current.sheet.cancelActiveStroke(); current.closing = true; updateTools(current); }
        InkNote snapshot = current.sheet.getNote();
        final NoteIcon symbol = current.icon;
        final int revision = current.revision;
        current.status.setText("Saving locally…");
        IO.execute(() -> {
            try {
                store.save(current.book.id(), current.area.id(), current.x, current.y, snapshot, symbol);
                main.post(() -> {
                    if (disposed || current != session) return;
                    if (!close && (current.closing || current.deleting)) return;
                    if (revision == current.revision) current.status.setText("Saved locally · " + current.book.label());
                    if (close) { current.dialog.dismiss(); refreshFlags(); }
                });
            } catch (IOException | RuntimeException failure) {
                android.util.Log.w("PoolRad.Notebook", "Note save failed", failure);
                main.post(() -> {
                    if (disposed || current != session) return;
                    if (!close && (current.closing || current.deleting)) return;
                    if (revision != current.revision && !close) return;
                    current.closing = false; updateTools(current);
                    current.status.setText("Save failed. Your ink is still here; retry Close & save.");
                    toast("Note was not saved. Keep this sheet open and retry.");
                });
            }
        });
    }

    private void chooseSymbol(Session current) {
        if (disposed || current != session || current.closing || current.deleting) return;
        current.sheet.cancelActiveStroke();
        LinearLayout options = column();
        options.addView(text("Your own map labels—not automatically discovered places. Handwriting stays with this note."));
        for (NoteIcon icon : NoteIcon.values()) {
            Button select = button(options, icon.label() + (icon == current.icon ? " · selected" : ""));
            select.setOnClickListener(v -> {
                picker.dismiss();
                if (disposed || current != session || current.closing || current.deleting) return;
                current.icon = icon; current.symbols.put(current.y * 16 + current.x, icon);
                current.sheet.setMap(current.snapshot, current.symbols, current.x, current.y);
                current.revision++; updateTools(current); save(current, false);
            });
        }
        ScrollView scroll = new ScrollView(activity); scroll.addView(options);
        picker = UpperHalfReferenceDialog.show(activity, "Choose map symbol", scroll);
    }

    private void confirmDelete(Session current) {
        if (current.closing || current.deleting) return;
        current.sheet.cancelActiveStroke(); current.deleting = true; updateTools(current);
        LinearLayout content = column();
        content.addView(text("Delete the flag AND its handwritten note at " + current.x + ", " + current.y
                + " in " + current.area.label() + " (" + current.book.label() + ")? This cannot be undone."));
        Button remove = button(content, "Delete flag and note");
        final boolean[] confirmed = {false};
        picker = UpperHalfReferenceDialog.show(activity, "Delete linked note?", content, () -> {
            if (!confirmed[0] && session == current) { current.deleting = false; updateTools(current); }
        });
        remove.setOnClickListener(v -> {
            confirmed[0] = true; picker.dismiss(); current.status.setText("Deleting linked flag and note…");
            IO.execute(() -> {
                try {
                    store.delete(current.book.id(), current.area.id(), current.x, current.y);
                    main.post(() -> {
                        if (disposed || session != current) return;
                        current.dialog.dismiss(); refreshFlags(); toast("Flag and linked note deleted; this cannot be undone.");
                    });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> {
                        if (disposed || session != current) return;
                        current.deleting = false; updateTools(current); current.status.setText("Delete failed; note was not discarded.");
                    });
                    report("Could not delete linked note", failure);
                }
            });
        });
    }
}
