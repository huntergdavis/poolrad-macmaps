package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import name.osher.gil.minivmac.mapper.PartyState;

/** Read-only details from the row the user tapped, never a character editor. */
public final class PartyDetailsDialog {
    private PartyDetailsDialog() { }

    public static AlertDialog show(Activity activity, PartyState.Member member, Runnable onDismiss) {
        float density = activity.getResources().getDisplayMetrics().density;
        int inset = Math.round(16 * density);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(inset, 0, inset, inset);
        column.setBackgroundColor(Color.WHITE);
        line(activity, column, member.classLabel(), 18);
        line(activity, column, "Health: " + member.currentHp + " / " + member.maxHp + " HP", 18);
        line(activity, column, "Armor class: " + (member.armorClass == null ? "Unavailable" : member.armorClass), 18);
        line(activity, column, "Lower armor class is better. These are the original game's values, not editable stats.", 14);
        line(activity, column, "Snapshot when opened. Close and tap the row again to refresh. Zero HP alone does not identify a character's condition.", 14);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(column);
        return UpperHalfReferenceDialog.show(activity, member.name, scroll, onDismiss);
    }

    private static void line(Activity activity, LinearLayout column, String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.BLACK);
        int gap = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(0, gap, 0, gap);
        column.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }
}
