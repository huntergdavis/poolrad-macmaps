package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Small opaque key over the guest, with a full 48dp target that consumes its own taps. */
public final class EnterOverlayButton extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public EnterOverlayButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(false); // Hardware keys keep going to the guest.
        setContentDescription("Enter");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = 26*density;
        RectF box = new RectF((getWidth()-size)/2, (getHeight()-size)/2,
                (getWidth()+size)/2, (getHeight()+size)/2);
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(isPressed() ? Color.LTGRAY : Color.WHITE);
        canvas.drawRoundRect(box, 4*density, 4*density, ink);
        ink.setStyle(Paint.Style.STROKE); ink.setColor(Color.BLACK); ink.setStrokeWidth(density);
        canvas.drawRoundRect(box, 4*density, 4*density, ink);
        float inset = size*.28f, l = box.left+inset, r = box.right-inset;
        float t = box.top+inset, b = box.bottom-inset, head = size*.16f;
        ink.setStrokeWidth(Math.max(1, 1.4f*density));
        canvas.drawLine(r,t,r,b,ink); canvas.drawLine(r,b,l,b,ink);
        canvas.drawLine(l,b,l+head,b-head,ink); canvas.drawLine(l,b,l+head,b+head,ink);
    }

    @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
    }
}
