package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Two retained companion pages; tab navigation never replaces or focuses the guest. */
public final class CompanionPane extends LinearLayout {
    public static final String MAP = "map";
    public static final String INFO = "info";
    public static final String CONNECTIONS = "connections";

    public enum Tool { OPTIONS, SAVES, REST, PARTY_ORDER, MESSAGE_LOG, EXPLORATION, LEVELS, SPELLS, EQUIPMENT, MONEY, WHEEL, JOURNAL, LEGEND, NOTE_INDEX }
    public interface OnTabSelectedListener { void onTabSelected(String tab); }
    public interface OnToolSelectedListener { void onToolSelected(Tool tool); }

    private final Button mapTab, infoTab, connectionsTab;
    private final ConnectionsView connections;
    public ConnectionsView connections() { return connections; }
    private final LinearLayout citationNotice;
    private final Button citationOpen;
    private Runnable citationAction;
    private final LiveMapView map;
    private final ScrollView info;
    private String selected = MAP;
    private OnTabSelectedListener tabListener;
    private OnToolSelectedListener toolListener;

    public CompanionPane(Context context) { this(context, null); }
    public CompanionPane(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public CompanionPane(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.WHITE);
        setClickable(true); // Empty companion space also consumes touches, never the guest.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(true);

        LinearLayout tabs = new LinearLayout(context);
        tabs.setId(R.id.companion_tab_row);
        tabs.setOrientation(HORIZONTAL);
        tabs.setFocusable(false);
        addView(tabs, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        mapTab = tab(context, R.id.companion_tab_map, R.string.companion_tab_map, MAP);
        infoTab = tab(context, R.id.companion_tab_info, R.string.companion_tab_info, INFO);
        tabs.addView(mapTab, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        connectionsTab = tab(context, R.id.companion_tab_connections, R.string.companion_tab_connections, CONNECTIONS);
        tabs.addView(connectionsTab, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        tabs.addView(infoTab, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

        citationNotice = new LinearLayout(context);
        citationNotice.setId(R.id.companion_citation_notice);
        citationNotice.setOrientation(HORIZONTAL);
        citationNotice.setVisibility(GONE);
        citationOpen = plainButton(context, R.id.companion_citation_open, R.string.companion_citation_read);
        citationOpen.setMaxLines(2);
        citationOpen.setEllipsize(android.text.TextUtils.TruncateAt.END);
        citationOpen.setOnClickListener(view -> {
            Runnable action = citationAction;
            clearCitationNotice();
            if (action != null) action.run();
        });
        Button dismiss = plainButton(context, R.id.companion_citation_dismiss, R.string.companion_citation_dismiss);
        dismiss.setOnClickListener(view -> clearCitationNotice());
        citationNotice.addView(citationOpen, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        citationNotice.addView(dismiss, new LayoutParams(dp(88), LayoutParams.MATCH_PARENT));
        addView(citationNotice, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(context);
        content.setId(R.id.companion_content);
        content.setFocusable(false);
        content.setClipChildren(true);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        map = new LiveMapView(context, null);
        map.setId(R.id.live_map);
        content.addView(map, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        connections = new ConnectionsView(context);
        connections.setId(R.id.companion_connections);
        content.addView(connections, new FrameLayout.LayoutParams(-1, -1));

        info = new ScrollView(context);
        info.setId(R.id.companion_info);
        info.setBackgroundColor(Color.WHITE);
        info.setFocusable(false);
        info.setFocusableInTouchMode(false);
        info.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        info.setFillViewport(false);
        info.setOverScrollMode(OVER_SCROLL_NEVER);
        info.setSmoothScrollingEnabled(false);
        info.setClipToPadding(true);
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(VERTICAL);
        tools.setPadding(dp(8), dp(6), dp(8), dp(6));
        tools.setFocusable(false);
        addTool(tools, Tool.OPTIONS, R.id.companion_tool_options, R.string.companion_tool_options);
        addTool(tools, Tool.SAVES, R.id.companion_tool_saves, R.string.companion_tool_saves);
        addTool(tools, Tool.REST, R.id.companion_tool_rest, R.string.companion_tool_rest);
        addTool(tools, Tool.PARTY_ORDER, R.id.companion_tool_party_order, R.string.companion_tool_party_order);
        addTool(tools, Tool.MESSAGE_LOG, R.id.companion_tool_message_log, R.string.companion_tool_message_log);
        addTool(tools, Tool.EXPLORATION, R.id.companion_tool_exploration, R.string.companion_tool_exploration);
        addTool(tools, Tool.JOURNAL, R.id.companion_tool_journal, R.string.companion_tool_journal);
        addTool(tools, Tool.NOTE_INDEX, R.id.companion_tool_note_index, R.string.companion_tool_note_index);
        addTool(tools, Tool.LEVELS, R.id.companion_tool_levels, R.string.companion_tool_levels);
        addTool(tools, Tool.SPELLS, R.id.companion_tool_spells, R.string.companion_tool_spells);
        addTool(tools, Tool.EQUIPMENT, R.id.companion_tool_equipment, R.string.companion_tool_equipment);
        addTool(tools, Tool.MONEY, R.id.companion_tool_money, R.string.companion_tool_money);
        addTool(tools, Tool.WHEEL, R.id.companion_tool_wheel, R.string.companion_tool_wheel);
        addTool(tools, Tool.LEGEND, R.id.companion_tool_legend, R.string.companion_tool_legend);
        info.addView(tools, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        content.addView(info, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        updateSelection();
    }

    /** A retained, explicit action inside the companion allocation, never over the guest. */
    public void showCitationNotice(String label, Runnable open) {
        if (label == null || open == null) { clearCitationNotice(); return; }
        citationAction = open;
        citationOpen.setText(label);
        citationOpen.setContentDescription(label);
        citationNotice.setVisibility(VISIBLE);
    }

    public void clearCitationNotice() {
        citationAction = null;
        citationNotice.setVisibility(GONE);
    }

    public String selectedTab() { return selected; }
    public boolean isMapSelected() { return MAP.equals(selected); }

    /** Unknown persisted IDs safely fall back to Map; unchanged selection does not notify again. */
    public void setTab(String tab) {
        String next = INFO.equals(tab) ? INFO : CONNECTIONS.equals(tab) ? CONNECTIONS : MAP;
        if (selected.equals(next)) return;
        selected = next;
        updateSelection();
        if (tabListener != null) tabListener.onTabSelected(selected);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) { tabListener = listener; }
    public void setOnToolSelectedListener(OnToolSelectedListener listener) { toolListener = listener; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Button tab(Context context, int id, int label, String value) {
        Button button = plainButton(context, id, label);
        button.setSingleLine(true);
        button.setContentDescription(context.getString(R.string.companion_tab_accessibility, context.getString(label)));
        button.setOnClickListener(view -> setTab(value));
        return button;
    }

    private Button plainButton(Context context, int id, int label) {
        Button button = new Button(context);
        button.setId(id);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setTextColor(Color.BLACK);
        button.setStateListAnimator(null);
        // Plain state drawables: no ripple/animation and no theme-dependent low-contrast tint.
        button.setBackgroundTintList(null);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_selected}, fill(Color.BLACK));
        background.addState(new int[]{android.R.attr.state_pressed}, fill(0xffdddddd));
        background.addState(new int[]{}, fill(Color.WHITE));
        button.setBackground(background);
        return button;
    }

    private GradientDrawable fill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(Math.max(1, dp(1)), 0xff555555);
        return drawable;
    }

    private void addTool(LinearLayout parent, Tool tool, int id, int label) {
        Button button = plainButton(getContext(), id, label);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(view -> {
            if (toolListener != null) toolListener.onToolSelected(tool);
        });
        LayoutParams layout = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        layout.topMargin = dp(2);
        layout.bottomMargin = dp(2);
        parent.addView(button, layout);
    }

    private void updateSelection() {
        boolean mapSelected = isMapSelected();
        mapTab.setSelected(mapSelected);
        infoTab.setSelected(INFO.equals(selected));
        connectionsTab.setSelected(CONNECTIONS.equals(selected));
        connectionsTab.setTextColor(CONNECTIONS.equals(selected) ? Color.WHITE : Color.BLACK);
        mapTab.setTextColor(mapSelected ? Color.WHITE : Color.BLACK);
        infoTab.setTextColor(INFO.equals(selected) ? Color.WHITE : Color.BLACK);
        map.setVisibility(mapSelected ? View.VISIBLE : View.GONE);
        info.setVisibility(INFO.equals(selected) ? View.VISIBLE : View.GONE);
        connections.setVisibility(CONNECTIONS.equals(selected) ? View.VISIBLE : View.GONE);
    }
}
