package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import name.osher.gil.minivmac.mapper.PoolRadState;

/** Static black-on-white cartography: no animation, blink, or network access. */
public final class LiveMapView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float density;
    private PoolRadState state;
    private boolean positionAvailable;

    public LiveMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.WHITE);
        setClickable(true); // Consume map taps: never send them to the guest or fullscreen gesture.
        setFocusable(false); // Hardware Return/arrows belong to the guest, not this read-only view.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Area map. Waiting for a supported Pool of Radiance party.");
    }

    public void showSample(byte[] sample) {
        PoolRadState next = PoolRadState.parse(sample);
        if (next == null) {
            if (!positionAvailable) return;
            positionAvailable = false;
            setContentDescription("Last area map. Position unavailable; party arrow hidden.");
        } else {
            if (positionAvailable && state != null && state.sameDisplay(next)) return;
            state = next;
            positionAvailable = true;
            setContentDescription("Area map. Party at " + next.positionLabel() + ". North is up.");
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(14 * density);
        ink.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("AREA MAP", 12 * density, 22 * density, ink);
        ink.setTextAlign(Paint.Align.RIGHT);
        String status = state == null ? "Waiting for party"
                : positionAvailable ? state.positionLabel() : "Last area · position unavailable";
        canvas.drawText(status, getWidth() - 12 * density, 22 * density, ink);
        ink.setStrokeWidth(density);
        canvas.drawLine(0, getHeight() - density, getWidth(), getHeight() - density, ink);
        if (state == null) {
            ink.setTextAlign(Paint.Align.CENTER);
            ink.setTextSize(13 * density);
            canvas.drawText("Load a party in Pool of Radiance v1.1", getWidth() / 2f,
                    Math.max(48 * density, getHeight() / 2f), ink);
            return;
        }
        float top = 42 * density, bottom = 20 * density;
        float cell = Math.min((getWidth() - 48 * density) / 16f,
                (getHeight() - top - bottom) / 16f);
        if (cell < 3) return;
        float left = (getWidth() - cell * 16) / 2;
        ink.setTextSize(Math.min(11 * density, cell * .7f));
        ink.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 16; i += 4) {
            canvas.drawText(Integer.toString(i), left + (i + .5f) * cell, top - 5 * density, ink);
            canvas.drawText(Integer.toString(i), left - 11 * density, top + (i + .7f) * cell, ink);
        }
        for (int y = 0; y <= 16; y++) for (int x = 0; x <= 16; x++)
            canvas.drawCircle(left + x * cell, top + y * cell, .65f * density, ink);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            for (int direction = 0; direction < 4; direction++) {
                if (state.map.wall(x, y, direction) == 0 && state.map.door(x, y, direction) == 0) continue;
                float x1 = left + x * cell, y1 = top + y * cell;
                float x2 = x1, y2 = y1;
                if (direction == 0 || direction == 2) { if (direction == 2) y1 += cell; x2 += cell; y2 = y1; }
                else { if (direction == 1) x1 += cell; y2 += cell; x2 = x1; }
                ink.setColor(Color.BLACK);
                ink.setStrokeWidth(Math.max(1.5f * density, cell * .07f));
                canvas.drawLine(x1, y1, x2, y2, ink);
            }
        }
        // Draw door openings after all walls so the adjacent cell cannot paint over them.
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int d = 0; d < 4; d++) {
            if (state.map.door(x, y, d) == 0) continue;
            float cx = left + (x + .5f) * cell, cy = top + (y + .5f) * cell;
            float dx = (d == 1 ? .5f : d == 3 ? -.5f : 0) * cell;
            float dy = (d == 2 ? .5f : d == 0 ? -.5f : 0) * cell;
            float hw = (d % 2 == 0 ? .24f : .09f) * cell, hh = (d % 2 == 0 ? .09f : .24f) * cell;
            ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.FILL);
            canvas.drawRect(cx + dx - hw, cy + dy - hh, cx + dx + hw, cy + dy + hh, ink);
            ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
            canvas.drawRect(cx + dx - hw, cy + dy - hh, cx + dx + hw, cy + dy + hh, ink);
        }
        if (positionAvailable) {
            canvas.save();
            canvas.translate(left + (state.x + .5f) * cell, top + (state.y + .5f) * cell);
            canvas.rotate(state.facing * 90);
            arrow.reset(); arrow.moveTo(0, -cell * .42f); arrow.lineTo(cell * .32f, cell * .32f);
            arrow.lineTo(0, cell * .15f); arrow.lineTo(-cell * .32f, cell * .32f); arrow.close();
            ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(3 * density);
            canvas.drawPath(arrow, ink);
            ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.FILL); canvas.drawPath(arrow, ink);
            canvas.restore();
        }
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(10 * density); ink.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("North up · outlined gaps are doors", getWidth() / 2f, getHeight() - 5 * density, ink);
    }
}
