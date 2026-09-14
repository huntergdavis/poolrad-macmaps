package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;

import name.osher.gil.minivmac.mapper.MoneyReference;
import name.osher.gil.minivmac.mapper.MoneyReference.Coin;

/** Offline arithmetic only; this panel never reads or writes the guest purse. */
public final class MoneyReferenceDialog {
    private final Activity activity;
    private final long[] counts = new long[Coin.values().length];
    private final Button[] selectors = new Button[Coin.values().length];
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private Coin selected = Coin.GOLD;
    private TextView editing;
    private TextView equivalents;
    private TextView change;
    private Button clear;

    private MoneyReferenceDialog(Activity activity) { this.activity = activity; }

    public static void show(Activity activity) { new MoneyReferenceDialog(activity).open(); }

    private void open() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = vertical();
        content.setPadding(dp(10), dp(4), dp(10), dp(8));
        scroll.addView(content);
        content.addView(text("Tap a coin, then enter its count. Different coins add together.", 14));

        LinearLayout coins = new LinearLayout(activity);
        for (Coin coin : Coin.values()) {
            Button selector = button("");
            selector.setTextSize(14);
            selector.setOnClickListener(v -> { selected = coin; refresh(); });
            selectors[coin.ordinal()] = selector;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
            params.setMargins(dp(1), dp(4), dp(1), dp(4));
            coins.addView(selector, params);
        }
        content.addView(coins);
        editing = text("", 16);
        editing.setTypeface(null, Typeface.BOLD);
        editing.setPadding(0, dp(4), 0, dp(8));
        editing.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(editing);

        LinearLayout body = new LinearLayout(activity);
        equivalents = text("", 15);
        equivalents.setPadding(0, dp(4), dp(8), 0);
        body.addView(equivalents, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout keypad = vertical();
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(activity);
            for (int column = 0; column < 3; column++) {
                int digit = row < 3 ? row * 3 + column + 1 : 0;
                Button key;
                if (row == 3 && column == 0) {
                    clear = button("C");
                    clear.setOnClickListener(v -> { counts[selected.ordinal()] = 0; refresh(); });
                    key = clear;
                } else if (row == 3 && column == 2) {
                    key = button("⌫");
                    key.setContentDescription("Delete last digit");
                    key.setOnClickListener(v -> { counts[selected.ordinal()] /= 10; refresh(); });
                } else {
                    key = button(Integer.toString(digit));
                    key.setOnClickListener(v -> enterDigit(digit));
                }
                line.addView(key, new LinearLayout.LayoutParams(0, dp(44), 1));
            }
            keypad.addView(line);
        }
        Button reset = button("Reset all");
        reset.setTextSize(14);
        reset.setContentDescription("Reset all five coin amounts to zero");
        reset.setOnClickListener(v -> { Arrays.fill(counts, 0); refresh(); });
        keypad.addView(reset, new LinearLayout.LayoutParams(-1, dp(44)));
        body.addView(keypad, new LinearLayout.LayoutParams(dp(152), -2));
        content.addView(body);

        change = text("", 15);
        change.setPadding(0, dp(10), 0, dp(8));
        content.addView(change);
        content.addView(text("Any remainder is shown in copper; no value is rounded away. "
                + "C clears the selected coin. Up to 999,999,999 of each coin.", 13));
        TextView source = text("Gems and jewelry: value unknown until appraised in the game; excluded here.\n\n"
                + "Source: original Macintosh Pool of Radiance tables, Money Conversions, "
                + "and Rule Book, Character Screen / Shops.\n"
                + "200 cp = 20 sp = 2 ep = 1 gp; 1 pp = 5 gp.", 13);
        source.setPadding(0, dp(8), 0, 0);
        content.addView(source);
        refresh();
        UpperHalfReferenceDialog.show(activity, "Money conversion", scroll);
    }

    private void enterDigit(int digit) {
        try {
            counts[selected.ordinal()] = MoneyReference.appendDigit(counts[selected.ordinal()], digit);
            refresh();
        } catch (IllegalArgumentException failure) {
            editing.setText("Limit: 999,999,999 " + selected.abbreviation + ". Use C or ⌫ to edit.");
        }
    }

    private void refresh() {
        for (Coin coin : Coin.values()) {
            Button selector = selectors[coin.ordinal()];
            selector.setText(coin.abbreviation.toUpperCase(Locale.US) + "\n" + counts[coin.ordinal()]);
            selector.setTextColor(coin == selected ? Color.WHITE : Color.BLACK);
            selector.setBackgroundColor(coin == selected ? Color.BLACK : Color.rgb(238, 238, 238));
            selector.setSelected(coin == selected);
            selector.setContentDescription(coin.label + ": " + numbers.format(counts[coin.ordinal()])
                    + (coin == selected ? ", selected" : ", tap to edit"));
        }
        editing.setText("Editing " + selected.label + " · " + numbers.format(counts[selected.ordinal()])
                + " " + selected.abbreviation);
        clear.setContentDescription("Clear " + selected.label + " amount");
        long total = MoneyReference.totalCopper(counts);
        StringBuilder result = new StringBuilder("Equivalent amounts\n");
        for (Coin coin : Coin.values()) {
            MoneyReference.Equivalent amount = MoneyReference.equivalent(total, coin);
            result.append('\n').append(numbers.format(amount.coins)).append(' ').append(coin.abbreviation);
            if (amount.copperRemainder != 0) {
                result.append(" + ").append(numbers.format(amount.copperRemainder)).append(" cp");
            }
        }
        equivalents.setText(result.toString());
        long[] compact = MoneyReference.compactChange(total);
        StringBuilder summary = new StringBuilder("Compact change: ");
        Coin[] coins = Coin.values();
        boolean added = false;
        for (int i = coins.length - 1; i >= 0; i--) {
            if (compact[i] == 0) continue;
            if (added) summary.append(" + ");
            summary.append(numbers.format(compact[i])).append(' ').append(coins[i].abbreviation);
            added = true;
        }
        if (!added) summary.append("0 cp");
        change.setText(summary.toString());
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(Color.BLACK);
        view.setTextSize(size);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.BLACK);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
