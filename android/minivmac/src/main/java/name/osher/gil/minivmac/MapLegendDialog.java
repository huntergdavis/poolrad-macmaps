package name.osher.gil.minivmac;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * An offline legend for every mark the companion draws, because they have piled
 * up -- the party arrow, the door chevron, the struck-through fight, note flags,
 * footprints and fog on the map; circles, squares and crosses in a fight; and
 * the Q, T, R, W letters on the party rows -- and e-ink has no colour to lean
 * on. Each row draws the actual mark beside what it means, so the key looks like
 * the thing it explains rather than describing it in words alone.
 *
 * Reference only: it reads nothing from the game and changes nothing.
 */
public final class MapLegendDialog {
    private MapLegendDialog() { }

    /** The marks, in the order they are met: the map first, then a fight, then the party rows. */
    enum Mark {
        PARTY, EXIT, FIGHT, NOTE, FOOTPRINTS, FOG,          // on the map
        FOE, ALLY, DOWN, DEAD, ACTING_RING,                 // on the battle grid
        QUICK, TRAIN, REST, SLOWED, ACTING_BAR, PICKED      // on the party rows
    }

    public static void show(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        int pad = dp(activity, 12);
        list.setPadding(pad, dp(activity, 4), pad, dp(activity, 12));

        heading(activity, list, "On the map");
        row(activity, list, Mark.PARTY, "Party", "Where your party stands and the way it faces.");
        row(activity, list, Mark.EXIT, "Unwalked exit", "A door you have mapped but not yet walked through.");
        row(activity, list, Mark.FIGHT, "Fight here", "A square where a fight began. It does not say how many times.");
        row(activity, list, Mark.NOTE, "Your note", "A page you left on this square. Tap it to open the note.");
        row(activity, list, Mark.FOOTPRINTS, "Footprints", "The path your party has walked. The left button on the map turns it off.");
        row(activity, list, Mark.FOG, "Fog of war", "Squares you have not seen stay hidden. The other map button turns it off.");

        heading(activity, list, "In a fight");
        row(activity, list, Mark.ALLY, "Your character", "One of your party, still standing.");
        row(activity, list, Mark.FOE, "Enemy", "A foe on the battle grid.");
        row(activity, list, Mark.DOWN, "Down, savable", "One of yours is down but can still be reached and revived.");
        row(activity, list, Mark.DEAD, "Dead or petrified", "Past bandaging. A different shape, not a heavier cross.");
        row(activity, list, Mark.ACTING_RING, "Acting now", "The ring marks whose turn it is on the grid.");

        heading(activity, list, "On the party rows");
        row(activity, list, Mark.QUICK, "Q — quick", "The quick flag: filled when on, ? when it cannot be read yet.");
        row(activity, list, Mark.TRAIN, "T — can train", "The game would let this character level up. Not advice, just that it is allowed.");
        row(activity, list, Mark.REST, "R — awaiting rest", "Spells are memorised and waiting on rest.");
        row(activity, list, Mark.SLOWED, "W — slowed", "Carrying too much to keep up with the party.");
        row(activity, list, Mark.ACTING_BAR, "Acting", "In a fight, the bar down a row's edge is whose turn it is.");
        row(activity, list, Mark.PICKED, "Pointed at", "The row you just tapped on the battle grid, held for a moment.");

        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        UpperHalfReferenceDialog.show(activity, "Map legend", scroll);
    }

    private static void heading(Context c, LinearLayout list, String text) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextColor(Color.BLACK);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextSize(15);
        view.setPadding(0, dp(c, 12), 0, dp(c, 4));
        list.addView(view);
    }

    private static void row(Context c, LinearLayout list, Mark mark, String title, String detail) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 6), 0, dp(c, 6));

        Glyph glyph = new Glyph(c, mark);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(c, 12), 0, 0, 0);
        TextView name = new TextView(c);
        name.setText(title);
        name.setTextColor(Color.BLACK);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextSize(14);
        TextView body = new TextView(c);
        body.setText(detail);
        body.setTextColor(Color.DKGRAY);
        body.setTextSize(13);
        text.addView(name);
        text.addView(body);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        list.addView(row);
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Draws one mark at legend size, matching the shapes the map and rows use. */
    private static final class Glyph extends View {
        private final Mark mark;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Glyph(Context context, Mark mark) {
            super(context);
            this.mark = mark;
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) * 0.30f;
            float d = getResources().getDisplayMetrics().density;
            ink.setColor(Color.BLACK);
            ink.setStrokeCap(Paint.Cap.BUTT);
            ink.setTextAlign(Paint.Align.CENTER);
            switch (mark) {
                case PARTY: {
                    ink.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(cx, cy + r * 1.1f); path.lineTo(cx, cy - r);
                    path.lineTo(cx + r, cy - r * 0.2f); path.lineTo(cx, cy + r * 0.2f);
                    path.close();
                    canvas.drawPath(path, ink);
                    break;
                }
                case EXIT: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(2 * d);
                    path.reset();
                    path.moveTo(cx - r, cy + r); path.lineTo(cx, cy - r); path.lineTo(cx + r, cy + r);
                    canvas.drawPath(path, ink);
                    break;
                }
                case FIGHT: {
                    // Two horns, a head and a diagonal line through it.
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.6f * d);
                    canvas.drawCircle(cx, cy, r, ink);
                    canvas.drawLine(cx - r * 0.8f, cy - r * 0.8f, cx - r * 0.3f, cy - r * 1.3f, ink);
                    canvas.drawLine(cx + r * 0.8f, cy - r * 0.8f, cx + r * 0.3f, cy - r * 1.3f, ink);
                    canvas.drawLine(cx - r * 1.2f, cy + r * 1.2f, cx + r * 1.2f, cy - r * 1.2f, ink);
                    break;
                }
                case NOTE: {
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawRect(cx - 1.2f * d, cy - r, cx + 1.2f * d, cy + r, ink); // pole
                    path.reset();
                    path.moveTo(cx + 1.2f * d, cy - r);
                    path.lineTo(cx + r * 1.4f, cy - r * 0.5f);
                    path.lineTo(cx + 1.2f * d, cy);
                    path.close();
                    canvas.drawPath(path, ink);
                    break;
                }
                case FOOTPRINTS: {
                    ink.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 4; i++) {
                        float px = cx - r + i * (r * 0.7f);
                        float py = (i % 2 == 0) ? cy - r * 0.4f : cy + r * 0.4f;
                        canvas.drawCircle(px, py, 2.2f * d, ink);
                    }
                    break;
                }
                case FOG: {
                    ink.setStyle(Paint.Style.FILL);
                    for (float y = cy - r; y <= cy + r; y += 3 * d)
                        for (float x = cx - r; x <= cx + r; x += 3 * d)
                            canvas.drawCircle(x, y, 0.7f * d, ink);
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1 * d);
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, ink);
                    break;
                }
                case ALLY: {
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, r, ink);
                    break;
                }
                case FOE: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(2 * d);
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, ink);
                    break;
                }
                case DOWN: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(2 * d);
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, ink);
                    canvas.drawLine(cx - r, cy + r, cx + r, cy - r, ink);
                    break;
                }
                case DEAD: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(2 * d);
                    canvas.drawLine(cx, cy - r, cx, cy + r, ink);
                    canvas.drawLine(cx - r * 0.7f, cy - r * 0.3f, cx + r * 0.7f, cy - r * 0.3f, ink);
                    break;
                }
                case ACTING_RING: {
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, r * 0.55f, ink);
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.4f * d);
                    canvas.drawCircle(cx, cy, r * 1.05f, ink);
                    break;
                }
                case QUICK: case TRAIN: case REST: case SLOWED: {
                    String c = mark == Mark.QUICK ? "Q" : mark == Mark.TRAIN ? "T"
                            : mark == Mark.REST ? "R" : "W";
                    if (mark == Mark.QUICK) {
                        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.4f * d);
                        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, ink);
                    }
                    ink.setStyle(Paint.Style.FILL);
                    ink.setTypeface(Typeface.DEFAULT_BOLD);
                    ink.setTextSize(r * 1.7f);
                    Paint.FontMetrics fm = ink.getFontMetrics();
                    canvas.drawText(c, cx, cy - (fm.ascent + fm.descent) / 2f, ink);
                    break;
                }
                case ACTING_BAR: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1 * d);
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, ink); // the row
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawRect(cx - r, cy - r, cx - r + 3 * d, cy + r, ink); // the bar
                    break;
                }
                case PICKED: {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(2 * d);
                    canvas.drawRect(cx - r, cy - r, cx + r, cy + r, ink);
                    break;
                }
            }
        }
    }
}
