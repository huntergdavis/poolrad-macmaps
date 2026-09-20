package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.CheckBox;
import android.widget.Button;
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
        label(activity, list, "Map size · all areas");
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_ORIGINAL_TILE_SCALE,
                "Original area tile size (1:1)", false, changed);
        label(activity, list, "32 pixels per tile. Drag the map to scroll; tap its header to find the party.");
        label(activity, list, "Enter shortcut");
        Button placement = new Button(activity);
        placement.setAllCaps(false);
        placement.setTextColor(Color.BLACK);
        Runnable updatePlacement = () -> placement.setText("Enter: " + EnterPlacement.parse(
                prefs.getString(SettingsFragment.KEY_PREF_ENTER_PLACEMENT, null)).label);
        updatePlacement.run();
        placement.setOnClickListener(view -> {
            EnterPlacement[] choices = EnterPlacement.values();
            String[] labels = new String[choices.length];
            for (int i=0; i<choices.length; i++) labels[i] = choices[i].label;
            new AlertDialog.Builder(activity).setTitle("Enter shortcut placement")
                    .setSingleChoiceItems(labels, EnterPlacement.parse(prefs.getString(
                            SettingsFragment.KEY_PREF_ENTER_PLACEMENT, null)).ordinal(), (dialog, which) -> {
                        prefs.edit().putString(SettingsFragment.KEY_PREF_ENTER_PLACEMENT,
                                choices[which].value).apply();
                        changed.run(); updatePlacement.run(); dialog.dismiss();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        });
        list.addView(placement, new LinearLayout.LayoutParams(-1, -2));
        label(activity, list, "Party and messages");
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_ONELINE_PARTY,
                "One-line party rows", false, changed);
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_MIRROR_MESSAGE,
                "Large message text", false, changed);
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_AUTO_SKIP_MESSAGES,
                "Auto-skip informational messages", false, changed);
        label(activity, list, "Story prompts, choices and confirmations stay manual.");
        label(activity, list, "Save states");
        preference(activity, list, prefs, SettingsFragment.KEY_PREF_AUTOLOAD,
                "Load last snapshot when the app starts", true, changed);
        label(activity, list, "Requires matching disks and an existing notebook. Start normally skips the launch attempt.");
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
