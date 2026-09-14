package name.osher.gil.minivmac;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import name.osher.gil.minivmac.personal.PersonalPackage;

/** Optional first-use bootstrap, before any guest disk can be mounted.
 * Reuses the existing FileManager, ROM settings and guest auto-mount/startup path.
 */
final class PersonalSetupController {
    private static final String DECIDED = "poolrad_personal_setup_decided";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final MiniVMac activity;
    private final Context context;
    private final SharedPreferences prefs;
    private final Runnable ready;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean destroyed;
    private ScrollView screen;

    PersonalSetupController(MiniVMac activity, Runnable ready) {
        this.activity = activity; this.context = activity.getApplicationContext();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.ready = ready;
    }

    boolean startIfNeeded() {
        if (!BuildConfig.PERSONAL_PACKAGE || prefs.getBoolean(DECIDED, false)
                || prefs.getString(SettingsFragment.KEY_PREF_ROM, null) != null) return false;
        prepare(); return true;
    }

    void onDestroy() { destroyed = true; }
    private boolean alive() { return !destroyed && !activity.isFinishing(); }

    private LinearLayout page(String heading, String message) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding); page.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(activity); title.setText(heading);
        title.setTextColor(Color.BLACK); title.setTextSize(25); page.addView(title);
        TextView body = new TextView(activity); body.setText(message);
        body.setTextColor(Color.BLACK); body.setTextSize(17);
        body.setPadding(0, padding, 0, padding); page.addView(body);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        screen = scroll;
        activity.setContentView(scroll); return page;
    }

    private void finish() {
        if (!alive()) return;
        // Fragment replacement removes other fragments, not this temporary raw View.
        if (screen != null && screen.getParent() instanceof ViewGroup)
            ((ViewGroup) screen.getParent()).removeView(screen);
        screen = null;
        ready.run();
    }

    private void prepare() {
        if (!alive()) return;
        LinearLayout page = page("Preparing your personal copy",
                "Verifying the bundled ROM and disks, then making your writable copy. "
                + "This happens once. App updates never replace your existing disks or saves.");
        page.addView(new ProgressBar(activity));
        IO.execute(() -> {
            try {
                PersonalPackage.Result result = PersonalPackage.install(context.getFilesDir(),
                        name -> context.getAssets().open("personal/" + name));
                if (!FileManager.getInstance().init(context)) throw new IOException("Local disk storage is unavailable");
                SharedPreferences.Editor edit = prefs.edit().putBoolean(DECIDED, true);
                // Never replace an existing user's ROM/machine choice, including a concurrent Activity's choice.
                if (result.status() != PersonalPackage.Status.EXISTING_USER_FILES
                        && prefs.getString(SettingsFragment.KEY_PREF_ROM, null) == null
                        && FileManager.getInstance().getRomFile("MacII.ROM").isFile()) {
                    String romName = RomManager.knownRomName(result.romChecksum());
                    if (romName == null) throw new IOException("The installed ROM profile is unsupported");
                    edit.putString(SettingsFragment.KEY_PREF_ROM, romName);
                    edit.putString(SettingsFragment.KEY_PREF_ROM_FILE, "MacII.ROM");
                    edit.putLong(SettingsFragment.KEY_PREF_ROM_CHECKSUM, result.romChecksum());
                    edit.putString(SettingsFragment.KEY_PREF_MACHINE, "libmnvmcoreii.so");
                }
                if (!edit.commit()) throw new IOException("Could not retain first-run settings");
                main.post(this::finish);
            } catch (IOException | RuntimeException failure) {
                Log.w("PoolRad.Personal", "Personal setup did not finish", failure);
                main.post(() -> { if (alive()) failed(failure.getMessage()); });
            }
        });
    }

    private void failed(String detail) {
        LinearLayout page = page("Personal setup needs attention",
                "Existing disks and saves were not replaced. "
                + "Retry the verified package, or use the normal ROM/disk import tools.\n\n"
                + (detail == null ? "The package could not be read." : detail));
        Button retry = new Button(activity); retry.setText("Retry personal setup");
        retry.setOnClickListener(v -> prepare()); page.addView(retry);
        Button manual = new Button(activity); manual.setText("Use my own files");
        manual.setOnClickListener(v -> {
            if (prefs.edit().putBoolean(DECIDED, true).commit()) finish();
            else failed("Android could not save your choice. Free some storage and try again.");
        });
        page.addView(manual);
    }
}
