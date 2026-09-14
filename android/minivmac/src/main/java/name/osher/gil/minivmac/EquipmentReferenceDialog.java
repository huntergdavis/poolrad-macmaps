package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;
import name.osher.gil.minivmac.reference.EquipmentReference;
import name.osher.gil.minivmac.reference.EquipmentReference.Entry;
import name.osher.gil.minivmac.reference.EquipmentReference.Group;
import name.osher.gil.minivmac.reference.EquipmentReference.Kind;

/** Finite, touch-only equipment lists. Never requests a keyboard or sends guest input. */
public final class EquipmentReferenceDialog {
    private final Activity activity;
    private Kind kind = Kind.WEAPON;
    private Group group;
    private Entry comparing;
    private Button weapons, armor, ammunition, categories, cancelCompare;
    private LinearLayout results;
    private TextView count;
    private ScrollView browserScroll;

    private EquipmentReferenceDialog(Activity activity) { this.activity = activity; }

    public static void show(Activity activity) { new EquipmentReferenceDialog(activity).open(); }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), dp(8));
        content.setBackgroundColor(Color.WHITE);
        return content;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
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

    private Button button(LinearLayout parent, String label, View.OnClickListener action) {
        Button button = new Button(activity);
        button.setText(label); button.setTextColor(Color.BLACK); button.setTextSize(14);
        button.setAllCaps(false); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(6), dp(4), dp(6), dp(4));
        button.setBackgroundTintList(ColorStateList.valueOf(0xffeeeeee));
        button.setOnClickListener(action);
        parent.addView(button, parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void open() {
        LinearLayout content = column();
        text(content, "Original printed stats + Macintosh base values · offline", 13);
        LinearLayout lists = row(content);
        weapons = button(lists, "Weapons", v -> select(Kind.WEAPON));
        armor = button(lists, "Armor", v -> select(Kind.ARMOR));
        ammunition = button(lists, "Ammo", v -> select(Kind.AMMUNITION));
        categories = button(content, "", v -> {
            group = group == null ? Group.values()[0]
                    : group.ordinal() == Group.values().length - 1 ? null : Group.values()[group.ordinal() + 1];
            refresh();
        });
        count = text(content, "", 13);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        cancelCompare = button(content, "Cancel comparison", v -> { comparing = null; refresh(); });
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results, new LinearLayout.LayoutParams(-1, -2));
        button(content, "Sources, units & equipment rules", v -> showSources());
        browserScroll = scroll(content);
        UpperHalfReferenceDialog.show(activity, "Weapons & armor", browserScroll);
        refresh();
    }

    private void select(Kind next) {
        if (next != kind) { comparing = null; group = null; }
        kind = next;
        refresh();
    }

    private void selected(Button button, boolean selected) {
        button.setSelected(selected);
        button.setTextColor(selected ? Color.WHITE : Color.BLACK);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? Color.BLACK : 0xffeeeeee));
        button.setContentDescription(button.getText() + (selected ? ", selected list" : ", show list"));
    }

    private void refresh() {
        selected(weapons, kind == Kind.WEAPON);
        selected(armor, kind == Kind.ARMOR);
        selected(ammunition, kind == Kind.AMMUNITION);
        categories.setVisibility(kind == Kind.WEAPON ? View.VISIBLE : View.GONE);
        categories.setText("Category: " + (group == null ? "All weapons" : group.label) + " · tap to cycle");
        List<Entry> rows = EquipmentReference.browse(kind, group);
        count.setText(comparing == null ? rows.size() + " entries · tap for details or comparison"
                : "Comparing " + comparing.name + " · choose another item");
        cancelCompare.setVisibility(comparing == null ? View.GONE : View.VISIBLE);
        results.removeAllViews();
        for (Entry entry : rows) {
            Button item = button(results, entry.name + "\n" + entry.summary()
                    + (entry.macDifference.isEmpty() ? "" : "\nMac difference: see details"), v -> {
                if (comparing == null) showEntry(entry);
                else if (!entry.id.equals(comparing.id)) showComparison(comparing, entry);
            });
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            if (comparing != null && entry.id.equals(comparing.id)) {
                item.setEnabled(false);
                item.setText(entry.name + " · comparison baseline\n" + entry.summary());
            }
        }
    }

    private String protection(Entry entry) {
        if (entry.id.equals("small-shield")) return "Subtract 1 from armor AC; AC 9 if otherwise unarmored.";
        return "AC " + entry.armorClass + " before character bonuses or effects.";
    }

    private String movement(Entry entry) {
        return entry.maxMovement == 0 ? "No separate armor movement cap in the printed table."
                : "At most " + entry.maxMovement + " combat squares; carried weight can reduce this further.";
    }

    private void showEntry(Entry entry) {
        LinearLayout detail = column();
        text(detail, entry.kind.label + (entry.group == null ? "" : " · " + entry.group.label), 14);
        if (entry.kind == Kind.WEAPON) {
            text(detail, "Printed damage\nMan-sized: " + entry.smallDamage + "\nLarger targets: " + entry.largeDamage, 17);
            text(detail, "Printed hands required\n" + entry.hands, 16);
        } else if (entry.kind == Kind.ARMOR) {
            text(detail, "Printed protection\n" + protection(entry), 17);
            text(detail, "Printed maximum movement\n" + movement(entry), 16);
        }
        text(detail, "Base cost / value — Macintosh data\n" + entry.costText(), 16);
        text(detail, "Weight — Macintosh data\n" + entry.weightText(), 16);
        if (entry.kind == Kind.AMMUNITION) text(detail, "Used with\n" + (entry.id.equals("arrows") ? "Bows; printed bow permission: Fighter." : "Crossbows; printed crossbow permission: Fighter."), 16);
        else text(detail, "Printed class permissions\n" + entry.restrictions, 16);
        if (!entry.notes.isEmpty()) text(detail, "Equipment notes\n" + entry.notes, 15);
        if (!entry.macDifference.isEmpty()) text(detail, "Printed / Macintosh disagreement\n" + entry.macDifference, 16);
        text(detail, "Stored item fields are not a guaranteed shop quote or an individually tested combat result.", 13);
        final AlertDialog[] dialog = new AlertDialog[1];
        button(detail, "Compare with another " + (entry.kind == Kind.WEAPON ? "weapon" : "item"), v -> {
            comparing = entry; kind = entry.kind; group = null;
            dialog[0].dismiss();
            refresh(); browserScroll.scrollTo(0, 0);
        });
        text(detail, entry.provenance(), 12);
        button(detail, "Sources, units & equipment rules", v -> showSources());
        dialog[0] = UpperHalfReferenceDialog.show(activity, entry.name, scroll(detail));
    }

    private void comparisonRow(LinearLayout parent, String label, String left, String right) {
        text(parent, label, 13);
        LinearLayout pair = row(parent);
        for (String value : new String[]{left, right}) {
            TextView cell = new TextView(activity);
            cell.setText(value); cell.setTextColor(Color.BLACK); cell.setTextSize(15);
            cell.setPadding(dp(4), 0, dp(8), dp(8));
            pair.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void showComparison(Entry left, Entry right) {
        LinearLayout content = column();
        comparisonRow(content, "Compare base equipment", left.name, right.name);
        if (left.kind == Kind.WEAPON) {
            comparisonRow(content, "Printed damage: man / larger", left.smallDamage + " / " + left.largeDamage, right.smallDamage + " / " + right.largeDamage);
            comparisonRow(content, "Printed hands", Integer.toString(left.hands), Integer.toString(right.hands));
        } else if (left.kind == Kind.ARMOR) {
            comparisonRow(content, "Printed protection", protection(left), protection(right));
            comparisonRow(content, "Printed movement", movement(left), movement(right));
        }
        comparisonRow(content, "Mac base value — not a shop quote", left.costText(), right.costText());
        comparisonRow(content, "Mac weight / encumbrance", left.weightText(), right.weightText());
        comparisonRow(content, "Printed class permissions", left.restrictions, right.restrictions);
        comparisonRow(content, "Notes", left.notes.isEmpty() ? "—" : left.notes, right.notes.isEmpty() ? "—" : right.notes);
        if (!left.macDifference.isEmpty() || !right.macDifference.isEmpty())
            comparisonRow(content, "Printed / Mac differences", left.macDifference.isEmpty() ? "None noted here." : left.macDifference,
                    right.macDifference.isEmpty() ? "None noted here." : right.macDifference);
        text(content, "No strength, dexterity, magical modifiers or character state included. Stored fields and printed tables are not individually verified combat outcomes.", 13);
        text(content, left.provenance() + "\n\n" + right.provenance(), 12);
        UpperHalfReferenceDialog.show(activity, "Equipment comparison", scroll(content));
    }

    private void showSources() {
        LinearLayout content = column();
        text(content, EquipmentReference.SOURCE_SUMMARY, 16);
        text(content, EquipmentReference.BASE_VALUES, 15);
        text(content, EquipmentReference.RULES, 15);
        text(content, EquipmentReference.DISAGREEMENTS, 15);
        text(content, "Printed source\n" + EquipmentReference.JOURNAL_URL + "\n\nItem-format research by Gold Box Companion's author\n" + EquipmentReference.FORMAT_URL, 12);
        text(content, "The bundled catalog is our own factual summary. No scanned manuals or private game assets are shipped. Once installed, this panel uses no network, cloud or LLM.", 13);
        UpperHalfReferenceDialog.show(activity, "Equipment reference sources", scroll(content));
    }
}
