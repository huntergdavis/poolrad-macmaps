package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import name.osher.gil.minivmac.reference.SpellReference;
import name.osher.gil.minivmac.reference.SpellReference.Caster;
import name.osher.gil.minivmac.reference.SpellReference.Spell;

/** Touch-only, offline reference; never issues commands to the Macintosh. */
public final class SpellReferenceDialog {
    private final Activity activity;
    private Caster caster;
    private int level;
    private Button classButton, levelButton;
    private LinearLayout results;
    private TextView count;

    private SpellReferenceDialog(Activity activity) { this.activity = activity; }

    public static void show(Activity activity) { new SpellReferenceDialog(activity).open(); }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(4), dp(12), dp(8));
        layout.setBackgroundColor(Color.WHITE);
        return layout;
    }

    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextColor(Color.BLACK); view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(6));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener click) {
        Button button = new Button(activity);
        button.setText(label); button.setAllCaps(false); button.setTextColor(Color.BLACK);
        button.setTextSize(14); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setBackgroundTintList(ColorStateList.valueOf(0xffeeeeee));
        button.setOnClickListener(click);
        if (parent.getOrientation() == LinearLayout.HORIZONTAL)
            parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        else parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void open() {
        LinearLayout content = column();
        text(content, "SSI Pool of Radiance Rule Book + Clue Book · offline", 13);
        LinearLayout filters = row(content);
        classButton = button(filters, "", v -> {
            caster = caster == null ? Caster.CLERIC : caster == Caster.CLERIC ? Caster.MAGIC_USER : null;
            refresh();
        });
        classButton.setContentDescription("Class filter. Tap to cycle All, Cleric, Magic-user.");
        levelButton = button(filters, "", v -> { level = (level + 1) % 4; refresh(); });
        levelButton.setContentDescription("Spell level filter. Tap to cycle All, 1, 2, 3.");
        text(content, "Tap class or level to cycle, then browse the spell names below.", 12);
        count = text(content, "", 13);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results, new LinearLayout.LayoutParams(-1, -2));
        button(content, "Sources, units & casting rules", v -> showSources());
        text(content, "SSI Rule Book + Clue Book. Printed values; Mac spell behavior unverified.", 12);
        UpperHalfReferenceDialog.show(activity, "Spells", scroll(content));
        refresh();
    }

    private void refresh() {
        classButton.setText("Class: " + (caster == null ? "All" : caster.label));
        classButton.setContentDescription(classButton.getText() + ". Tap to cycle All, Cleric, Magic-user.");
        levelButton.setText("Level: " + (level == 0 ? "All" : level));
        levelButton.setContentDescription(levelButton.getText() + ". Tap to cycle All, 1, 2, 3.");
        List<Spell> matches = SpellReference.filter(caster, level);
        count.setText(matches.size() + (matches.size() == 1 ? " spell" : " spells")
                + " · tap a name for details");
        results.removeAllViews();
        if (matches.isEmpty()) {
            text(results, "No spells match these filters.", 16);
            button(results, "Reset filters", v -> { caster = null; level = 0; refresh(); });
        }
        for (Spell spell : matches) button(results,
                spell.heading() + (spell.inChart ? "" : " · availability uncertain"), v -> showSpell(spell));
    }

    private void showSpell(Spell spell) {
        LinearLayout detail = column();
        text(detail, spell.caster.label + " · spell level " + spell.level, 15);
        text(detail, "Effect\n" + spell.effect, 17);
        text(detail, "Range\n" + spell.range, 16);
        text(detail, "Duration\n" + spell.duration, 16);
        text(detail, "Targets / area\n" + spell.targeting, 16);
        text(detail, "Usable\n" + spell.usable, 16);
        if (!spell.notes.isEmpty()) text(detail, "Reference note\n" + spell.notes, 15);
        text(detail, spell.provenance() + " Original printed rules; Macintosh behavior unverified.", 12);
        button(detail, "Sources, units & casting rules", v -> showSources());
        UpperHalfReferenceDialog.show(activity, spell.name, scroll(detail));
    }

    private void showSources() {
        LinearLayout content = column();
        text(content, SpellReference.SOURCE_SUMMARY, 16);
        text(content, SpellReference.UNITS, 15);
        text(content, SpellReference.MENUS, 15);
        text(content, "All 54 class/level entries in SSI's chart are included. Resist Cold is a separate, flagged Rule Book entry. Conflicts and missing values remain visible in the affected spell details.", 15);
        text(content, "Rule Book archive\n" + SpellReference.RULEBOOK_URL
                + "\n\nClue Book archive (printed p. 63; PDF p. 34)\n" + SpellReference.CHART_URL, 12);
        text(content, "Companion inspiration: Gold Box Companion by Zorbus · https://gbc.zorbus.net/\nReference descriptions are original summaries of game mechanics. This panel needs no network connection.", 13);
        UpperHalfReferenceDialog.show(activity, "Spell reference sources", scroll(content));
    }
}
