package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.MapViewport;
import name.osher.gil.minivmac.mapper.MapObservation;
import name.osher.gil.minivmac.mapper.CombatSnapshot;
import name.osher.gil.minivmac.mapper.MapMode;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.mapper.PartyRefusal;
import name.osher.gil.minivmac.mapper.ReadingHold;
import name.osher.gil.minivmac.notebook.NoteIcon;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/** Static black-on-white cartography: no animation, blink, or network access. */
public final class LiveMapView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyph = new Path();
    // Original Macintosh class-string order, not a modern D&D class list.
    private static final int[][] CLASS_MARKS = {{0},{1},{2},{3},{4},{5},{6},{7},
            {0,2},{0,2,5},{0,4},{0,5},{0,6},{2,5},{2,6},{2,5,6},{5,6},{8}};
    private final MapArtwork artwork = new MapArtwork();
    private final float density;
    private final float textScale;
    /** Why the probe would not report a party, when it would not. */
    private String partyRefusal;
    /*
     * One hold per display reading. The probes sample a running machine every
     * 250ms and the game is under no obligation to be readable at that instant,
     * least of all mid-battle, where it rewrites the very records these read.
     * Each unreadable frame used to blank its part of the pane for one poll.
     */
    private final ReadingHold mapHold = new ReadingHold();
    private final ReadingHold partyHold = new ReadingHold();
    private final ReadingHold combatHold = new ReadingHold();
    private final SparseArray<PartyState.Member> partyActions = new SparseArray<>();
    private PoolRadState state;
    private PartyState party;
    private boolean positionAvailable;
    /** Set only while the game is in combat; null at every other moment. */
    private CombatSnapshot combat;
    private MapMode mode = MapMode.UNAVAILABLE;
    private String notebook = "Loading notebook…";
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private ExplorationTrail exploration = ExplorationTrail.empty();
    private boolean visitedOnly, footprints = true;
    private String explorationStatus = "";
    private Listener listener;
    private int touchPointer = -1, touchTile = -1;
    /** Where the footprint toggle was last drawn, and whether a press began on it. */
    private final android.graphics.RectF footprintButton = new android.graphics.RectF();
    private final android.graphics.RectF footprintTarget = new android.graphics.RectF();
    /** The fog-of-war toggle beside it, same size, same behaviour. */
    private final android.graphics.RectF fogButton = new android.graphics.RectF();
    private final android.graphics.RectF fogTarget = new android.graphics.RectF();
    private boolean touchFog;
    /**
     * A Return key in the map's bottom-right corner. Much of this game is
     * playable with the mouse, but not all of it, and opening the whole
     * keyboard to press one key is a poor trade. It sends exactly the key the
     * app's own keyboard sends; nothing here reaches into the game any further
     * than that.
     */
    private final android.graphics.RectF returnButton = new android.graphics.RectF();
    private final android.graphics.RectF returnTarget = new android.graphics.RectF();
    private final android.graphics.RectF quickButton = new android.graphics.RectF();
    private boolean touchReturn;
    /** Which member's Q a press began on, or -1. */
    private int touchQuick = -1;
    private boolean touchFootprints;
    private float touchX, touchY;
    private String touchArea;
    private boolean preciseTouch;
    private PartyState touchParty;
    private int touchMember = -1;

    public interface Listener {
        void onAreaChanged(AreaIdentity area);
        void onTileTapped(AreaIdentity area, int x, int y);
        default void onNearbyFlagsTapped(AreaIdentity area, int x, int y, int[] candidates) {
            onTileTapped(area, x, y);
        }
        default void onPartyMemberTapped(PartyState.Member member) { }
        default void onExplorationSample(PoolRadState sample) { }
        default void onExplorationAreaChanged(AreaIdentity area) { }
        /** The player tapped the footprint button on the map itself. */
        default void onFootprintsToggled(boolean shown) { }
        /** The player tapped the fog-of-war button beside it. */
        default void onFogToggled(boolean visitedOnly) { }
        /** The player tapped the Return key in the map's corner. */
        default void onReturnPressed() { }
        /**
         * The player tapped a character's Q. {@code member} numbers the party
         * rows as they are drawn, which is the numbering the writer uses.
         */
        default void onQuickToggled(int member, boolean on) { }
    }

    public void setListener(Listener value) { listener = value; }

    /** Monotonic, so a wall-clock change cannot extend or cut short a hold. */
    private long now() { return android.os.SystemClock.elapsedRealtime(); }

    /**
     * Drop every reading at once. Polling has stopped -- the pane is being put
     * away, not blinking -- and nothing stale may survive to be shown on the
     * way back in.
     */
    public void clearReadings() {
        mapHold.reset(); partyHold.reset(); combatHold.reset();
        showSample(null); showPartySample(null);
    }
    public AreaIdentity currentArea() { return positionAvailable && state != null ? state.area : null; }
    public AreaIdentity displayedArea() { return state == null ? null : state.area; }
    public PoolRadState snapshot() { return positionAvailable ? state : null; }
    public void showPartySample(byte[] sample) {
        PartyState next = PartyState.parse(sample);
        // A single unreadable frame is a blink; keep the party that is drawn.
        if (!partyHold.accept(next != null, now())) return;
        PartyRefusal why = next == null ? PartyRefusal.parse(sample) : null;
        String reason = why == null ? null : why.label();
        boolean same = (party == null ? next == null : party.sameDisplay(next))
                && (partyRefusal == null ? reason == null : partyRefusal.equals(reason));
        partyRefusal = reason;
        if (same) return;
        party = next; cancelTap();
        partyActions.clear();
        if (party != null) for (PartyState.Member member : party.members)
            partyActions.put(0x03000000 | View.generateViewId(), member);
        refreshDescription(); invalidate();
    }
    private PartyPaneLayout pane() { return new PartyPaneLayout(getWidth(), getHeight(), density, party == null ? 0 : party.members.size(), textScale); }
    private MapViewport viewport() { PartyPaneLayout p=pane(); return new MapViewport(p.mapWidth,p.mapHeight,density); }
    public void showNotebook(String label, Map<Integer, NoteIcon> tiles) {
        Map<Integer, NoteIcon> copy = new HashMap<>(tiles);
        if (notebook.equals(label) && flags.equals(copy)) return;
        cancelTap(); notebook = label; flags = copy; refreshDescription(); invalidate();
    }

    public void setExplorationStyle(boolean fog, boolean feet) {
        if (visitedOnly == fog && footprints == feet) return;
        visitedOnly = fog; footprints = feet; refreshDescription(); invalidate();
    }

    public void showExploration(ExplorationTrail trail, String status) {
        if (trail == exploration && explorationStatus.equals(status)) return;
        exploration = trail; explorationStatus = status;
        refreshDescription(); invalidate();
    }

    private void refreshDescription() {
        String areaLabel = state == null ? "No local map yet"
                : state.area == null ? "Unidentified area" : state.area.label();
        String status = positionAvailable ? areaLabel + ". Party at " + state.positionLabel()
                + (state.searching ? ", searching" : "")
                : mode.label() + ". " + (state == null ? areaLabel : "Last local map: " + areaLabel)
                    + ". Position unavailable; party arrow hidden. " + mode.explanation();
        StringBuilder health = new StringBuilder();
        if (party != null) for (PartyState.Member member : party.members)
            health.append(' ').append(member.name).append(": ").append(member.currentHp).append(" of ").append(member.maxHp)
                    .append(" HP; AC ").append(member.armorClass == null ? "unavailable" : member.armorClass)
                    .append("; ").append(member.classLabel())
                    .append("; ").append(member.conditionSummary()).append('.');
        if (mode == MapMode.COMBAT && combat != null)
            status = "Battle overview. " + combat.summary()
                    + ", between " + combat.left + "," + combat.top
                    + " and " + combat.right + "," + combat.bottom
                    + ". Reference only; no terrain is shown and nothing here can be tapped.";
        setContentDescription(status + (positionAvailable
                ? ". Tap a tile to add a note; tap a symbol to reopen it. "
                : ". Reference only; map notes resume with local exploration. ")
                + notebook + ". " + flags.size() + " flags. " + exploration.visitedCount()
                + " walked squares. " + (visitedOnly ? "Visited-only map. " : "Full map. ")
                + (footprints ? "Footprints shown; " : "Footprints hidden; ")
                + "two buttons in the top-left corner of the map turn the footprints "
                + "and the fog of war off and on, and a Return key in the "
                + "bottom-right corner presses Return in the game. "
                + explorationStatus + health);
    }

    public LiveMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textScale = Math.max(1, getResources().getDisplayMetrics().scaledDensity / density);
        setBackgroundColor(Color.WHITE);
        setClickable(true); // Consume map taps: never send them to the guest or fullscreen gesture.
        setFocusable(false); // Hardware Return/arrows belong to the guest, not this read-only view.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Area map. Waiting for a supported Pool of Radiance party.");
    }

    /**
     * The tactical overview. Kept separate from the area map on purpose: this
     * is not a place the party can be tapped, walked or noted, it only shows
     * the squares the game has already drawn on its own Combat View.
     */
    public void showCombatSample(byte[] sample) {
        CombatSnapshot next = mode == MapMode.COMBAT ? CombatSnapshot.parse(sample) : null;
        // The battlefield is the worst offender: a fight rewrites these records
        // continuously, so without a hold it can blink several times a second.
        if (mode == MapMode.COMBAT && !combatHold.accept(next != null, now())) return;
        boolean had = combat != null;
        if (next == null && !had) return;
        combat = next;
        refreshDescription(); invalidate();
    }

    public void showSample(byte[] sample) {
        MapObservation observation = MapObservation.parse(sample);
        showState(observation.state, observation.mode);
    }

    // Retained for source-derived synthetic View fixtures without shipping game geometry.
    private void showState(PoolRadState next) {
        showState(next, next == null ? MapMode.UNAVAILABLE
                : !next.hasExplorationMetadata || next.explorationSafe
                    ? MapMode.EXPLORATION : MapMode.UPDATING);
    }

    private void showState(PoolRadState next, MapMode nextMode) {
        AreaIdentity previous = currentArea();
        AreaIdentity previousDisplay = displayedArea();
        boolean available = nextMode == MapMode.EXPLORATION && next != null;
        boolean changed = mode != nextMode || positionAvailable != available
                || (next != null && (state == null || !state.sameDisplay(next)));
        // Processing frames record nothing and do not refresh the previous-safe
        // deadline. The next sample still needs the same native epoch and a
        // short gap; a real relocation changes that epoch even while Updating.
        // Other unavailable modes actively interrupt recording, including repeats.
        boolean deliver = nextMode != MapMode.UPDATING || next != null;
        /*
         * Unavailable and Updating are the probe saying "not this instant",
         * not the game saying "something else is happening"; Combat, Camp,
         * Wilderness and Loading are named states and apply at once. So hold
         * the drawing through a transient unreadable frame -- position
         * included, which is the whole point: the last known position is a
         * better answer than no position.
         *
         * Recording is deliberately outside the hold. It still hears every
         * interruption the moment it happens, because a position held on
         * screen must never become a footprint the party did not walk.
         */
        boolean unreadable = nextMode == MapMode.UNAVAILABLE || nextMode == MapMode.UPDATING;
        if (!mapHold.accept(!unreadable, now())) {
            if (listener != null && deliver) listener.onExplorationSample(next);
            return;
        }
        if (!changed) {
            if (listener != null && deliver) listener.onExplorationSample(next);
            return;
        }
        mode = nextMode;
        // Never a stale battlefield: leaving combat drops it, hold and all.
        if (nextMode != MapMode.COMBAT) { combat = null; combatHold.reset(); }
        positionAvailable = available;
        if (next != null) state = next; // Status-only packets retain a reference, not a live map.
        cancelTap(); // A press begun in one mode cannot finish in another.
        AreaIdentity current = currentArea();
        AreaIdentity displayed = displayedArea();
        if (!(previousDisplay == null ? displayed == null : previousDisplay.equals(displayed))) {
            exploration = ExplorationTrail.empty(); explorationStatus = displayed == null ? "" : "Loading trail";
            if (listener != null) listener.onExplorationAreaChanged(displayed);
        }
        if (!(previous == null ? current == null : current != null && previous.id().equals(current.id()))) {
            cancelTap();
            flags = Collections.emptyMap();
            if (listener != null) listener.onAreaChanged(current);
        }
        if (listener != null && deliver) listener.onExplorationSample(next);
        refreshDescription();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelTap();
            touchPointer = event.getPointerId(0); touchX = event.getX(); touchY = event.getY();
            preciseTouch = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER;
            touchFootprints = footprintTarget.contains(touchX, touchY);
            touchFog = !touchFootprints && fogTarget.contains(touchX, touchY);
            touchReturn = !touchFootprints && !touchFog && returnTarget.contains(touchX, touchY);
            touchQuick = touchFootprints || touchFog || touchReturn ? -1 : quickAt(touchX, touchY);
            boolean onButton = touchFootprints || touchFog || touchReturn || touchQuick >= 0;
            touchMember = onButton ? -1 : pane().memberAt(touchX, touchY);
            touchParty = touchMember < 0 ? null : party;
            touchTile = touchMember < 0 && !onButton ? viewport().tileAt(touchX, touchY) : -1;
            AreaIdentity area = currentArea(); touchArea = area == null ? null : area.id();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE) {
            int pointer = event.findPointerIndex(touchPointer);
            if (pointer < 0 || Math.hypot(event.getX(pointer) - touchX, event.getY(pointer) - touchY)
                    > ViewConfiguration.get(getContext()).getScaledTouchSlop()) cancelTap();
        } else if (action == MotionEvent.ACTION_UP) {
            AreaIdentity area = currentArea();
            int tile = viewport().tileAt(event.getX(), event.getY());
            boolean valid = event.getPointerId(0) == touchPointer
                    && (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0
                    && Math.hypot(event.getX() - touchX, event.getY() - touchY)
                        <= ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    && listener != null;
            if (valid && touchFootprints && footprintTarget.contains(event.getX(), event.getY())) {
                boolean shown = !footprints;
                setExplorationStyle(visitedOnly, shown);
                performClick();
                listener.onFootprintsToggled(shown);
            } else if (valid && touchQuick >= 0 && quickAt(event.getX(), event.getY()) == touchQuick
                    && party != null && touchQuick < party.members.size()) {
                // Unknown reads as off, so a first tap turns it on rather than
                // doing nothing the player can see.
                Boolean current = party.members.get(touchQuick).quick;
                performClick();
                listener.onQuickToggled(touchQuick, !Boolean.TRUE.equals(current));
            } else if (valid && touchReturn && returnTarget.contains(event.getX(), event.getY())) {
                performClick();
                listener.onReturnPressed();
            } else if (valid && touchFog && fogTarget.contains(event.getX(), event.getY())) {
                boolean fog = !visitedOnly;
                setExplorationStyle(fog, footprints);
                performClick();
                listener.onFogToggled(fog);
            } else if (valid && touchMember >= 0 && touchParty == party && party != null
                    && pane().memberAt(event.getX(), event.getY()) == touchMember) {
                PartyState.Member selected = party.members.get(touchMember);
                cancelTap(); performClick(); listener.onPartyMemberTapped(selected);
            } else if (valid && positionAvailable && tile >= 0 && tile == touchTile
                    && (touchArea == null ? area == null : area != null && touchArea.equals(area.id()))) {
                performClick();
                int[] nearby = preciseTouch || flags.containsKey(tile) ? new int[0]
                        : viewport().nearbyFlags(touchX, touchY, flags.keySet(), 24 * density);
                if (nearby.length == 0) listener.onTileTapped(area, tile % 16, tile / 16);
                else listener.onNearbyFlagsTapped(area, tile % 16, tile / 16, nearby);
            }
            cancelTap();
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) {
            cancelTap();
        }
        return true; // No map gesture, including a cancelled one, reaches the Mac.
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (listener != null) for (int i = 0; i < partyActions.size(); i++) {
            PartyState.Member member = partyActions.valueAt(i);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(partyActions.keyAt(i),
                    "Details for " + member.name + ", " + member.classLabel() + ", " + member.conditionSummary()));
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        PartyState.Member member = partyActions.get(action);
        if (member != null && listener != null) {
            cancelTap(); listener.onPartyMemberTapped(member); return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private void cancelTap() {
        touchFootprints = false; touchFog = false; touchReturn = false; touchQuick = -1;
        touchPointer = touchTile = -1;
        touchMember = -1; touchParty = null;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH); cancelTap();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelTap();
    }

    @Override protected void onDetachedFromWindow() { cancelTap(); super.onDetachedFromWindow(); }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) cancelTap();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PartyPaneLayout pane = pane();
        drawParty(canvas, pane);
        canvas.save();
        canvas.clipRect(0, 0, pane.mapWidth, pane.mapHeight);
        try { drawMap(canvas, pane); } finally { canvas.restore(); }
    }

    private void drawMap(Canvas canvas, PartyPaneLayout pane) {
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(14 * density);
        String title = state != null && state.area != null ? state.area.label() : "AREA MAP";
        if (state != null && !positionAvailable) title += " · reference";
        if (mode == MapMode.COMBAT) title = combat == null ? "BATTLE" : "BATTLE · overview";
        /*
         * One steady word rather than a blinking one. The transient modes churn
         * between Updating, Loading and Position unavailable several times a
         * second while the game settles, and the old inverted badge made every
         * one of those flips flash. The reason is still carried in the
         * accessibility description and in the "· reference" title suffix.
         */
        String status = positionAvailable ? state.positionLabelWithSearch()
                : mode == MapMode.COMBAT && combat != null ? combat.summary()
                : MapMode.UNAVAILABLE.label();
        float button = drawHeaderButtons(canvas, pane);
        float available = Math.max(0, pane.mapWidth - 24 * density - button);
        float statusWidth = Math.min(ink.measureText(status), available * .48f);
        ink.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fitHeaderText(title, available - statusWidth - 12 * density),
                12 * density + button, 22 * density, ink);
        ink.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(fitHeaderText(status, statusWidth), pane.mapWidth - 12 * density, 22 * density, ink);
        drawReturnButton(canvas, pane);
        ink.setColor(Color.BLACK);
        ink.setStrokeWidth(density);
        canvas.drawLine(0, getHeight() - density, getWidth(), getHeight() - density, ink);
        if (mode == MapMode.COMBAT) { drawCombat(canvas, pane, available); return; }
        if (state == null) {
            // No centred explanation: the header already says the position is
            // unavailable, and the full reason stays in the accessible text.
            return;
        }
        MapViewport viewport = viewport();
        float top = viewport.top, cell = viewport.cell;
        if (cell < 3) return;
        float left = viewport.left;
        ink.setTextSize(Math.min(11 * density, cell * .7f));
        ink.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 16; i += 4) {
            canvas.drawText(Integer.toString(i), left + (i + .5f) * cell, top - 5 * density, ink);
            canvas.drawText(Integer.toString(i), left - 11 * density, top + (i + .7f) * cell, ink);
        }
        artwork.drawExploration(canvas, state.map, exploration, visitedOnly, footprints, left, top, cell, density);
        artwork.drawMarkers(canvas, flags, state, positionAvailable, left, top, cell, density);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextAlign(Paint.Align.CENTER);
        // One caption line. The old "North up · N walked" reminder is gone; the
        // slot still carries whichever of these actually tells the player
        // something, and the notebook name always rides along.
        ink.setTextSize(11 * density);
        // The caption names the notebook and says taps work. Helper state does
        // not belong here; the header carries availability on its own.
        /*
         * When the party is not on screen, say why on screen. Guessing at a
         * tablet's pane size and density from a bug report cost two wrong
         * fixes; this makes the numbers screenshottable.
         *
         * The build name rides along on every caption for the same
         * reason. A screenshot taken from v0.18.0 looked exactly like a
         * bug in the current layout, and dating it took an archaeology
         * pass over every tag.
         */
        String missing = party == null
                    ? "party: " + (partyRefusal == null ? "no reading yet" : partyRefusal)
                : pane.rows == 0 ? "party: no room in " + getWidth() + "×" + getHeight()
                    + " @" + density + (textScale == 1 ? "" : " ×" + textScale)
                : null;
        canvas.drawText(fitHeaderText(
                        (positionAvailable ? "Tap a tile or symbol · " : "") + notebook
                        + (missing == null ? "" : " · " + missing)
                        + " · v" + BuildConfig.VERSION_NAME, available),
                pane.mapWidth / 2f, pane.mapHeight - 7 * density, ink);
    }

    /**
     * The battle as an overview: one square per combatant, the party filled and
     * everyone else hollow, on the bounds of the squares actually read. No
     * terrain is drawn, because none has been decoded, and nothing here is a
     * suggestion: the original Combat View below remains the place to act.
     */
    private void drawCombat(Canvas canvas, PartyPaneLayout pane, float available) {
        CombatSnapshot battle = combat;
        ink.setStyle(Paint.Style.FILL);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(11 * density);
        if (battle == null) {
            canvas.drawText(fitHeaderText(MapMode.COMBAT.explanation(), available),
                    pane.mapWidth / 2f, pane.mapHeight / 2f, ink);
            return;
        }
        float margin = 26 * density, caption = 22 * density;
        float usableWidth = pane.mapWidth - 2 * margin;
        float usableHeight = pane.mapHeight - margin - caption - 10 * density;
        if (usableWidth <= 0 || usableHeight <= 0) return;
        float cell = Math.min(usableWidth / battle.width(), usableHeight / battle.height());
        cell = Math.min(cell, 34 * density);
        if (cell < 3) return;
        float gridWidth = cell * battle.width(), gridHeight = cell * battle.height();
        float left = (pane.mapWidth - gridWidth) / 2f, top = margin + (usableHeight - gridHeight) / 2f;

        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1, density));
        canvas.drawRect(left, top, left + gridWidth, top + gridHeight, ink);
        for (CombatSnapshot.Spot spot : battle.spots()) {
            float cx = left + (spot.x - battle.left + .5f) * cell;
            float cy = top + (spot.y - battle.top + .5f) * cell;
            float radius = cell * .32f;
            ink.setStyle(spot.party ? Paint.Style.FILL : Paint.Style.STROKE);
            ink.setStrokeWidth(Math.max(1.5f * density, cell * .09f));
            if (spot.party) canvas.drawCircle(cx, cy, radius, ink);
            else canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, ink);
        }
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(Math.min(11 * density, cell * .8f));
        canvas.drawText(fitHeaderText(battle.left + "," + battle.top + " to "
                        + battle.right + "," + battle.bottom, available),
                pane.mapWidth / 2f, top - 8 * density, ink);
        ink.setTextSize(11 * density);
        canvas.drawText(fitHeaderText(
                        "Filled is yours · reference only, tap the game below to act", available),
                pane.mapWidth / 2f, pane.mapHeight - 7 * density, ink);
    }

    /**
     * Two small toggles in the header: the trail, and fog of war. Both can make
     * a busy street hard to read, and both were three taps away under Info.
     * Returns the width they used so the title can start beside them. Drawn
     * even with nothing walked yet, so the controls do not appear and disappear
     * under the player.
     */
    private float drawHeaderButtons(Canvas canvas, PartyPaneLayout pane) {
        float size = 26 * density, gap = 6 * density, left = 8 * density, top = 3 * density;
        // Two buttons plus the title need the room; below this the header wins.
        if (pane.mapWidth < 260 * density) {
            footprintButton.setEmpty(); footprintTarget.setEmpty();
            fogButton.setEmpty(); fogTarget.setEmpty();
            return 0;
        }
        footprintButton.set(left, top, left + size, top + size);
        fogButton.set(left + size + gap, top, left + 2 * size + gap, top + size);
        target(footprintButton, footprintTarget);
        target(fogButton, fogTarget);
        drawFootprints(canvas, footprintButton, size);
        drawFog(canvas, fogButton, size);
        ink.setStyle(Paint.Style.FILL); ink.setStrokeWidth(density);
        return 2 * size + gap + 8 * density;
    }

    /** The drawn button is 26dp; fingers get the 48dp target the guidelines ask for. */
    private void target(android.graphics.RectF drawn, android.graphics.RectF touch) {
        touch.set(drawn);
        float grow = Math.max(0, (48 * density - drawn.width()) / 2);
        touch.inset(-grow, -grow);
        touch.offset(Math.max(0, -touch.left), Math.max(0, -touch.top));
    }

    private void frame(Canvas canvas, android.graphics.RectF button) {
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
        ink.setColor(Color.BLACK);
        canvas.drawRoundRect(button, 4 * density, 4 * density, ink);
    }

    /**
     * Crossed out, not hollowed out. Hollow soles plus a slash read as a percent
     * sign at button size; filled soles under a slash read as footprints that
     * are switched off. The white underlay keeps the slash visible where it
     * crosses a sole, which matters most on e-ink, where there is no colour to
     * fall back on.
     */
    private void drawFootprints(Canvas canvas, android.graphics.RectF button, float size) {
        frame(canvas, button);
        float cx = button.centerX(), cy = button.centerY(), sole = size * .16f;
        ink.setStyle(Paint.Style.FILL);
        canvas.drawOval(cx - sole * 1.8f, cy - sole * 1.9f, cx - sole * .2f, cy + sole * .3f, ink);
        canvas.drawOval(cx + sole * .2f, cy - sole * .3f, cx + sole * 1.8f, cy + sole * 1.9f, ink);
        if (!footprints) slash(canvas, button);
    }

    /**
     * Fog of war as the thing it does to the map: four map squares, one walked
     * and open, three still covered. An earlier version split a single square
     * down the middle and outlined one half, which at this size read as a
     * letter rather than a map. Same size as the trail button beside it, and
     * the same slash when the feature is off, so the pair reads as one control
     * strip rather than two ideas.
     */
    private void drawFog(Canvas canvas, android.graphics.RectF button, float size) {
        frame(canvas, button);
        float inset = size * .24f, gap = size * .07f;
        float l = button.left + inset, t = button.top + inset;
        float r = button.right - inset, b = button.bottom - inset;
        float cellW = (r - l - gap) / 2, cellH = (b - t - gap) / 2;
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                float x = l + column * (cellW + gap), y = t + row * (cellH + gap);
                boolean walked = row == 0 && column == 0;
                ink.setStyle(walked ? Paint.Style.STROKE : Paint.Style.FILL);
                ink.setStrokeWidth(Math.max(1, density));
                canvas.drawRect(x, y, x + cellW, y + cellH, ink);
            }
        }
        if (!visitedOnly) slash(canvas, button);
    }

    /**
     * The Return key, in the bottom-right corner of the map rather than the
     * header: it is an action on the game, not a view option, and the corner is
     * where a thumb already is. The arrow is the key's own glyph -- a bar down
     * the right, a shaft left along the bottom, and a head on the end.
     */
    private void drawReturnButton(Canvas canvas, PartyPaneLayout pane) {
        float size = 26 * density, margin = 8 * density;
        // The caption owns the bottom centre; below this the corner is too tight.
        if (pane.mapWidth < 200 * density || pane.mapHeight < 120 * density) {
            returnButton.setEmpty(); returnTarget.setEmpty(); return;
        }
        float right = pane.mapWidth - margin, bottom = pane.mapHeight - margin;
        returnButton.set(right - size, bottom - size, right, bottom);
        target(returnButton, returnTarget);
        // Keep the touch target inside the pane, not off its right edge.
        returnTarget.offset(Math.min(0, pane.mapWidth - returnTarget.right),
                Math.min(0, pane.mapHeight - returnTarget.bottom));
        frame(canvas, returnButton);
        float inset = size * .28f;
        float l = returnButton.left + inset, r = returnButton.right - inset;
        float t = returnButton.top + inset, b = returnButton.bottom - inset;
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1, 1.4f * density));
        canvas.drawLine(r, t, r, b, ink);          // the bar the arrow returns to
        canvas.drawLine(r, b, l, b, ink);          // the shaft
        float head = size * .16f;
        canvas.drawLine(l, b, l + head, b - head, ink);
        canvas.drawLine(l, b, l + head, b + head, ink);
        ink.setStyle(Paint.Style.FILL); ink.setStrokeWidth(density);
    }

    /**
     * A character's quick flag as a square Q: filled when the game has them on
     * computer control, outlined when it does not, and a question mark when the
     * flag does not read as either -- never a confident "off" over a byte
     * nobody understands.
     */
    private void drawQuick(Canvas canvas, android.graphics.RectF box, Boolean quick, float unit) {
        boolean on = Boolean.TRUE.equals(quick);
        ink.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1, density));
        ink.setColor(Color.BLACK);
        canvas.drawRoundRect(box, 3 * unit, 3 * unit, ink);
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(on ? Color.WHITE : Color.BLACK);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(11 * unit);
        Paint.FontMetrics metrics = ink.getFontMetrics();
        float baseline = box.centerY() - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(quick == null ? "?" : "Q", box.centerX(), baseline, ink);
        ink.setColor(Color.BLACK);
        ink.setTextAlign(Paint.Align.LEFT);
    }

    private void slash(Canvas canvas, android.graphics.RectF button) {
        float x1 = button.left + 5 * density, y1 = button.bottom - 5 * density;
        float x2 = button.right - 5 * density, y2 = button.top + 5 * density;
        ink.setStyle(Paint.Style.STROKE);
        ink.setColor(Color.WHITE); ink.setStrokeWidth(4 * density);
        canvas.drawLine(x1, y1, x2, y2, ink);
        ink.setColor(Color.BLACK); ink.setStrokeWidth(1.5f * density);
        canvas.drawLine(x1, y1, x2, y2, ink);
    }

    /** Keep title and live/unavailable status in separate bounded header regions. */
    private String fitHeaderText(String value, float width) {
        if (width <= 0) return "";
        if (ink.measureText(value) <= width) return value;
        float ellipsis = ink.measureText("…");
        if (width < ellipsis) return "";
        int count = ink.breakText(value, true, width - ellipsis, null);
        return value.substring(0, count) + "…";
    }

    /**
     * The Q square on a party row, in the row's top-right corner.
     *
     * One helper for drawing and for hit-testing, so the two cannot drift; the
     * footprint button was drawn and tested from separate arithmetic once and
     * that is a bug waiting to happen.
     */
    private void quickSquare(PartyPaneLayout p, int index, float unit, android.graphics.RectF into) {
        float column = p.columnLeft(index);
        float right = column + p.columnWidth - 10 * unit;
        float top = p.rowTop(index) + (p.rowHeight - 48 * unit) / 2;
        float size = 16 * unit;
        into.set(right - size, top, right, top + size);
    }

    /** Which member's Q is under a point, or -1. Generous, like the map buttons. */
    private int quickAt(float x, float y) {
        PartyPaneLayout p = pane();
        if (party == null || p.rows == 0) return -1;
        float unit = density * p.appliedScale;
        android.graphics.RectF box = new android.graphics.RectF();
        android.graphics.RectF touch = new android.graphics.RectF();
        for (int i = 0; i < p.visibleMembers(); i++) {
            quickSquare(p, i, unit, box);
            touch.set(box);
            // Half the shortfall to a 48dp target, and no more: the squares sit
            // inside a row, so a target that grew to the full 48dp would reach
            // into the row above and steal its taps.
            float grow = Math.max(0, Math.min(8 * density, (48 * density - box.width()) / 2));
            touch.inset(-grow, -grow);
            if (touch.contains(x, y)) return i;
        }
        return -1;
    }

    private void drawParty(Canvas canvas, PartyPaneLayout p) {
        if (party == null || p.rows == 0) return;
        canvas.save(); canvas.clipRect(p.partyLeft,p.partyTop,p.partyLeft+p.partyWidth,p.partyTop+p.partyHeight);
        // The layout, not the device, decides the party's text scale: it steps
        // a very large accessibility scale back rather than draw no party.
        float unit = density * p.appliedScale;
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
        // Beside the map the party is fenced off on its left; underneath it, on top.
        if (p.belowMap()) canvas.drawLine(p.partyLeft,p.partyTop,p.partyLeft+p.partyWidth,p.partyTop,ink);
        else canvas.drawLine(p.partyLeft,p.partyTop,p.partyLeft,p.partyTop+p.partyHeight,ink);
        ink.setStyle(Paint.Style.FILL); ink.setTextAlign(Paint.Align.LEFT); ink.setTextSize(10*unit);
        canvas.drawText("PARTY · TAP FOR DETAILS",p.partyLeft+10*unit,p.partyTop+16*unit,ink);
        for (int i=1;i<p.columns;i++) {
            float divider=p.partyLeft+i*p.columnWidth;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(density);
            canvas.drawLine(divider,p.partyTop+p.headerHeight,divider,p.partyTop+p.partyHeight,ink);
        }
        for (int i=0;i<p.visibleMembers();i++) {
            PartyState.Member member=party.members.get(i);
            float column=p.columnLeft(i);
            float left=column+44*unit, right=column+p.columnWidth-10*unit;
            float top=p.rowTop(i)+(p.rowHeight-48*unit)/2;
            if (member.badge().isEmpty()) drawClassSymbol(canvas, member, column+9*unit, top+8*unit, 27*unit);
            else drawConditionBadge(canvas,member.badge(),column+9*unit,top+8*unit,27*unit);
            quickSquare(p, i, unit, quickButton);
            drawQuick(canvas, quickButton, member.quick, unit);
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK); ink.setTextSize(13*unit);
            float available=Math.max(0,right-left-quickButton.width()-6*unit);
            int chars=ink.breakText(member.name,true,available,null);
            String name=chars==member.name.length()?member.name:chars>1?member.name.substring(0,chars-1)+"…":"";
            ink.setTextAlign(Paint.Align.LEFT);canvas.drawText(name,left,top+14*unit,ink);
            ink.setTextSize(11*unit);
            // A narrow strip cell has no room for both readouts; health wins.
            boolean compact = p.columnWidth < PartyPaneLayout.COMPACT_COLUMN * unit;
            canvas.drawText((compact ? "" : "HP ")+member.currentHp+"/"+member.maxHp,left,top+28*unit,ink);
            if (!compact) {
                ink.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText("AC "+(member.armorClass==null ? "—" : member.armorClass),right,top+28*unit,ink);
            }
            float barTop=top+34*unit,barBottom=top+40*unit;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(density);
            canvas.drawRect(left,barTop,right,barBottom,ink);
            ink.setStyle(Paint.Style.FILL);
            float fraction=Math.max(0,Math.min(1,member.healthFraction()));
            if(fraction>0)canvas.drawRect(left,barTop,left+(right-left)*fraction,barBottom,ink);
        }
        canvas.restore();
    }

    /** One high-contrast condition mark in the existing class-icon slot; details explain it. */
    private void drawConditionBadge(Canvas canvas, String badge, float left, float top, float size) {
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left,top,left+size,top+size,size*.12f,size*.12f,ink);
        ink.setColor(Color.WHITE); ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(size*.72f);
        float baseline=top+size/2-(ink.ascent()+ink.descent())/2;
        canvas.drawText(badge,left+size/2,baseline,ink);
        ink.setColor(Color.BLACK); ink.setTextAlign(Paint.Align.LEFT);
    }

    /** Class marks return automatically when the condition needs no badge. */
    private void drawClassSymbol(Canvas canvas, PartyState.Member member, float left, float top, float size) {
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(Math.max(density, size*.055f));
        ink.setColor(Color.BLACK); ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
        int kind=member.characterClass;
        if(kind<0 || kind>=CLASS_MARKS.length) {
            canvas.drawCircle(left+size/2,top+size/2,size*.45f,ink);
            ink.setStyle(Paint.Style.FILL); ink.setTextAlign(Paint.Align.CENTER); ink.setTextSize(size*.7f);
            canvas.drawText("?",left+size/2,top+size*.75f,ink);
        } else {
            int[] marks=CLASS_MARKS[kind];
            for(int i=0;i<marks.length;i++) {
                float side=marks.length==1 ? size : size*.52f;
                float x=left+(marks.length==1 ? 0 : marks.length==3 && i==2 ? size*.24f : i*size*.48f);
                float y=top+(marks.length==1 ? 0 : marks.length==2 ? size*.24f : i==2 ? size*.48f : 0);
                drawClassMark(canvas,marks[i],x,y,side);
            }
        }
        ink.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawClassMark(Canvas canvas,int kind,float left,float top,float size) {
        canvas.save(); canvas.translate(left,top); canvas.scale(size/24,size/24);
        ink.setStrokeWidth(1.7f); ink.setStyle(Paint.Style.STROKE); glyph.reset();
        switch(kind) {
            case 0: // Cleric: a holy-symbol cross.
                canvas.drawLine(12,3,12,21,ink);canvas.drawLine(5,9,19,9,ink);break;
            case 1: // Druid: leaf and stem.
                glyph.moveTo(4,20);glyph.cubicTo(1,8,10,2,21,3);glyph.cubicTo(22,13,15,23,4,20);
                canvas.drawPath(glyph,ink);canvas.drawLine(3,22,17,7,ink);canvas.drawLine(9,15,8,9,ink);break;
            case 2: // Fighter: sword.
                glyph.moveTo(9,15);glyph.lineTo(9,6);glyph.lineTo(12,2);glyph.lineTo(15,6);glyph.lineTo(15,15);
                canvas.drawPath(glyph,ink);canvas.drawLine(5,16,19,16,ink);canvas.drawLine(12,16,12,22,ink);break;
            case 3: // Paladin: shield bearing the cleric's cross.
                glyph.moveTo(4,3);glyph.lineTo(20,3);glyph.lineTo(19,15);glyph.quadTo(16,20,12,22);
                glyph.quadTo(8,20,5,15);glyph.close();canvas.drawPath(glyph,ink);
                canvas.drawLine(12,6,12,16,ink);canvas.drawLine(8,10,16,10,ink);break;
            case 4: // Ranger: bow, string and arrow.
                glyph.moveTo(7,2);glyph.quadTo(26,12,7,22);canvas.drawPath(glyph,ink);
                canvas.drawLine(7,2,7,22,ink);canvas.drawLine(2,12,22,12,ink);
                canvas.drawLine(18,9,22,12,ink);canvas.drawLine(18,15,22,12,ink);break;
            case 5: // Magic-user: wand and spark.
                canvas.drawLine(3,21,14,10,ink);canvas.drawLine(17,1,17,11,ink);canvas.drawLine(12,6,22,6,ink);
                canvas.drawLine(14,3,20,9,ink);canvas.drawLine(14,9,20,3,ink);break;
            case 6: // Thief: key.
                canvas.drawCircle(7,7,4,ink);canvas.drawLine(10,10,21,21,ink);
                canvas.drawLine(16,16,19,13,ink);canvas.drawLine(19,19,22,16,ink);break;
            case 7: // Monk: closed fist and wrist.
                glyph.moveTo(5,14);glyph.lineTo(4,8);glyph.lineTo(8,5);glyph.lineTo(19,5);glyph.lineTo(20,13);
                glyph.lineTo(16,18);glyph.lineTo(16,22);glyph.lineTo(8,22);glyph.lineTo(8,18);glyph.close();
                canvas.drawPath(glyph,ink);canvas.drawLine(9,6,9,11,ink);canvas.drawLine(13,6,13,11,ink);
                canvas.drawLine(17,6,17,11,ink);canvas.drawLine(5,14,13,14,ink);break;
            default: // Original guest's Monster class: horns and fangs.
                glyph.moveTo(4,10);glyph.lineTo(2,2);glyph.lineTo(9,7);glyph.lineTo(15,7);glyph.lineTo(22,2);
                glyph.lineTo(20,10);glyph.lineTo(20,18);glyph.lineTo(12,22);glyph.lineTo(4,18);glyph.close();
                canvas.drawPath(glyph,ink);canvas.drawLine(7,11,9,12,ink);canvas.drawLine(17,11,15,12,ink);
                canvas.drawLine(8,16,10,19,ink);canvas.drawLine(16,16,14,19,ink);break;
        }
        canvas.restore();
    }
}
