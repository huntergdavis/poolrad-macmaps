package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.Supplier;

/** Shared change-only, lifecycle-bound refresh for read-only companion pages. */
final class LiveTextReferenceDialog {
    private LiveTextReferenceDialog() { }

    static void show(Activity activity, String title, String explanation, Supplier<String> reading,
                     String footer, int textSize, String actionLabel, Runnable action) {
        int pad = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(Color.WHITE);
        column.addView(text(activity, explanation, 16));
        Button actionButton = null;
        if (action != null) {
            actionButton = new Button(activity);
            actionButton.setText(actionLabel);
            column.addView(actionButton);
        }
        TextView body = text(activity, "", textSize);
        body.setPadding(0, pad, 0, pad);
        body.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(body);
        column.addView(text(activity, footer, 14));
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
                String value = reading.get();
                if (!value.contentEquals(body.getText())) body.setText(value);
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
        androidx.appcompat.app.AlertDialog dialog = UpperHalfReferenceDialog.show(activity, title, scroll, () -> {
            closed[0] = true;
            main.removeCallbacks(refresh);
            if (activity instanceof androidx.lifecycle.LifecycleOwner)
                ((androidx.lifecycle.LifecycleOwner) activity).getLifecycle().removeObserver(lifecycle);
        });
        if (actionButton != null) actionButton.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        main.removeCallbacks(refresh);
        refresh.run();
    }

    private static TextView text(Activity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextColor(Color.BLACK); view.setTextSize(size);
        view.setFocusable(false);
        return view;
    }
}
