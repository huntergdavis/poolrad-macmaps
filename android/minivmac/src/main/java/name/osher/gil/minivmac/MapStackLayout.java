package name.osher.gil.minivmac;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Gives the companion the unused portrait space; keyboard stays below the guest. */
public final class MapStackLayout extends LinearLayout {
    private int guestWidth = 640, guestHeight = 480;

    public MapStackLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setGuestSize(int width, int height) {
        if (width > 0 && height > 0) { guestWidth = width; guestHeight = height; requestLayout(); }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View companion = findViewById(R.id.companion_pane), keyboard = findViewById(R.id.keyboard);
        // Only the direct child belongs to this LinearLayout. The nested map's
        // frame parameters and visibility change with tabs and are not our budget.
        if (companion != null && companion.getParent() == this && companion.getVisibility() != GONE) {
            int keyboardHeight = 0;
            if (keyboard != null && keyboard.getVisibility() != GONE) {
                measureChild(keyboard, widthSpec, heightSpec);
                keyboardHeight = keyboard.getMeasuredHeight();
            }
            int width = MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, MeasureSpec.getSize(heightSpec) - keyboardHeight);
            ((LayoutParams) companion.getLayoutParams()).height =
                    CompanionGeometry.allocation(width, available, guestWidth, guestHeight);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
