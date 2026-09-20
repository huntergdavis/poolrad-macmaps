package name.osher.gil.minivmac;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Compact, touch-only controls in two fixed rows; all remaining space belongs
 * to the paper. Drawing tools on the first row, page actions on the second,
 * and Close & save standing beside both. Nothing scrolls: every control is
 * visible at once, which the owner asked for after the single strip grew a
 * scrollbar (2026-09-20).
 */
public final class NoteEditorLayout extends LinearLayout {
    public final InkSheetView sheet;
    public final TextView heading, status;
    public final Button pen, eraser, undo, redo, symbol, journal, template, fit, delete, close;
    private final LinearLayout header, identity;
    private final LinearLayout tools, drawingRow, pageRow;

    public NoteEditorLayout(Context context, String title) {
        super(context);
        setOrientation(VERTICAL); setBackgroundColor(Color.WHITE);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        setContentDescription("Handwritten note: " + title);
        header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(48));
        identity = new LinearLayout(context); identity.setOrientation(VERTICAL);
        identity.setPadding(dp(3), 0, dp(6), 0);
        heading = label(title, 12); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status = label("Saved locally", 10);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        identity.addView(heading, new LayoutParams(-1, -2));
        identity.addView(status, new LayoutParams(-1, -2));
        header.addView(identity, new LayoutParams(dp(160), -2));

        drawingRow = row(context); pageRow = row(context);
        pen = tool(drawingRow, "Pen"); eraser = tool(drawingRow, "Eraser");
        undo = tool(drawingRow, "Undo"); redo = tool(drawingRow, "Redo");
        symbol = tool(drawingRow, "Symbol");
        journal = tool(pageRow, "Journal"); template = tool(pageRow, "Template");
        fit = tool(pageRow, "Fit page"); delete = tool(pageRow, "Delete…");
        tools = new LinearLayout(context); tools.setOrientation(VERTICAL); tools.setFocusable(false);
        tools.addView(drawingRow, new LayoutParams(-1, dp(ROW_DP)));
        tools.addView(pageRow, new LayoutParams(-1, dp(ROW_DP)));
        header.addView(tools, new LayoutParams(0, -2, 1));
        close = button(header, "Close & save", 88, 2 * ROW_DP);
        close.setContentDescription("Close and save this handwritten note");
        addView(header, new LayoutParams(-1, -2));

        sheet = new InkSheetView(context);
        // Retiring the old preference must not leave finger drawing invisibly disabled.
        sheet.setPenOnly(false);
        addView(sheet, new LayoutParams(-1, 0, 1));
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(getContext()); text.setText(value);
        text.setTextColor(Color.BLACK); text.setTextSize(size);
        text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    /** Each tool row is 48dp: the touch-target height the notes have always promised. */
    public static final int ROW_DP = 48;

    private LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(false); return row;
    }

    /** A row tool shares its row equally with its neighbours; no tool is ever off screen. */
    private Button tool(LinearLayout row, String value) {
        Button button = styled(value);
        row.addView(button, new LayoutParams(0, dp(ROW_DP), 1)); return button;
    }

    private Button button(LinearLayout parent, String value, int width, int heightDp) {
        Button button = styled(value);
        int readableWidth = Math.max(dp(width), (int) Math.ceil(button.getPaint().measureText(value)) + dp(12));
        parent.addView(button, new LayoutParams(readableWidth, dp(heightDp))); return button;
    }

    private Button styled(String value) {
        Button button = new Button(getContext()); button.setText(value); button.setAllCaps(false);
        button.setTextSize(11); button.setSingleLine(true); button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(3), 0, dp(3), 0);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{Color.GRAY, Color.BLACK}));
        button.setFocusable(false); button.setFocusableInTouchMode(false);
        return button;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        // Close never moves. The tools share two fixed rows at every width; only
        // the title column gives way on narrow devices.
        int wanted = width >= dp(640) ? dp(160) : width >= dp(420) ? dp(120)
                : width >= dp(280) ? dp(88) : 0;
        wanted = Math.min(wanted, Math.max(0, width - close.getLayoutParams().width - dp(48)));
        if (wanted < dp(64)) wanted = 0;
        int visibility = wanted == 0 ? GONE : VISIBLE;
        if (identity.getVisibility() != visibility) identity.setVisibility(visibility);
        LayoutParams params = (LayoutParams) identity.getLayoutParams();
        if (params.width != wanted) { params.width = wanted; identity.setLayoutParams(params); }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
