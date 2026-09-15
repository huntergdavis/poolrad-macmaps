package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.preference.PreferenceManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.NoteIcon;
import name.osher.gil.minivmac.journal.JournalBook;
import name.osher.gil.minivmac.journal.JournalCitation;
import name.osher.gil.minivmac.journal.JournalHistory;
import name.osher.gil.minivmac.notebook.NotebookStore;
import name.osher.gil.minivmac.notebook.NotebookSelection;
import name.osher.gil.minivmac.notebook.ExplorationRecorder;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/** User-owned notes only. This class has no reference to the emulator Core. */
public final class NotebookController implements LiveMapView.Listener, JournalController.Notebooks {
    private static final String ACTIVE = "poolrad_notebook_id";
    private static final String VISITED_ONLY = "poolrad_visited_only";
    private static final String FOOTPRINTS = "poolrad_footprints";
    // One ordered queue also lets an old Activity finish its saves before a new one reads them.
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final LiveMapView map;
    private final NotebookStore store;
    private final ExplorationRecorder exploration;
    private final SharedPreferences prefs;
    private final NotebookTransferController transfers;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookStore.Notebook notebook;
    private AreaIdentity area;
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private boolean disposed, opening, flagsReady;
    private int generation;
    private Session session;
    private AlertDialog picker;
    private boolean explorationInterrupted = true, explorationFailed;
    private JournalHistory journal;

    public NotebookController(Activity activity, LiveMapView map) {
        this.activity = activity; this.map = map;
        transfers = ((MiniVMac) activity).notebookTransfers();
        store = new NotebookStore(new File(activity.getFilesDir(), "notebooks"));
        exploration = new ExplorationRecorder(store);
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        map.setExplorationStyle(prefs.getBoolean(VISITED_ONLY, false), prefs.getBoolean(FOOTPRINTS, true));
        map.setListener(this);
        ((MiniVMac) activity).journal().setNotebooks(this);
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
        exploration.forget();
        final JournalHistory loadedJournal = loadOrMigrateJournal(selected.id());
        main.post(() -> {
            if (disposed) return;
            notebook = selected; journal = loadedJournal; opening = false; refreshFlags();
            explorationInterrupted = true; explorationFailed = false;
            map.showExploration(ExplorationTrail.empty(), "Loading trail");
            onExplorationAreaChanged(map.displayedArea());
            onExplorationSample(map.snapshot());
        });
    }

    /**
     * 0.14.0 kept journal lookups and bookmarks in app preferences, so a notebook
     * backup silently lost them. Carry an existing preference history into the
     * notebook exactly once, and only forget the old keys after it is safely stored.
     */
    private JournalHistory loadOrMigrateJournal(String id) {
        try {
            JournalHistory stored = store.loadJournal(id);
            if (!stored.isEmpty()) return stored;
            String key = prefs.contains("journal." + id + ".recent") ? "journal." + id
                    : prefs.contains("journal.unassigned.recent") ? "journal.unassigned" : null;
            if (key == null) return stored;
            JournalHistory legacy = new JournalHistory(
                    prefs.getString(key + ".recent", ""), prefs.getString(key + ".stars", ""));
            store.saveJournal(id, legacy);
            prefs.edit().remove(key + ".recent").remove(key + ".stars").apply();
            return legacy;
        } catch (IOException | RuntimeException failure) {
            report("Journal history unavailable; nothing was replaced", failure);
            return null;
        }
    }

    /** The selected notebook's display label, or null while none is open. */
    public String notebookLabel() { return notebook == null ? null : notebook.label(); }

    @Override public JournalHistory journalHistory() { return journal; }
    @Override public String areaId() { return area == null ? null : area.id(); }
    @Override public String areaLabel() { return area == null ? null : area.label(); }
    @Override public Map<Integer, NoteIcon> flags() { return flagsReady ? flags : Collections.emptyMap(); }

    /** Opens the flag's own handwritten page, reusing the ordinary tap path and its guards. */
    @Override public void openFlagPage(String targetArea, int x, int y) {
        if (disposed || area == null || !area.id().equals(targetArea)) {
            toast("That flag is on another area map. Return there to open its page."); return;
        }
        onTileTapped(area, x, y);
    }

    /** Encodes on this thread: a live history must never be walked while the UI edits it. */
    @Override public void persistJournal() {
        if (disposed || notebook == null || journal == null) return;
        final String id = notebook.id();
        final byte[] payload;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { journal.write(out); }
            payload = bytes.toByteArray();
        } catch (IOException | RuntimeException failure) {
            report("Journal history could not be prepared", failure); return;
        }
        IO.execute(() -> {
            try { store.saveJournal(id, payload); }
            catch (IOException | RuntimeException failure) { report("Journal history could not be saved", failure); }
        });
    }

    /**
     * The running game has printed something. Any reference it cites in its own
     * words joins this notebook's encountered list, once. Nothing is guessed:
     * see {@link name.osher.gil.minivmac.journal.JournalCitation}, which matches
     * only wordings observed in the game.
     */
    public void onGameMessage(byte[] sample) {
        if (disposed || journal == null) return;
        java.util.Set<JournalBook.Key> cited =
                JournalCitation.read(name.osher.gil.minivmac.journal.GameMessage.parse(sample));
        if (cited.isEmpty()) return;
        List<String> added = new ArrayList<>();
        for (JournalBook.Key key : cited) if (journal.encounter(key)) added.add(key.label());
        if (added.isEmpty()) return;
        persistJournal();
        toast(added.size() == 1
                ? added.get(0) + " noted in this notebook's journal list"
                : added.size() + " references noted in this notebook's journal list");
    }

    @Override public void onAreaChanged(AreaIdentity next) { area = next; refreshFlags(); }

    @Override public void onExplorationSample(PoolRadState sample) {
        if (disposed) return;
        // A normal step clears the input-wait tag while it updates the map.
        // Do not record that transient position or mistake it for a reload.
        // The next settled sample still needs the same epoch and a short gap.
        if (sample != null && sample.explorationProcessing) return;
        if (notebook == null || sample == null || sample.area == null || !sample.explorationSafe) {
            if (!explorationInterrupted) IO.execute(exploration::interrupt);
            explorationInterrupted = true;
            return;
        }
        explorationInterrupted = false;
        final NotebookStore.Notebook book = notebook;
        final String key = sample.area.id();
        final long time = SystemClock.elapsedRealtime();
        IO.execute(() -> {
            try {
                ExplorationTrail recorded = exploration.observe(book.id(), key,
                        sample.y * 16 + sample.x, sample.continuityToken, time);
                main.post(() -> {
                    if (!explorationTarget(book, key)) return;
                    explorationFailed = false; map.showExploration(recorded, "");
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> {
                    if (!explorationTarget(book, key)) return;
                    map.showExploration(ExplorationTrail.empty(), "Trail unavailable · retry in Info");
                    if (!explorationFailed) {
                        explorationFailed = true;
                        report("Cannot save exploration trail", failure);
                    }
                });
            }
        });
    }

    private boolean explorationTarget(NotebookStore.Notebook book, String key) {
        AreaIdentity shown = map.displayedArea();
        return !disposed && notebook == book && shown != null && key.equals(shown.id());
    }

    @Override public void onExplorationAreaChanged(AreaIdentity target) {
        if (disposed || notebook == null || target == null) return;
        final NotebookStore.Notebook book = notebook;
        IO.execute(() -> {
            try {
                ExplorationTrail saved = exploration.read(book.id(), target.id());
                main.post(() -> {
                    if (explorationTarget(book, target.id())) map.showExploration(saved, "");
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> {
                    if (explorationTarget(book, target.id()))
                        map.showExploration(ExplorationTrail.empty(), "Trail unavailable · retry in Info");
                });
            }
        });
    }

    public void showExploration() {
        if (disposed || opening || session != null || (picker != null && picker.isShowing())) return;
        final NotebookStore.Notebook book = notebook;
        final AreaIdentity target = map.displayedArea();
        if (book == null || target == null) { toast("Load a supported area map first."); return; }
        opening = true;
        IO.execute(() -> {
            try {
                ExplorationTrail trail = exploration.read(book.id(), target.id());
                main.post(() -> {
                    opening = false;
                    if (disposed || notebook != book) return;
                    showExplorationOptions(book, target, trail);
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> opening = false); report("Cannot read exploration trail", failure);
            }
        });
    }

    private void showExplorationOptions(NotebookStore.Notebook book, AreaIdentity target, ExplorationTrail trail) {
        LinearLayout list = column();
        list.addView(text(book.label() + " · " + target.label() + "\n" + trail.visitedCount() + " of 256 squares walked"));
        list.addView(text("Observed visits, not line of sight. Footprints point in the direction travelled, not where the party looked. History starts now, not retroactively. Switch notebooks when changing campaigns."));
        CheckBox fog = new CheckBox(activity); fog.setText("Show only walked squares (fog of war)");
        CheckBox feet = new CheckBox(activity); feet.setText("Show directional footprints");
        fog.setTextColor(Color.BLACK); feet.setTextColor(Color.BLACK);
        fog.setButtonTintList(ColorStateList.valueOf(Color.BLACK));
        feet.setButtonTintList(ColorStateList.valueOf(Color.BLACK));
        fog.setStateListAnimator(null); feet.setStateListAnimator(null);
        fog.setChecked(prefs.getBoolean(VISITED_ONLY, false)); feet.setChecked(prefs.getBoolean(FOOTPRINTS, true));
        fog.setMinHeight(dp(48)); feet.setMinHeight(dp(48)); list.addView(fog); list.addView(feet);
        android.widget.CompoundButton.OnCheckedChangeListener style = (view, checked) -> {
            prefs.edit().putBoolean(VISITED_ONLY, fog.isChecked()).putBoolean(FOOTPRINTS, feet.isChecked()).apply();
            map.setExplorationStyle(fog.isChecked(), feet.isChecked());
        };
        fog.setOnCheckedChangeListener(style); feet.setOnCheckedChangeListener(style);
        button(list, "Clear footprints only…").setOnClickListener(v -> {
            picker.dismiss(); confirmClearExploration(book, target, false);
        });
        button(list, "Reset walked map…").setOnClickListener(v -> {
            picker.dismiss(); confirmClearExploration(book, target, true);
        });
        list.addView(text("Recent observed route — newest first (up to 256 observations). Use the return directions to retrace it. Breaks are not connected; older visits remain shaded. This is a snapshot, not turn-by-turn navigation."));
        list.addView(text(ExplorationSummary.describe(trail)));
        ScrollView scroll = new ScrollView(activity); scroll.setSmoothScrollingEnabled(false); scroll.addView(list);
        picker = UpperHalfReferenceDialog.show(activity, "Exploration trail", scroll);
    }

    private void confirmClearExploration(NotebookStore.Notebook book, AreaIdentity target, boolean coverage) {
        LinearLayout list = column();
        list.addView(text((coverage ? "Reset walked squares and footprints" : "Clear recent footprints but retain walked squares")
                + " for " + target.label() + " in " + book.label() + "?\nFlags, handwritten notes, other areas and the original game stay untouched. No undo; export your notebook first if you need a backup."));
        Button clear = button(list, coverage ? "Reset this area's walked map" : "Clear this area's footprints");
        picker = UpperHalfReferenceDialog.show(activity, "Clear exploration?", list);
        clear.setOnClickListener(v -> {
            clear.setEnabled(false); opening = true;
            IO.execute(() -> {
                try {
                    ExplorationTrail reset = exploration.clear(book.id(), target.id(), coverage);
                    main.post(() -> {
                        if (disposed) return;
                        opening = false; picker.dismiss();
                        if (explorationTarget(book, target.id())) map.showExploration(reset, "");
                        toast("Exploration cleared. Flags and handwriting were kept. Current position is recorded again when tracking resumes.");
                    });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> { opening = false; if (!disposed) clear.setEnabled(true); });
                    report("Cannot clear exploration", failure);
                }
            });
        });
    }

    @Override public void onPartyMemberTapped(PartyState.Member member) {
        if (disposed || opening || session != null || (picker != null && picker.isShowing())) return;
        opening = true;
        picker = PartyDetailsDialog.show(activity, member, () -> opening = false);
    }

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
                    list.addView(text("Back up each notebook to a .prnb file outside the app. Uninstalling removes local notes. Restore never overwrites an existing campaign."));
                    for (NotebookStore.Notebook book : books) {
                        Button select = button(list, book.label() + (notebook != null && notebook.id().equals(book.id()) ? " · active" : ""));
                        select.setOnClickListener(v -> {
                            picker.dismiss(); opening = true;
                            IO.execute(() -> { try { selectOnDisk(book); }
                                catch (IOException failure) { main.post(() -> opening = false); report("Cannot select notebook", failure); } });
                        });
                    }
                    button(list, "New notebook…").setOnClickListener(v -> { picker.dismiss(); confirmNewNotebook(); });
                    if (notebook != null) {
                        final NotebookStore.Notebook target = notebook;
                        button(list, "Back up " + target.label() + "…").setOnClickListener(v -> {
                            picker.dismiss(); transfers.exportNotebook(target);
                        });
                        button(list, "Remove " + target.label() + "…").setOnClickListener(v -> {
                            picker.dismiss(); confirmRemoveNotebook(target);
                        });
                    }
                    button(list, "Restore backup…").setOnClickListener(v -> {
                        picker.dismiss(); transfers.importNotebook();
                    });
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

    private void confirmRemoveNotebook(NotebookStore.Notebook target) {
        if (disposed || session != null || opening) return;
        LinearLayout content = column();
        content.addView(text("Remove " + target.label() + " and ALL its flags, handwritten pages and exploration history in every area? "
                + "Save a .prnb backup first. There is no undo. Other notebooks and the original game saves are untouched."));
        Button remove = button(content, "Remove " + target.label() + " and all its notes");
        picker = UpperHalfReferenceDialog.show(activity, "Remove notebook?", content);
        remove.setOnClickListener(v -> {
            if (opening || disposed || session != null) return;
            opening = true; remove.setEnabled(false); picker.dismiss();
            IO.execute(() -> {
                boolean removed = false;
                try {
                    store.deleteNotebook(target.id()); removed = true;
                    if (target.id().equals(prefs.getString(ACTIVE, "")))
                        if (!prefs.edit().remove(ACTIVE).commit()) throw new IOException("Could not clear notebook selection");
                    List<NotebookStore.Notebook> remaining = store.listNotebooks();
                    NotebookStore.Notebook selected = NotebookSelection.choose(remaining, prefs.getString(ACTIVE, ""));
                    if (selected == null) selected = store.createNotebook();
                    selectOnDisk(selected);
                    main.post(() -> toast(target.label() + " removed. Restore its .prnb backup to recover it."));
                } catch (IOException | RuntimeException failure) {
                    final boolean didRemove = removed;
                    android.util.Log.w("PoolRad.Notebook", "Notebook removal/selection failed", failure);
                    main.post(() -> {
                        if (disposed) return;
                        opening = false;
                        if (didRemove) {
                            notebook = null; refreshFlags();
                            map.showExploration(ExplorationTrail.empty(), "Notebook unavailable");
                            IO.execute(exploration::forget); explorationInterrupted = true;
                        }
                        toast(didRemove ? "Notebook removed, but replacement selection failed. Open Notebooks to choose/create one."
                                : "Notebook was not removed. Existing notes are unchanged.");
                    });
                }
            });
        });
    }

    public void dispose() {
        disposed = true; generation++; map.setListener(null);
        // A replacement controller may already have registered; never unhook theirs.
        JournalController owner = ((MiniVMac) activity).journal();
        if (owner.notebooks() == this) owner.setNotebooks(null);
        IO.execute(exploration::interrupt);
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
        Button pen, eraser, undo, redo, symbol, journal, delete, fit, close;
        AlertDialog dialog;
    }

    private void showSheet(NotebookStore.Notebook book, AreaIdentity target, int x, int y, InkNote note,
            PoolRadState pinned, Map<Integer, NoteIcon> symbols) {
        Session current = new Session(); session = current;
        current.book = book; current.area = target; current.x = x; current.y = y;
        current.snapshot = pinned; current.symbols = new HashMap<>(symbols); current.icon = symbols.get(y * 16 + x);
        NoteEditorLayout content = new NoteEditorLayout(activity, x + ", " + y + " · " + target.label());
        current.pen = content.pen; current.eraser = content.eraser;
        current.undo = content.undo; current.redo = content.redo;
        current.symbol = content.symbol; current.journal = content.journal;
        current.delete = content.delete;
        current.fit = content.fit; current.close = content.close;
        current.delete.setContentDescription("Delete flag and linked handwritten note");
        current.status = content.status; current.status.setText("Saved locally · " + book.label());
        current.sheet = content.sheet;
        current.sheet.setMap(pinned, symbols, x, y); current.sheet.setNote(note);
        current.dialog = UpperHalfReferenceDialog.showEditor(activity, content, () -> {
                    current.sheet.cancelActiveStroke();
                    if (session == current) session = null;
                });
        // Never let platform/predictive Back dismiss an unsaved sheet. The explicit
        // Close action (and key Back where delivered) finishes the save first.
        current.dialog.setCancelable(false);
        current.dialog.getOnBackPressedDispatcher().addCallback(current.dialog, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { save(current, true); }
        });
        current.close.setOnClickListener(v -> save(current, true));
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
        current.journal.setOnClickListener(v -> {
            if (disposed || current != session || current.closing || current.deleting) return;
            current.sheet.cancelActiveStroke();
            ((MiniVMac) activity).journal().showFlagLinks(
                    current.area.id(), current.area.label(), current.x, current.y);
        });
        current.delete.setOnClickListener(v -> confirmDelete(current));
        current.fit.setOnClickListener(v -> current.sheet.fitPage());
        current.sheet.setOnChangeListener(() -> { current.revision++; updateTools(current); save(current, false); });
        updateTools(current);
    }

    private void updateTools(Session current) {
        boolean enabled = !current.closing && !current.deleting;
        current.sheet.setEnabled(enabled);
        current.pen.setEnabled(enabled); current.eraser.setEnabled(enabled); current.delete.setEnabled(enabled);
        current.symbol.setEnabled(enabled);
        current.fit.setEnabled(enabled);
        current.symbol.setText(current.icon.label());
        current.symbol.setContentDescription("Change map symbol. Current: " + current.icon.label());
        int linked = journal == null ? 0
                : journal.entriesFor(new JournalHistory.Flag(current.area.id(), current.x, current.y)).size();
        current.journal.setEnabled(enabled);
        current.journal.setText(linked == 0 ? "Journal" : "Journal " + linked);
        current.journal.setContentDescription(linked == 0
                ? "Journal references linked to this flag: none. Open to link one."
                : "Journal references linked to this flag: " + linked);
        current.undo.setEnabled(enabled && current.sheet.canUndo()); current.redo.setEnabled(enabled && current.sheet.canRedo());
        current.close.setEnabled(enabled);
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
                        // A deleted flag must not leave a journal entry pointing at nothing.
                        if (journal != null && notebook == current.book
                                && journal.forgetFlag(new JournalHistory.Flag(current.area.id(), current.x, current.y))) {
                            persistJournal();
                        }
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
