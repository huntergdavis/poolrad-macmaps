package name.osher.gil.minivmac;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import name.osher.gil.minivmac.mapper.*;
import name.osher.gil.minivmac.notebook.AreaConnections;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/**
 * The World tab: every area the party has discovered, drawn as one map the
 * player can drag and pinch. Border crossings stitch districts edge to edge;
 * stairs, boats and teleports leave islands joined by dotted links between the
 * exact squares. A chip row names each place and animates the board to it.
 * Built only from recorded travel and remembered exploration, never game data.
 */
public final class WorldView extends LinearLayout {
    /** What the notebook remembers of one area: explored geometry and walked squares. */
    public static final class Sheet {
        public final GeoMap map; public final ExplorationTrail trail;
        public Sheet(GeoMap map, ExplorationTrail trail) { this.map = map; this.trail = trail; }
    }
    public static final String FOCUS_ALL = "all", FOCUS_HERE = "here", FOCUS_PLACE = "place:";
    public static final int ROW_DP = 48;

    private final TextView heading, footer;
    private final HorizontalScrollView chipScroll;
    private final LinearLayout chips;
    private final Button chipAll, chipHere, zoomOut, zoomIn, fit;
    private final Board board;
    private final float density;
    private final MapArtwork artwork = new MapArtwork();

    private String book = "Notebook loading", failure = "";
    private AreaConnections history = new AreaConnections();
    private WorldLayout layout = WorldLayout.EMPTY;
    private int here = -1, selected = -1;
    private PoolRadState position;
    private Map<Integer, Sheet> sheets = Collections.emptyMap();
    private String focus = FOCUS_ALL;
    private final List<Button> placeChips = new ArrayList<>();
    private List<String> chipNames = Collections.emptyList();

    public WorldView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL); setBackgroundColor(Color.WHITE);
        setFocusable(false); setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        heading = text(); heading.setPadding(dp(12), dp(6), dp(12), dp(2));
        addView(heading, new LayoutParams(-1, -2));
        chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false); chipScroll.setFocusable(false);
        chipScroll.setId(R.id.companion_world_chips);
        chips = new LinearLayout(context); chips.setOrientation(HORIZONTAL); chips.setPadding(dp(8), 0, dp(8), 0);
        chipScroll.addView(chips, new FrameLayout.LayoutParams(-2, -1));
        addView(chipScroll, new LayoutParams(-1, dp(ROW_DP)));
        chipAll = chip("Whole world", R.id.companion_world_chip_all); chipAll.setOnClickListener(v -> focusAll());
        chipHere = chip("Current area", R.id.companion_world_chip_here); chipHere.setOnClickListener(v -> focusHere());
        board = new Board(context); board.setId(R.id.companion_world_board);
        addView(board, new LayoutParams(-1, 0, 1));
        LinearLayout bottom = new LinearLayout(context); bottom.setOrientation(HORIZONTAL); bottom.setPadding(dp(8), dp(2), dp(8), dp(4));
        zoomOut = control("−", "Zoom world out", R.id.companion_world_zoom_out); zoomOut.setOnClickListener(v -> board.zoomBy(1 / 1.5f));
        zoomIn = control("+", "Zoom world in", R.id.companion_world_zoom_in); zoomIn.setOnClickListener(v -> board.zoomBy(1.5f));
        fit = control("Fit", "Show the whole world", R.id.companion_world_fit); fit.setOnClickListener(v -> focusAll());
        bottom.addView(zoomOut, new LayoutParams(dp(ROW_DP), dp(ROW_DP)));
        bottom.addView(zoomIn, new LayoutParams(dp(ROW_DP), dp(ROW_DP)));
        bottom.addView(fit, new LayoutParams(dp(56), dp(ROW_DP)));
        footer = text(); footer.setId(R.id.companion_world_footer); footer.setPadding(dp(8), 0, 0, 0); footer.setMaxLines(4);
        footer.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(footer, new LayoutParams(0, -2, 1));
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        addView(bottom, new LayoutParams(-1, -2));
        render();
    }

    private int dp(int value) { return Math.round(value * density); }
    private TextView text() { TextView view = new TextView(getContext()); view.setTextColor(Color.BLACK); view.setTextSize(14); return view; }
    private Button chip(String label, int id) {
        Button button = plain(label, id); button.setTextSize(14);
        button.setPadding(dp(14), 0, dp(14), 0);
        LayoutParams params = new LayoutParams(-2, dp(ROW_DP) - dp(8)); params.setMargins(dp(4), dp(4), dp(4), dp(4));
        chips.addView(button, params);
        return button;
    }
    private Button control(String label, String description, int id) {
        Button button = plain(label, id); button.setContentDescription(description); button.setTextSize(18);
        button.setPadding(0, 0, 0, 0); return button;
    }
    private Button plain(String label, int id) {
        Button button = new Button(getContext()); button.setId(id); button.setText(label); button.setAllCaps(false);
        button.setSingleLine(true); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setFocusable(false); button.setFocusableInTouchMode(false); button.setTextColor(Color.BLACK);
        button.setStateListAnimator(null); button.setBackgroundTintList(null);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_selected}, fill(Color.BLACK));
        background.addState(new int[]{android.R.attr.state_pressed}, fill(0xffdddddd));
        background.addState(new int[]{}, fill(Color.WHITE));
        button.setBackground(background);
        return button;
    }
    private GradientDrawable fill(int color) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color);
        drawable.setCornerRadius(dp(6)); drawable.setStroke(Math.max(1, dp(1)), 0xff555555); return drawable;
    }

    /** New data from the notebook. Cheap when nothing changed; the board only re-fits when the world grew. */
    public void show(String book, AreaConnections history, int here, PoolRadState position, Map<Integer, Sheet> sheets, String failure) {
        boolean bookChanged = !this.book.equals(book);
        boolean worldChanged = bookChanged || this.history != history || this.here != here;
        boolean sheetsChanged = this.sheets != sheets;
        boolean moved = position != this.position && (position == null || this.position == null
                || position.x != this.position.x || position.y != this.position.y || position.facing != this.position.facing);
        boolean failed = !this.failure.equals(failure);
        if (!worldChanged && !sheetsChanged && !moved && !failed) return;
        this.book = book; this.history = history; this.here = here; this.position = position; this.sheets = sheets; this.failure = failure;
        if (bookChanged) { selected = -1; focus = FOCUS_ALL; }
        if (worldChanged) {
            WorldLayout before = layout;
            layout = WorldLayout.build(history, here);
            if (selected >= 0 && layout.find(selected) == null) selected = -1;
            if (focus.startsWith(FOCUS_PLACE) && placeIndex() >= layout.places.size()) focus = FOCUS_ALL;
            render();
            boolean grew = before.areaCount() != layout.areaCount() || before.width != layout.width || before.height != layout.height;
            if (grew || bookChanged) board.applyFocus(false);
            return;
        }
        render(); board.invalidate();
    }

    public WorldLayout layout() { return layout; }
    public int selectedArea() { return selected; }
    public String focus() { return focus; }
    public View board() { return board; }
    private int placeIndex() { try { return Integer.parseInt(focus.substring(FOCUS_PLACE.length())); } catch (RuntimeException invalid) { return -1; } }

    public void focusAll() { focus = FOCUS_ALL; render(); board.applyFocus(true); }
    public void focusHere() { if (here < 0 || layout.find(here) == null) return; focus = FOCUS_HERE; selected = here; render(); board.applyFocus(true); }
    public void focusPlace(int index) { if (index < 0 || index >= layout.places.size()) return; focus = FOCUS_PLACE + index; render(); board.applyFocus(true); }
    /** Select an area on the board; its name and crossings go to the footer. */
    public void selectArea(int area) { if (layout.find(area) == null) return; selected = area; render(); board.invalidate(); }

    private void render() {
        heading.setText(book + " · World");
        List<String> names = new ArrayList<>();
        for (WorldLayout.Place place : layout.places) names.add(place.name + (place.separate ? "\u0000" : ""));
        if (!names.equals(chipNames)) {
            chipNames = names;
            for (Button chip : placeChips) chips.removeView(chip);
            placeChips.clear();
            for (int i = 0; i < layout.places.size(); i++) {
                final int index = i; WorldLayout.Place place = layout.places.get(i);
                Button chip = chip(place.name, View.NO_ID);
                chip.setContentDescription("Show " + place.name + (place.separate ? ", no crossing recorded to the party's part of the world" : ""));
                chip.setOnClickListener(v -> focusPlace(index));
                placeChips.add(chip);
            }
        }
        for (int i = 0; i < placeChips.size(); i++) {
            Button chip = placeChips.get(i);
            chip.setSelected(focus.equals(FOCUS_PLACE + i));
            chip.setTextColor(chip.isSelected() ? Color.WHITE : Color.BLACK);
        }
        chipHere.setEnabled(here >= 0 && layout.find(here) != null);
        chipAll.setSelected(FOCUS_ALL.equals(focus)); chipAll.setTextColor(chipAll.isSelected() ? Color.WHITE : Color.BLACK);
        chipHere.setSelected(FOCUS_HERE.equals(focus)); chipHere.setTextColor(chipHere.isSelected() ? Color.WHITE : chipHere.isEnabled() ? Color.BLACK : 0xff888888);
        chipScroll.setVisibility(layout.isEmpty() ? GONE : VISIBLE);
        zoomOut.setEnabled(!layout.isEmpty()); zoomIn.setEnabled(!layout.isEmpty()); fit.setEnabled(!layout.isEmpty());
        footer.setText(footerText());
        board.setContentDescription(boardDescription());
    }

    private String footerText() {
        if (!failure.isEmpty()) return failure;
        if (layout.isEmpty()) return "No areas discovered yet. Travel between areas to build the world.";
        if (selected < 0) return "Drag to look around, pinch or use − and + to zoom. Tap an area for its crossings.";
        StringBuilder text = new StringBuilder(WorldLayout.label(selected));
        if (selected == here) text.append(" · you are here");
        Sheet sheet = sheets.get(selected);
        if (sheet != null) text.append(" · ").append(sheet.trail.visitedCount()).append(" of 256 squares walked");
        List<String> crossings = crossings(selected);
        if (crossings.isEmpty()) text.append("\nNo crossings recorded here yet.");
        else { text.append("\nCrossings: "); for (int i = 0; i < crossings.size(); i++) { if (i > 0) text.append(" · "); text.append(crossings.get(i)); } }
        return text.toString();
    }
    private static String square(int tile) { return tile % 16 + "," + tile / 16; }
    private List<String> crossings(int area) {
        List<String> found = new ArrayList<>();
        for (AreaConnections.Edge e : history.edges) if (e.fromArea == area || e.toArea == area)
            found.add(WorldLayout.label(e.fromArea) + " " + square(e.fromTile) + " → " + WorldLayout.label(e.toArea) + " " + square(e.toTile));
        return found;
    }
    private String boardDescription() {
        if (layout.isEmpty()) return "World map. Nothing discovered yet.";
        StringBuilder text = new StringBuilder("World map with " + layout.areaCount() + " areas in " + layout.places.size() + " places: ");
        for (int i = 0; i < layout.places.size(); i++) { if (i > 0) text.append("; "); text.append(layout.places.get(i).name); if (layout.places.get(i).separate) text.append(" (separate)"); }
        text.append(". ").append(footerText().replace('\n', ' '));
        return text.toString();
    }

    /** The pannable, zoomable canvas. Scale is pixels per tile. */
    private final class Board extends View {
        private static final int SELECT_ACTION = 0x04000000;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private float scale = 0, offsetX, offsetY;
        private boolean fitted;
        private ValueAnimator animator;
        private final ScaleGestureDetector pinch;
        private final GestureDetector gestures;
        Board(Context context) {
            super(context); setClickable(true); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) { zoomAt(d.getScaleFactor(), d.getFocusX(), d.getFocusY()); return true; }
            });
            gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { stopAnimation(); return true; }
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) { offsetX -= dx; offsetY -= dy; clampOffset(); invalidate(); return true; }
                @Override public boolean onSingleTapConfirmed(MotionEvent e) { int area = areaAt(e.getX(), e.getY()); if (area >= 0) { performClick(); selectArea(area); } return true; }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    int area = areaAt(e.getX(), e.getY());
                    if (area >= 0) { selected = area; render(); animateTo(fitFor(bounds(layout.find(area)), dp(24)), true); }
                    else animateTo(fitFor(new RectF(0, 0, layout.width, layout.height), dp(12)), true);
                    return true;
                }
            });
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { super.onSizeChanged(w, h, oldW, oldH); applyFocus(false); }

        private RectF bounds(WorldLayout.Placed p) { return new RectF(p.x, p.y - WorldLayout.LABEL, p.x + WorldLayout.SIDE, p.y + WorldLayout.SIDE); }
        private RectF bounds(WorldLayout.Place p) { return new RectF(p.left, p.top, p.right, p.bottom); }
        private float minScale() { return Math.max(1 * density, Math.min(getWidth() / Math.max(1f, layout.width), getHeight() / Math.max(1f, layout.height)) * .5f); }
        private float maxScale() { return dp(48); }
        /** Scale and offset that show the tile rectangle centred with a pixel margin. */
        private float[] fitFor(RectF tiles, float margin) {
            float w = Math.max(1, getWidth() - 2 * margin), h = Math.max(1, getHeight() - 2 * margin);
            float s = Math.min(w / Math.max(1f, tiles.width()), h / Math.max(1f, tiles.height()));
            s = Math.max(minScale(), Math.min(maxScale(), s));
            return new float[]{s, getWidth() / 2f - tiles.centerX() * s, getHeight() / 2f - tiles.centerY() * s};
        }
        void applyFocus(boolean animate) {
            if (getWidth() == 0 || getHeight() == 0) return;
            if (layout.isEmpty()) { scale = 0; invalidate(); return; }
            float[] target;
            if (FOCUS_HERE.equals(focus) && layout.find(here) != null) target = fitFor(bounds(layout.find(here)), dp(24));
            else if (focus.startsWith(FOCUS_PLACE) && placeIndex() >= 0 && placeIndex() < layout.places.size()) target = fitFor(bounds(layout.places.get(placeIndex())), dp(16));
            else target = fitFor(new RectF(0, 0, layout.width, layout.height), dp(12));
            animateTo(target, animate && fitted);
            fitted = true;
        }
        private void stopAnimation() { if (animator != null) { animator.cancel(); animator = null; } }
        private void animateTo(float[] target, boolean animate) {
            stopAnimation();
            if (!animate || scale <= 0) { scale = target[0]; offsetX = target[1]; offsetY = target[2]; invalidate(); return; }
            final float s0 = scale, x0 = offsetX, y0 = offsetY;
            animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(260);
            animator.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                scale = s0 + (target[0] - s0) * t; offsetX = x0 + (target[1] - x0) * t; offsetY = y0 + (target[2] - y0) * t; invalidate();
            });
            animator.start();
        }
        void zoomBy(float factor) { zoomAt(factor, getWidth() / 2f, getHeight() / 2f); }
        private void zoomAt(float factor, float px, float py) {
            if (layout.isEmpty() || scale <= 0) return;
            stopAnimation();
            float next = Math.max(minScale(), Math.min(maxScale(), scale * factor)); factor = next / scale;
            offsetX = px - (px - offsetX) * factor; offsetY = py - (py - offsetY) * factor; scale = next;
            clampOffset(); invalidate();
        }
        /** The world may leave the view, but never entirely. */
        private void clampOffset() {
            float w = layout.width * scale, h = layout.height * scale, keep = dp(48);
            offsetX = Math.max(keep - w, Math.min(getWidth() - keep, offsetX));
            offsetY = Math.max(keep - h, Math.min(getHeight() - keep, offsetY));
        }
        private int areaAt(float px, float py) {
            if (scale <= 0) return -1;
            float tx = (px - offsetX) / scale, ty = (py - offsetY) / scale;
            for (WorldLayout.Place p : layout.places) for (WorldLayout.Placed a : p.areas)
                if (tx >= a.x && tx < a.x + WorldLayout.SIDE && ty >= a.y - WorldLayout.LABEL && ty < a.y + WorldLayout.SIDE) return a.area;
            return -1;
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            pinch.onTouchEvent(event);
            if (!pinch.isInProgress()) gestures.onTouchEvent(event);
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            if (layout.isEmpty() || scale <= 0) {
                ink.setColor(0xff666666); ink.setStyle(Paint.Style.FILL); ink.setTextSize(dp(14)); ink.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(layout.isEmpty() ? "Nothing discovered yet" : "", getWidth() / 2f, getHeight() / 2f, ink);
                return;
            }
            float cell = scale;
            boolean captioned = false;
            for (WorldLayout.Place place : layout.places) {
                if (place.separate && !captioned) {
                    captioned = true;
                    ink.setColor(0xff666666); ink.setStyle(Paint.Style.FILL); ink.setTextSize(dp(11)); ink.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText("No crossing recorded between these and the rest", offsetX + place.left * cell, offsetY + place.top * cell - dp(4), ink);
                }
                for (WorldLayout.Placed a : place.areas) drawArea(canvas, a, cell);
            }
            for (AreaConnections.Edge link : layout.links) drawLink(canvas, link, cell);
            drawParty(canvas, cell);
            for (WorldLayout.Place place : layout.places) for (WorldLayout.Placed a : place.areas) drawLabel(canvas, a, cell);
        }
        private void drawArea(Canvas canvas, WorldLayout.Placed a, float cell) {
            float left = offsetX + a.x * cell, top = offsetY + a.y * cell, side = WorldLayout.SIDE * cell;
            if (left > getWidth() || top > getHeight() || left + side < 0 || top + side < 0) return;
            ink.setStyle(Paint.Style.FILL); ink.setColor(a.area == selected ? 0xffeeeeee : Color.WHITE);
            canvas.drawRect(left, top, left + side, top + side, ink);
            Sheet sheet = sheets.get(a.area);
            if (sheet != null) {
                ink.setColor(0xfff6f6f6);
                for (int tile = 0; tile < 256; tile++) if (sheet.trail.visited(tile))
                    canvas.drawRect(left + tile % 16 * cell, top + tile / 16 * cell, left + (tile % 16 + 1) * cell, top + (tile / 16 + 1) * cell, ink);
                if (cell >= 3 * density) artwork.drawGeometry(canvas, sheet.map, sheet.trail::visited, left, top, cell, density);
                else {
                    ink.setColor(Color.BLACK);
                    for (int tile = 0; tile < 256; tile++) if (sheet.trail.visited(tile))
                        canvas.drawRect(left + tile % 16 * cell, top + tile / 16 * cell, left + (tile % 16 + 1) * cell, top + (tile / 16 + 1) * cell, ink);
                }
            }
            ink.setStyle(Paint.Style.STROKE); ink.setColor(a.area == here ? Color.BLACK : 0xff999999);
            ink.setStrokeWidth(a.area == here ? dp(2) : a.area == selected ? dp(2) : Math.max(1, dp(1)));
            canvas.drawRect(left, top, left + side, top + side, ink);
        }
        private void drawLabel(Canvas canvas, WorldLayout.Placed a, float cell) {
            float left = offsetX + a.x * cell, top = offsetY + a.y * cell, side = WorldLayout.SIDE * cell;
            if (left > getWidth() || top > getHeight() || left + side < 0 || top + side < 0) return;
            String name = WorldLayout.label(a.area) + (a.area == here ? " · You are here" : "");
            ink.setStyle(Paint.Style.FILL); ink.setTextAlign(Paint.Align.CENTER);
            float size = Math.min(dp(13), Math.max(dp(8), side / 8));
            ink.setTextSize(size);
            float available = Math.max(side - dp(4), dp(40));
            String first = name, second = "";
            if (ink.measureText(name) > available) {
                int split = name.lastIndexOf(' ', name.length() / 2 + 5);
                if (split > 0) { first = name.substring(0, split); second = name.substring(split + 1); }
            }
            float widest = Math.max(ink.measureText(first), Math.max(1, ink.measureText(second)));
            if (widest > available) ink.setTextSize(Math.max(dp(7), size * available / widest));
            float lineHeight = ink.getTextSize() * 1.15f;
            float baseline = top - dp(3) - (second.isEmpty() ? 0 : lineHeight);
            float boxTop = baseline - lineHeight, boxBottom = top - dp(1);
            ink.setColor(0xddffffff);
            canvas.drawRect(left + side / 2 - widest / 2 - dp(3), boxTop, left + side / 2 + widest / 2 + dp(3), boxBottom, ink);
            ink.setColor(Color.BLACK);
            canvas.drawText(first, left + side / 2, baseline, ink);
            if (!second.isEmpty()) canvas.drawText(second, left + side / 2, baseline + lineHeight, ink);
        }
        private void drawLink(Canvas canvas, AreaConnections.Edge link, float cell) {
            WorldLayout.Placed from = layout.find(link.fromArea), to = layout.find(link.toArea);
            if (from == null || to == null) return;
            float x1 = offsetX + (from.x + link.fromTile % 16 + .5f) * cell, y1 = offsetY + (from.y + link.fromTile / 16 + .5f) * cell;
            float x2 = offsetX + (to.x + link.toTile % 16 + .5f) * cell, y2 = offsetY + (to.y + link.toTile / 16 + .5f) * cell;
            ink.setStyle(Paint.Style.STROKE); ink.setColor(Color.BLACK); ink.setStrokeWidth(dp(2));
            ink.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
            canvas.drawLine(x1, y1, x2, y2, ink);
            ink.setPathEffect(null);
            arrowHead(canvas, x1, y1, x2, y2);
            ink.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x1, y1, Math.max(dp(3), cell * .3f), ink);
        }
        private void arrowHead(Canvas canvas, float x1, float y1, float x2, float y2) {
            double angle = Math.atan2(y2 - y1, x2 - x1); float length = dp(10);
            arrow.reset(); arrow.moveTo(x2, y2);
            arrow.lineTo(x2 - length * (float) Math.cos(angle - .5), y2 - length * (float) Math.sin(angle - .5));
            arrow.lineTo(x2 - length * (float) Math.cos(angle + .5), y2 - length * (float) Math.sin(angle + .5));
            arrow.close(); ink.setStyle(Paint.Style.FILL); canvas.drawPath(arrow, ink);
        }
        private void drawParty(Canvas canvas, float cell) {
            if (position == null || here < 0) return;
            WorldLayout.Placed at = layout.find(here);
            if (at == null || position.x < 0 || position.x > 15 || position.y < 0 || position.y > 15) return;
            float cx = offsetX + (at.x + position.x + .5f) * cell, cy = offsetY + (at.y + position.y + .5f) * cell;
            float r = Math.max(dp(5), cell * .45f);
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.WHITE); canvas.drawCircle(cx, cy, r + dp(2), ink);
            ink.setColor(Color.BLACK); canvas.drawCircle(cx, cy, r, ink);
            // Facing: north, east, south, west.
            double angle = Math.toRadians(-90 + 90 * (position.facing & 3));
            ink.setColor(Color.WHITE); ink.setStrokeWidth(dp(2)); ink.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx, cy, cx + (float) Math.cos(angle) * r * .8f, cy + (float) Math.sin(angle) * r * .8f, ink);
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (layout.isEmpty()) return;
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION - 1, "Zoom world out"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION - 2, "Zoom world in"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION - 3, "Show the whole world"));
            for (WorldLayout.Place p : layout.places) for (WorldLayout.Placed a : p.areas)
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION + a.area, "Select " + WorldLayout.label(a.area)));
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == SELECT_ACTION - 1) { zoomBy(1 / 1.5f); return true; }
            if (action == SELECT_ACTION - 2) { zoomBy(1.5f); return true; }
            if (action == SELECT_ACTION - 3) { focusAll(); return true; }
            int area = action - SELECT_ACTION;
            if (area >= 0 && area <= 32 && layout.find(area) != null) { selectArea(area); return true; }
            return super.performAccessibilityAction(action, args);
        }
    }
}
