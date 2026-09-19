package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.Supplier;
import name.osher.gil.minivmac.mapper.PartyState;

/** The game's validated chain order, without adding another mark to every map row. */
public final class PartyOrderDialog {
    private PartyOrderDialog() { }

    public static void show(Activity activity, Supplier<PartyState> reading) {
        int pad = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(Color.WHITE);
        column.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        TextView explanation = text(activity, "The game's current order, first to last.", 16);
        column.addView(explanation);
        TextView order = text(activity, "", 20);
        order.setPadding(0, pad, 0, pad);
        order.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(order);
        column.addView(text(activity, "Change the order in the game: Encamp → Alter → Order.", 14));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFocusable(false);
        scroll.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(false);
        scroll.addView(column);
        Handler main = new Handler(Looper.getMainLooper());
        boolean[] closed = {false};
        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (closed[0]) return;
                PartyState party = reading.get();
                String value = "Marching order unavailable";
                if (party != null && !party.members.isEmpty()) {
                    StringBuilder rows = new StringBuilder();
                    for (int i = 0; i < party.members.size(); i++) {
                        if (i > 0) rows.append("\n");
                        rows.append(i + 1).append(". ").append(party.members.get(i).displayName());
                    }
                    value = rows.toString();
                }
                if (!value.contentEquals(order.getText())) order.setText(value);
                main.postDelayed(this, 1000);
            }
        };
        androidx.lifecycle.LifecycleEventObserver lifecycle = (owner, event) -> {
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) main.removeCallbacks(refresh);
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                main.removeCallbacks(refresh); refresh.run();
            }
        };
        if (activity instanceof androidx.lifecycle.LifecycleOwner)
            ((androidx.lifecycle.LifecycleOwner) activity).getLifecycle().addObserver(lifecycle);
        UpperHalfReferenceDialog.show(activity, "Marching order", scroll, () -> {
            closed[0] = true;
            main.removeCallbacks(refresh);
            if (activity instanceof androidx.lifecycle.LifecycleOwner)
                ((androidx.lifecycle.LifecycleOwner) activity).getLifecycle().removeObserver(lifecycle);
        });
        main.removeCallbacks(refresh);
        refresh.run();
    }

    private static TextView text(Activity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(Color.BLACK);
        view.setTextSize(size);
        view.setFocusable(false);
        return view;
    }
}
