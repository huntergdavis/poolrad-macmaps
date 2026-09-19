package name.osher.gil.minivmac;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.List;
import name.osher.gil.minivmac.journal.JournalBook;
import name.osher.gil.minivmac.journal.JournalHistory;

/** Local reference documents only. Never samples or writes the guest. */
public final class JournalController {
    /**
     * The active notebook owns the lookup history, bookmarks, checked tasks and
     * flag links so they travel with its backup. Everything here is player-made:
     * none of it is detected from the running game.
     */
    public interface Notebooks {
        JournalHistory journalHistory();
        void persistJournal();
        String areaId();
        String areaLabel();
        java.util.Map<Integer, name.osher.gil.minivmac.notebook.NoteIcon> flags();
        void openFlagPage(String areaId, int x, int y);
    }
    private Notebooks notebooks;
    private final AppCompatActivity activity;
    private final Context context;
    /** The journal ships with the app; there is nothing for the player to import. */
    public static final String BUNDLED_BOOK = "journal/adventurers-journal.prjr";
    private final Handler main = new Handler(Looper.getMainLooper());
    private JournalBook book;
    private boolean destroyed, busy;
    private java.util.function.Consumer<String> loadedAction;
    private AlertDialog home, picker, entry;
    private int kind;

    // Register unconditionally alongside the existing Activity-owned pickers.
    public JournalController(AppCompatActivity activity) {
        this.activity = activity; context = activity.getApplicationContext();
    }
    /** Set by the fragment-owned notebook controller; cleared when it goes away. */
    public void setNotebooks(Notebooks value) { notebooks = value; }
    public Notebooks notebooks() { return notebooks; }
    private JournalHistory history() { return notebooks == null ? null : notebooks.journalHistory(); }
    private void persist() { if (notebooks != null) notebooks.persistJournal(); }
    public void onDestroy() { destroyed = true; notebooks = null; entry = null; }
    private void toast(String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }

    public void show() { loadBook(this::openHome); }

    /** Open only the exact references supplied by the existing citation reader. */
    public void showCitations(List<JournalBook.Key> cited) {
        if (destroyed || cited == null || cited.isEmpty()) return;
        final List<JournalBook.Key> keys = new java.util.ArrayList<>(cited);
        final JournalHistory campaign = history();
        loadBook(error -> {
            // The asynchronous read must not remember an old campaign's citation in a new one.
            if (history() != campaign) return;
            if (book == null) { openHome(error); return; }
            if (keys.size() == 1) { openEntry(keys.get(0)); return; }
            LinearLayout choices = column();
            text(choices, "Choose a reference the game just named.", 16);
            for (JournalBook.Key key : keys)
                button(choices, key.label(), () -> { picker.dismiss(); openEntry(key); });
            picker = UpperHalfReferenceDialog.show(activity, "References just noted", scroll(choices));
        });
    }

    private void loadBook(java.util.function.Consumer<String> readyAction) {
        if (destroyed) return;
        loadedAction = readyAction; // A later explicit tap wins while a read is already running.
        if (busy) return;
        busy = true;
        NotebookController.IO.execute(() -> {
            JournalBook loaded = null; String problem = null;
            try (InputStream in = context.getAssets().open(BUNDLED_BOOK)) {
                loaded = JournalBook.read(JournalBook.readBytes(in));
            } catch (IOException | RuntimeException failure) {
                problem = "The bundled journal could not be read: " + failure.getMessage();
            }
            final JournalBook ready = loaded; final String error = problem;
            main.post(() -> {
                busy = false; if (destroyed || activity.isFinishing()) return;
                book = ready;
                java.util.function.Consumer<String> action = loadedAction;
                loadedAction = null;
                if (action != null) action.accept(error);
            });
        });
    }

    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout out = new LinearLayout(activity); out.setOrientation(LinearLayout.VERTICAL);
        out.setPadding(dp(10), dp(4), dp(10), dp(6)); out.setBackgroundColor(Color.WHITE); return out;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity); parent.addView(row, new LinearLayout.LayoutParams(-1,-2)); return row;
    }
    private ScrollView scroll(View view) {
        ScrollView scroll = new ScrollView(activity); scroll.setSmoothScrollingEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER); scroll.addView(view); return scroll;
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView out = new TextView(activity); out.setText(value); out.setTextSize(size); out.setTextColor(Color.BLACK);
        out.setPadding(0, dp(4), 0, dp(6)); parent.addView(out, new LinearLayout.LayoutParams(-1,-2)); return out;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button out = new Button(activity); out.setText(label); out.setAllCaps(false); out.setTextSize(14);
        out.setTextColor(Color.BLACK); out.setMinWidth(0); out.setMinimumWidth(0);
        out.setMinHeight(dp(44)); out.setMinimumHeight(dp(44)); out.setStateListAnimator(null);
        out.setPadding(dp(4),0,dp(4),0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xffeeeeee); bg.setStroke(dp(1), Color.DKGRAY); out.setBackgroundTintList(null); out.setBackground(bg);
        out.setOnClickListener(v -> action.run());
        parent.addView(out, parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0,-2,1) : new LinearLayout.LayoutParams(-1,-2));
        return out;
    }
    private void openHome(String error) {
        if (home != null) home.dismiss();
        LinearLayout content = column();
        if (error != null) text(content,error,15);
        if (book == null) {
            text(content,"The journal could not be opened. Reinstalling the app restores it; nothing you have written is affected.",17);
        } else {
            text(content,"Look up any number yourself. References the game cites in its own words are collected below automatically.",14);
            button(content,"Look up a number",this::lookup);
            JournalHistory history = history();
            if (history == null) text(content,"Journal history is unavailable for this notebook, so lookups are not being remembered. Existing history has not been replaced.",14);
            else {
                entryButtons(content,"Encountered in play",history.encountered(),history);
                entryButtons(content,"Recent lookups",history.recent(),history);
            }
            text(content,"Included with the app · 58 journal entries · 18 proclamations · 23 tavern tales",12);
        }
        text(content,"Encountered references are the ones the running game named in front of you, in the order it named them; the app only recognises wordings that have been observed in the game, so anything it is not sure about is left out rather than guessed. Recent numbers belong to the selected notebook and are included in its backup. The journal is read-only and never touches a game save.",12);
        home = UpperHalfReferenceDialog.show(activity,"Adventure journal",scroll(content));
    }
    private void entryButtons(LinearLayout parent, String title, List<JournalBook.Key> keys, JournalHistory history) {
        if (keys.isEmpty()) return;
        text(parent,title,16);
        for (JournalBook.Key key : keys) button(parent,key.label(),() -> openEntry(key));
    }
    private String category() { return kind == 0 ? "Journal (1–58)" : kind == 1 ? "Proclamation" : "Tavern tale (1–23)"; }
    private void lookup() {
        LinearLayout content = column();
        Button category = button(content,category(),() -> { });
        category.setOnClickListener(v -> { kind = (kind + 1) % 3; category.setText(category()); });
        StringBuilder number = new StringBuilder();
        TextView display = text(content,"Number: —",20);
        for (int first=1; first<=7; first+=3) {
            LinearLayout row = row(content);
            for (int n=first;n<first+3;n++) { final int digit=n;
                button(row,""+n,() -> { if (number.length()<3) number.append(digit); display.setText("Number: " + number); });
            }
        }
        LinearLayout bottom = row(content);
        button(bottom,"Clear",() -> { number.setLength(0); display.setText("Number: —"); });
        button(bottom,"0",() -> { if (number.length()>0 && number.length()<3) number.append('0'); display.setText("Number: " + number); });
        Button read = button(bottom,"Read",() -> { });
        text(content,"Proclamations: 59/LIX, 64/LXIV, 78/LXXVIII, 101/CI, 109/CIX, 110/CX, 114/CXIV, 120/CXX, 126/CXXVI, 129/CXXIX, 134/CXXXIV, 154/CLIV, 156/CLVI, 170/CLXX, 190/CXC, 201/CCI, 204/CCIV, 214/CCXIV.",12);
        AlertDialog picker = UpperHalfReferenceDialog.show(activity,"Reference number",scroll(content));
        read.setOnClickListener(v -> {
            try {
                JournalBook.Key key = new JournalBook.Key(kind,Integer.parseInt(number.toString()));
                picker.dismiss(); openEntry(key);
            } catch (IllegalArgumentException failure) { display.setText("No such reference. Check type and number."); }
        });
    }
    private void openEntry(JournalBook.Key key) {
        if (book == null) return;
        LinearLayout content = column();
        final JournalHistory history = history();
        /*
         * Opening a reference records it and then gets out of the way. Bookmark,
         * Check off and Link a map flag used to sit above the text; the user cut
         * them on 2026-09-15 as too complicated for reading a paragraph out of a
         * 1989 book. JournalHistory still stores all three, so existing
         * notebooks and their backups load unchanged -- only the buttons are
         * gone, and nothing new is written to them.
         */
        if (history != null) { history.opened(key); persist(); }
        for (JournalBook.Block block : book.entry(key)) {
            if (!block.isImage()) text(content,block.text,17);
            else {
                byte[] png = block.image();
                // Drawables own small, bounded decoded images; all illustrations
                // remain in original order. Tap for a larger, scrollable view.
                Bitmap image = BitmapFactory.decodeByteArray(png,0,png.length);
                if (image == null) { text(content,"Illustration could not be decoded; re-import the original book.",15); continue; }
                ImageView view = new ImageView(activity); view.setImageBitmap(image);
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                view.setContentDescription(key.label()+" original illustration; tap to enlarge");
                content.addView(view,new LinearLayout.LayoutParams(-1,dp(200)));
                text(content,"Tap illustration to enlarge.",12);
                view.setOnClickListener(v -> enlarge(key,image));
            }
        }
        // Refreshing after a bookmark/link change replaces this window instead of
        // stacking another copy, so closing never reveals a stale earlier state.
        final AlertDialog previous = entry;
        final AlertDialog[] self = new AlertDialog[1];
        self[0] = UpperHalfReferenceDialog.show(activity,key.label(),scroll(content),() -> {
            if (entry != self[0]) return;
            entry = null; if (!destroyed) openHome(null);
        });
        entry = self[0];
        if (previous != null) previous.dismiss();
    }
    /**
     * Opened from a handwritten flag page. Shows the references that were
     * linked to that exact flag before linking was removed, and lets them be
     * unlinked. Nothing here creates a new link any more.
     */
    public void showFlagLinks(String areaId, String areaLabel, int x, int y) {
        if (destroyed || busy) return;
        final JournalHistory history = history();
        LinearLayout content = column();
        if (history == null) {
            text(content,"Journal history is unavailable for this notebook, so links cannot be shown or changed.",15);
            UpperHalfReferenceDialog.show(activity,"Linked references",scroll(content)); return;
        }
        final JournalHistory.Flag flag;
        try { flag = new JournalHistory.Flag(areaId,x,y); }
        catch (IllegalArgumentException invalid) { toast("This flag cannot be linked."); return; }
        List<JournalBook.Key> keys = history.entriesFor(flag);
        text(content,"References you linked to " + x + ", " + y + " in " + areaLabel
                + ". Your handwriting on this page stays where it is.",13);
        if (keys.isEmpty()) text(content,"Nothing is linked to this flag. Linking was removed on 2026-09-15; anything linked before then is still listed here.",15);
        for (JournalBook.Key key : keys) {
            button(content,"Read " + key.label(),() -> {
                if (picker != null) picker.dismiss();
                if (book == null) { toast("Import your journal book first under Info → Journal."); return; }
                openEntry(key);
            });
            button(content,"Unlink " + key.label(),() -> {
                history.toggleLink(key,flag); persist();
                if (picker != null) picker.dismiss();
                showFlagLinks(areaId,areaLabel,x,y);
            });
        }
        picker = UpperHalfReferenceDialog.show(activity,"Linked references",scroll(content));
    }

    private void enlarge(JournalBook.Key key, Bitmap image) {
        LinearLayout content = column();
        ImageView view = new ImageView(activity); view.setImageBitmap(image);
        view.setContentDescription(key.label()+" enlarged original illustration");
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_START);
        content.addView(view,new LinearLayout.LayoutParams(-1,-2));
        UpperHalfReferenceDialog.show(activity,"Illustration · scroll to inspect",scroll(content));
    }
}
