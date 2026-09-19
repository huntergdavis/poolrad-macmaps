package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import java.util.function.Consumer;

/** One home for companion preferences; every control applies immediately. */
final class CompanionOptionsDialog {
    private CompanionOptionsDialog() { }

    static AlertDialog show(Activity activity, String area, boolean fog, boolean footprints,
            Consumer<Boolean> setFog, Consumer<Boolean> setFootprints, Runnable changed) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 12);
        list.setPadding(padding, 0, padding, padding);
        label(activity, list, area == null ? "Map defaults for areas without saved choices" : "Map appearance · " + area);
        toggle(activity, list, "Show only walked squares (fog of war)", fog, setFog);
        toggle(activity, list, "Show directional footprints", footprints, setFootprints);
        label(activity, list, "Party and messages");
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_ONELINE_PARTY,
                "One-line party rows", false, changed);
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_MIRROR_MESSAGE,
                "Large message text", false, changed);
        label(activity, list, "Save states");
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_AUTOSAVE,
                "Auto-save every five minutes", true, changed);
        label(activity, list, "Keeps the latest 20 automatic states while a party is in the world.");
        ScrollView scroll = new ScrollView(activity);
        scroll.setSmoothScrollingEnabled(false);
        scroll.addView(list);
        return UpperHalfReferenceDialog.show(activity, "Options", scroll);
    }

    private static void preference(Activity activity, LinearLayout list, SharedPreferences prefs,
            String key, String title, boolean fallback, Runnable changed) {
        toggle(activity, list, title, prefs.getBoolean(key, fallback), value -> {
            prefs.edit().putBoolean(key, value).apply();
            changed.run();
        });
    }

    private static void toggle(Activity activity, LinearLayout list, String title, boolean checked,
            Consumer<Boolean> action) {
        CheckBox box = new CheckBox(activity);
        box.setText(title);
        box.setTextColor(Color.BLACK);
        box.setTextSize(16);
        box.setButtonTintList(ColorStateList.valueOf(Color.BLACK));
        box.setStateListAnimator(null);
        box.setMinHeight(dp(activity, 48));
        box.setChecked(checked);
        box.setOnCheckedChangeListener((button, value) -> action.accept(value));
        list.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void label(Activity activity, LinearLayout list, String title) {
        TextView text = new TextView(activity);
        text.setText(title);
        text.setTextColor(Color.BLACK);
        text.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
        list.addView(text);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
