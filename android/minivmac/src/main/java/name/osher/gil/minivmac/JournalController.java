package name.osher.gil.minivmac;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import java.io.*;
import java.util.List;
import name.osher.gil.minivmac.journal.JournalBook;
import name.osher.gil.minivmac.journal.JournalHistory;

/** Local reference documents only. Never samples or writes the guest. */
public final class JournalController {
    private final AppCompatActivity activity;
    private final Context context;
    private final AtomicFile file;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> importer;
    private JournalBook book;
    private JournalHistory history;
    private String historyKey;
    private boolean destroyed, busy;
    private AlertDialog home;
    private int kind;

    // Register unconditionally alongside the existing Activity-owned pickers.
    public JournalController(AppCompatActivity activity) {
        this.activity = activity; context = activity.getApplicationContext();
        file = new AtomicFile(new File(context.getFilesDir(), "journal.prjr"));
        importer = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument() {
            @Override public Intent createIntent(Context context, String[] types) {
                return super.createIntent(context, types).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }
        }, this::importResult);
    }
    public void onDestroy() { destroyed = true; }
    private void toast(String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }

    public void show() {
        if (destroyed || busy) return;
        busy = true;
        NotebookController.IO.execute(() -> {
            JournalBook loaded = null; String problem = null;
            try (InputStream in = file.openRead()) { loaded = JournalBook.read(JournalBook.readBytes(in)); }
            catch (FileNotFoundException missing) { /* First use, not an error. */ }
            catch (IOException | RuntimeException failure) { problem = "Reference file could not be read: " + failure.getMessage(); }
            final JournalBook ready = loaded; final String error = problem;
            main.post(() -> {
                busy = false; if (destroyed || activity.isFinishing()) return;
                book = ready;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                historyKey = "journal." + prefs.getString("poolrad_notebook_id", "unassigned");
                try { history = new JournalHistory(prefs.getString(historyKey + ".recent", ""), prefs.getString(historyKey + ".stars", "")); }
                catch (IllegalArgumentException failure) {
                    history = null; toast("Journal history is damaged; existing history has not been replaced.");
                }
                openHome(error);
            });
        });
    }

    private void importBook() {
        if (busy || destroyed) return;
        if (home != null) home.dismiss();
        try { importer.launch(new String[]{"*/*"}); }
        catch (RuntimeException failure) { toast("Android could not open the local document picker."); }
    }
    private void importResult(Uri uri) {
        if (uri == null) { toast("Journal import cancelled; existing book unchanged."); return; }
        busy = true;
        NotebookController.IO.execute(() -> {
            String error = null;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                byte[] bytes = JournalBook.readBytes(in);
                JournalBook candidate = JournalBook.read(bytes);
                // Verify actual PNG decodability before publishing; header bounds
                // were already checked by the pure-Java book parser.
                for (JournalBook.Key key : candidate.keys()) for (JournalBook.Block block : candidate.entry(key)) {
                    if (!block.isImage()) continue;
                    byte[] png = block.image(); Bitmap image = BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (image == null) throw new IOException("Unreadable journal illustration");
                    image.recycle();
                }
                FileOutputStream out = null;
                try { out = file.startWrite(); out.write(bytes); out.flush(); out.getFD().sync(); file.finishWrite(out); }
                catch (IOException | RuntimeException | OutOfMemoryError failure) {
                    if (out != null) file.failWrite(out); throw failure;
                }
            } catch (IOException | RuntimeException | OutOfMemoryError failure) { error = failure.getMessage(); if (error == null) error = "Insufficient memory or invalid file"; }
            final String problem = error;
            main.post(() -> {
                busy = false;
                toast(problem == null ? "Journal imported: 99 references, available offline." : "Journal import failed; previous book retained. " + problem);
                if (!destroyed && !activity.isFinishing()) show();
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
            text(content,"Import your private .prjr journal book once. Text and illustrations then work entirely offline.",17);
            text(content,"Prepare it from your supplied Macintosh journal documents with tools/prepare-journal.py. No game files are downloaded or included in the public APK.",14);
        } else {
            text(content,"Read only the number the game gives you. Lookups are manual, not automatic encounter detection.",14);
            button(content,"Look up a number",this::lookup);
            if (history != null) {
                recentButtons(content,"Bookmarked",history.bookmarks());
                recentButtons(content,"Recent lookups",history.recent());
            }
            text(content,book.source + " · 58 journal entries · 18 proclamations · 23 tavern tales",12);
        }
        button(content,book == null ? "Import journal book" : "Replace reference book…",this::importBook);
        text(content,"Recent numbers and bookmarks belong to the selected notebook. They are local to this app and are not yet included in notebook backups. Importing a reference book never changes a game save.",12);
        home = UpperHalfReferenceDialog.show(activity,"Adventure journal",scroll(content));
    }
    private void recentButtons(LinearLayout parent, String title, List<JournalBook.Key> keys) {
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
    private void persistHistory() {
        if (history == null) return;
        final String key=historyKey, recent=history.recentValue(), stars=history.bookmarkValue();
        NotebookController.IO.execute(() -> {
            boolean saved = PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(key+".recent",recent).putString(key+".stars",stars).commit();
            if (!saved) main.post(() -> toast("Journal history could not be saved; keep the reference number."));
        });
    }
    private void openEntry(JournalBook.Key key) {
        if (book == null) return;
        LinearLayout content = column();
        if (history != null) {
            history.opened(key); persistHistory();
            Button star = button(content,history.bookmarked(key) ? "Remove bookmark" : "Bookmark",() -> { });
            star.setOnClickListener(v -> { history.toggle(key); persistHistory(); star.setText(history.bookmarked(key) ? "Remove bookmark" : "Bookmark"); });
        }
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
        UpperHalfReferenceDialog.show(activity,key.label(),scroll(content),() -> { if (!destroyed) openHome(null); });
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
