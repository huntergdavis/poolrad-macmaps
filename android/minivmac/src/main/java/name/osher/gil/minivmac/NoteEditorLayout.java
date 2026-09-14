package name.osher.gil.minivmac;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One compact, touch-only control strip; all remaining space belongs to the paper. */
public final class NoteEditorLayout extends LinearLayout {
    public final InkSheetView sheet;
    public final TextView heading, status;
    public final Button pen, eraser, undo, redo, symbol, fit, delete, close;
    private final LinearLayout header, identity;
    private final HorizontalScrollView tools;

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

        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        pen = button(row, "Pen", 48); eraser = button(row, "Eraser", 56);
        undo = button(row, "Undo", 48); redo = button(row, "Redo", 48);
        symbol = button(row, "Symbol", 72); fit = button(row, "Fit page", 64);
        delete = button(row, "Delete…", 60);
        tools = new HorizontalScrollView(context); tools.setFillViewport(false);
        tools.setHorizontalScrollBarEnabled(true); tools.setScrollbarFadingEnabled(false);
        tools.setOverScrollMode(OVER_SCROLL_NEVER); tools.setFocusable(false);
        tools.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        header.addView(tools, new LayoutParams(0, dp(48), 1));
        close = button(header, "Close & save", 88);
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

    private Button button(LinearLayout parent, String value, int width) {
        Button button = new Button(getContext()); button.setText(value); button.setAllCaps(false);
        button.setTextSize(11); button.setSingleLine(true); button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(3), 0, dp(3), 0);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{Color.GRAY, Color.BLACK}));
        button.setFocusable(false); button.setFocusableInTouchMode(false);
        int readableWidth = Math.max(dp(width), (int) Math.ceil(button.getPaint().measureText(value)) + dp(12));
        parent.addView(button, new LayoutParams(readableWidth, dp(48))); return button;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        // Close never scrolls away. Narrow devices scroll the same tools instead
        // of acquiring a second toolbar, menu or a smaller drawing allocation.
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
