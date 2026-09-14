package name.osher.gil.minivmac;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Gives the map the unused portrait space; keyboard stays below the guest. */
public final class MapStackLayout extends LinearLayout {
    private int guestWidth = 640, guestHeight = 480;

    public MapStackLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setGuestSize(int width, int height) {
        if (width > 0 && height > 0) { guestWidth = width; guestHeight = height; requestLayout(); }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View map = findViewById(R.id.live_map), keyboard = findViewById(R.id.keyboard);
        if (map != null && map.getVisibility() != GONE) {
            int keyboardHeight = 0;
            if (keyboard != null && keyboard.getVisibility() != GONE) {
                measureChild(keyboard, widthSpec, heightSpec);
                keyboardHeight = keyboard.getMeasuredHeight();
            }
            int width = MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, MeasureSpec.getSize(heightSpec) - keyboardHeight);
            int spare = available - (int) ((long) width * guestHeight / guestWidth);
            // When space is tight, share it rather than overlap the guest or the keyboard.
            int mapHeight = Math.min(width, Math.max(spare, available / 3));
            ((LayoutParams) map.getLayoutParams()).height = Math.min(mapHeight, available / 2);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
