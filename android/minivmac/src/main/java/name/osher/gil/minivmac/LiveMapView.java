package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import name.osher.gil.minivmac.mapper.GameClock;
import name.osher.gil.minivmac.mapper.CombatSnapshot;
import name.osher.gil.minivmac.mapper.MapMode;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.mapper.MapProgress;
import name.osher.gil.minivmac.mapper.PartyChores;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.mapper.UnwalkedExits;
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
    private final SparseArray<PartyState.Member> partySheetActions = new SparseArray<>();
    private PoolRadState state;
    private GameClock gameClock;
    private PartyState party;
    private boolean positionAvailable;
    /** Set only while the game is in combat; null at every other moment. */
    private CombatSnapshot combat;
    /**
     * The battle grid as it was last drawn, so a tap can be mapped back to the
     * combatant under it. Zero cell means nothing is tappable there.
     */
    private float combatLeft, combatTop, combatCell;
    /**
     * Which party row a tap on the overview is pointing at, and until when.
     *
     * On the grid everyone is a circle; the question "which of these is
     * Tanarakis" has no answer without counting markers against rows. Tapping
     * one answers it. It identifies and nothing more -- no order is given, no
     * target is chosen, and it fades on its own so the pane does not acquire a
     * selection the player has to remember to clear.
     */
    private int highlightedMember = -1;
    private long highlightUntil;
    private int touchCombatant = -1;
    /** Long enough to look across the pane and back, short enough to forget. */
    private static final long HIGHLIGHT_MS = 3000;
    /**
     * When the party's own square was last asked for, and the strip that asks.
     *
     * On a full sixteen-by-sixteen map with walls, doors, footprints and notes
     * on it, the one small arrow that is you is genuinely hard to pick out.
     * Tapping the header rings it for a moment. It is a way of looking, not a
     * way of doing: nothing moves, nothing is written, and it fades on its own.
     */
    private long pingUntil;
    private final RectF headerTarget = new RectF();
    private boolean touchHeader;
    private static final long PING_MS = 3000;
    private MapMode mode = MapMode.UNAVAILABLE;
    private String notebook = "Loading notebook…";
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private ExplorationTrail exploration = ExplorationTrail.empty();
    private boolean visitedOnly, footprints = true;
    /** F69: compact one-line party rows, so a big party keeps one column and the map keeps its width. */
    private boolean oneLineParty;
    /** F64: mirror the game's Message window in larger type over the map. */
    private boolean mirrorMessage;
    private String gameMessage = "";
    private String explorationStatus = "";
    private Listener listener;
    private int touchPointer = -1, touchTile = -1;
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
    private float touchX, touchY;
    private String touchArea;
    private boolean preciseTouch;
    private PartyState touchParty;
    private int touchMember = -1;
    private final Runnable partyLongPress = this::performLongClick;

    public interface Listener {
        void onAreaChanged(AreaIdentity area);
        void onTileTapped(AreaIdentity area, int x, int y);
        default void onNearbyFlagsTapped(AreaIdentity area, int x, int y, int[] candidates) {
            onTileTapped(area, x, y);
        }
        default void onPartyMemberTapped(PartyState.Member member) { }
        default void onPartyMemberLongPressed(PartyState.Member member) { }
        default void onExplorationSample(PoolRadState sample) { }
        default void onConnectionSample(name.osher.gil.minivmac.mapper.AreaTravel sample) { }
        default void onExplorationAreaChanged(AreaIdentity area) { }
        /** The player tapped the Return key in the map's corner. */
        default void onReturnPressed() { }
        /** A fight has just started on this square of this area. */
        default void onAmbush(AreaIdentity area, int tile) { }
        /**
         * The player tapped a character's Q. {@code member} numbers the party
         * rows as they are drawn, which is the numbering the writer uses.
         */
        default void onQuickToggled(int member, boolean on) { }
    }

    public void setListener(Listener value) { listener = value; }

    public interface QuickPending { Boolean desired(PartyState.Member member); }
    private QuickPending quickPending = member -> null;
    private String quickPendingDisplay = "";
    public void setQuickPending(QuickPending pending) { quickPending=pending; }
    public void refreshQuickPending() {
        StringBuilder next=new StringBuilder();
        if(party!=null) for(PartyState.Member member:party.members) {
            Boolean desired=quickPending.desired(member);
            if(desired!=null) next.append(member.name).append(':').append(desired).append(';');
        }
        String value=next.toString();
        if(!value.equals(quickPendingDisplay)) {quickPendingDisplay=value;refreshDescription();invalidate();}
    }
    public PartyState.Member partyMember(int index) {
        return party==null||index<0||index>=party.members.size()?null:party.members.get(index);
    }
    private void drawPendingQuick(Canvas canvas, android.graphics.RectF box, PartyState.Member member, float unit) {
        if (quickPending.desired(member) != null) {
            ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.FILL);
            canvas.drawCircle(box.left-3*unit,box.centerY(),1.8f*unit,ink);
        }
    }

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
    public PartyState partySnapshot() { return party; }
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
        partyActions.clear(); partySheetActions.clear();
        if (party != null) for (PartyState.Member member : party.members) {
            partyActions.put(0x03000000 | View.generateViewId(), member);
            partySheetActions.put(0x03000000 | View.generateViewId(), member);
        }
        refreshDescription(); invalidate();
    }
    private PartyPaneLayout pane() { return new PartyPaneLayout(getWidth(), getHeight(), density, party == null ? 0 : party.members.size(), textScale, oneLineParty); }
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

    /** Turn compact one-line party rows on or off (F69). */
    public void setOneLineParty(boolean on) {
        if (oneLineParty == on) return;
        oneLineParty = on; invalidate();
    }

    /** Turn the large-type message mirror on or off (F64). */
    public void setMirrorMessage(boolean on) {
        if (mirrorMessage == on) return;
        mirrorMessage = on; invalidate();
    }

    /** The game's current Message-window text, to mirror in larger type; null or blank hides the mirror. */
    public void showGameMessage(String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(gameMessage)) return;
        gameMessage = next;
        if (mirrorMessage) invalidate();
    }

    public void showExploration(ExplorationTrail trail, String status) {
        if (trail == exploration && explorationStatus.equals(status)) return;
        exploration = trail; explorationStatus = status;
        refreshDescription(); invalidate();
    }

    /**
     * Doors seen from one side and never gone through. Said in words as well as
     * drawn, because the chevron is the kind of mark that wants explaining once.
     */
    private String unwalkedExitsLabel() {
        if (state == null || state.map == null) return "";
        int exits = UnwalkedExits.count(state.map, exploration);
        return exits == 0 ? "" : exits == 1 ? "1 door not yet gone through. "
                : exits + " doors not yet gone through. ";
    }

    private void refreshDescription() {
        String areaLabel = state == null ? "No local map yet"
                : state.area == null ? "Unidentified area" : state.area.label();
        String status = positionAvailable ? areaLabel + ". Party at " + state.positionLabel()
                + (state.searching ? ", searching" : "")
                : mode.label() + ". " + (state == null ? areaLabel : "Last local map: " + areaLabel)
                    + ". Position unavailable; party arrow hidden. " + mode.explanation();
        StringBuilder health = new StringBuilder();
        if (party != null && !PartyChores.NOTHING.equals(PartyChores.summary(party)))
            health.append(' ').append(PartyChores.summary(party)).append('.');
        if (party != null) for (int index = 0; index < party.members.size(); index++) {
            PartyState.Member member = party.members.get(index);
            health.append(' ').append(member.displayName())
                    .append(mode == MapMode.COMBAT && combat != null && combat.isActing(member.name)
                            ? " (acting): " : selectedOutsideCombat(index) ? " (selected): " : ": ")
                    .append(member.currentHp).append(" of ").append(member.maxHp)
                    .append(" HP; AC ").append(member.armorClass == null ? "unavailable" : member.armorClass)
                    .append("; ").append(member.classLabel())
                    .append("; ").append(member.conditionSummary())
                    .append(member.readyToTrain() ? "; can train" : "")
                    .append(party.slowedByLoad(member) ? "; slowed by load" : "")
                    .append(highlightedMember == index && highlightShowing() ? "; tapped on the battle overview" : "")
                    .append(member.spellsAwaitingRestTotal() > 0 ? "; spells await rest" : "")
                    .append(quickPending.desired(member) != null ? "; quick-combat change queued" : "")
                    .append('.');
        }
        if (mode == MapMode.COMBAT && combat != null)
            status = "Battle overview. " + combat.summary()
                    + ", between " + combat.left + "," + combat.top
                    + " and " + combat.right + "," + combat.bottom
                    + ". Reference only; no terrain is shown and nothing here can be tapped.";
        setContentDescription(status + (gameClock == null ? "" : ". Game time: " + gameClock.label()) + (positionAvailable
                ? ". Tap a tile to add a note; tap a symbol to reopen it. "
                : ". Reference only; map notes resume with local exploration. ")
                + notebook + ". " + flags.size() + " flags. "
                + MapProgress.spoken(exploration.visitedCount()) + ". " + unwalkedExitsLabel()
                + (pinging() ? "Your square is ringed. " : "")
                + (visitedOnly ? "Visited-only map. " : "Full map. ")
                + (footprints ? "Footprints shown; " : "Footprints hidden; ")
                + "Info, Options changes map appearance. A Return key in the "
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
        if (listener != null) listener.onConnectionSample(name.osher.gil.minivmac.mapper.AreaTravel.parse(sample));
        MapObservation observation = MapObservation.parse(sample);
        showState(observation.state, observation.mode, observation.clock);
    }

    // Retained for source-derived synthetic View fixtures without shipping game geometry.
    private void showState(PoolRadState next) {
        showState(next, next == null ? MapMode.UNAVAILABLE
                : !next.hasExplorationMetadata || next.explorationSafe
                    ? MapMode.EXPLORATION : MapMode.UPDATING);
    }

    private void showState(PoolRadState next, MapMode nextMode) {
        showState(next, nextMode, null);
    }

    private void showState(PoolRadState next, MapMode nextMode, GameClock nextClock) {
        AreaIdentity previous = currentArea();
        AreaIdentity previousDisplay = displayedArea();
        boolean available = nextMode == MapMode.EXPLORATION && next != null;
        boolean changed = mode != nextMode || positionAvailable != available
                || (gameClock == null ? nextClock != null : !gameClock.equals(nextClock))
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
        gameClock = nextClock;
        mode = nextMode;
        // Never a stale battlefield: leaving combat drops it, hold and all.
        /*
         * A fight starting is the last moment the party's own square is known:
         * combat takes the position away, so the square has to be read from the
         * frame before rather than from this one.
         */
        if (nextMode == MapMode.COMBAT && mode != MapMode.COMBAT
                && positionAvailable && state != null && listener != null) {
            AreaIdentity where = displayedArea();
            if (where != null) listener.onAmbush(where, state.y * 16 + state.x);
        }
        if (nextMode != MapMode.COMBAT) {
            combat = null; combatHold.reset();
            // A lit row must not outlive the grid that explained it.
            highlightedMember = -1; highlightUntil = 0; combatCell = 0;
        }
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
            touchReturn = returnTarget.contains(touchX, touchY);
            touchQuick = touchReturn ? -1 : quickAt(touchX, touchY);
            boolean onButton = touchReturn || touchQuick >= 0;
            touchMember = onButton ? -1 : pane().memberAt(touchX, touchY);
            touchCombatant = onButton || touchMember >= 0 ? -1 : combatantAt(touchX, touchY);
            touchHeader = !onButton && touchMember < 0 && touchCombatant < 0
                    && headerTarget.contains(touchX, touchY);
            touchParty = touchMember < 0 ? null : party;
            if (touchMember >= 0) postDelayed(partyLongPress, ViewConfiguration.getLongPressTimeout());
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
            /*
             * Two kinds of valid. Everything that reports outward needs a
             * listener to report to; lighting a row on the battle overview
             * does not, because nothing leaves the view -- it is the pane
             * answering a question about itself.
             */
            boolean gesture = event.getPointerId(0) == touchPointer
                    && (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0
                    && Math.hypot(event.getX() - touchX, event.getY() - touchY)
                        <= ViewConfiguration.get(getContext()).getScaledTouchSlop();
            boolean valid = gesture && listener != null;
            if (valid && touchQuick >= 0 && quickAt(event.getX(), event.getY()) == touchQuick
                    && party != null && touchQuick < party.members.size()) {
                // Unknown reads as off, so a first tap turns it on rather than
                // doing nothing the player can see.
                Boolean current = party.members.get(touchQuick).quick;
                Boolean queued = quickPending.desired(party.members.get(touchQuick));
                if (queued != null) current = queued;
                performClick();
                listener.onQuickToggled(touchQuick, !Boolean.TRUE.equals(current));
            } else if (valid && touchReturn && returnTarget.contains(event.getX(), event.getY())) {
                performClick();
                listener.onReturnPressed();
            } else if (valid && touchMember >= 0 && touchParty == party && party != null
                    && pane().memberAt(event.getX(), event.getY()) == touchMember) {
                PartyState.Member selected = party.members.get(touchMember);
                cancelTap(); performClick(); listener.onPartyMemberTapped(selected);
            } else if (gesture && touchHeader && headerTarget.contains(event.getX(), event.getY())) {
                if (positionAvailable) {
                    pingUntil = now() + PING_MS;
                    performClick();
                    refreshDescription();
                    invalidate();
                    postDelayed(() -> { pingUntil = 0; refreshDescription(); invalidate(); }, PING_MS + 50);
                }
            } else if (gesture && touchCombatant >= 0 && touchParty == null && party != null
                    && combatantAt(event.getX(), event.getY()) == touchCombatant
                    && touchCombatant < party.members.size()) {
                highlightedMember = touchCombatant;
                highlightUntil = now() + HIGHLIGHT_MS;
                performClick();
                refreshDescription();
                // One redraw to light it and one to let it go; no animation,
                // which an e-ink panel would smear rather than show.
                invalidate();
                postDelayed(() -> {
                    highlightedMember = -1; highlightUntil = 0;
                    refreshDescription(); invalidate();
                }, HIGHLIGHT_MS + 50);
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

    @Override public boolean performLongClick() {
        if (listener == null || touchMember < 0 || party == null || touchParty != party
                || touchMember >= party.members.size()) return false;
        PartyState.Member member = party.members.get(touchMember);
        cancelTap();
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        listener.onPartyMemberLongPressed(member);
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (listener != null) for (int i = 0; i < partyActions.size(); i++) {
            PartyState.Member member = partyActions.valueAt(i);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(partyActions.keyAt(i),
                    "Details for " + member.displayName() + ", " + member.classLabel() + ", " + member.conditionSummary()));
        }
        if (listener != null) for (int i = 0; i < partySheetActions.size(); i++)
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(partySheetActions.keyAt(i),
                    "Open game sheet for " + partySheetActions.valueAt(i).displayName()));
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        PartyState.Member sheet = partySheetActions.get(action);
        if (sheet != null && listener != null) {
            cancelTap(); listener.onPartyMemberLongPressed(sheet); return true;
        }
        PartyState.Member member = partyActions.get(action);
        if (member != null && listener != null) {
            cancelTap(); listener.onPartyMemberTapped(member); return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private void cancelTap() {
        removeCallbacks(partyLongPress);
        touchReturn = false; touchQuick = -1;
        touchPointer = touchTile = -1;
        touchMember = -1; touchParty = null; touchCombatant = -1; touchHeader = false;
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
        try {
            drawMap(canvas, pane);
            if (mirrorMessage && !gameMessage.isEmpty()) drawMessageMirror(canvas, pane);
        } finally { canvas.restore(); }
    }

    /**
     * The game's own Message-window text, mirrored in larger type over the lower
     * part of the map (F64) -- the same place the game keeps its message. 1984
     * Mac type at e-ink size is the hardest thing on the screen to read; this is
     * an option, off by default, because it covers screen the map wants.
     */
    private void drawMessageMirror(Canvas canvas, PartyPaneLayout pane) {
        float pad = 10 * density;
        float boxLeft = pad, boxRight = pane.mapWidth - pad, boxBottom = pane.mapHeight - pad;
        float text = 18 * density, lineHeight = text * 1.25f;
        ink.setTextSize(text); ink.setTextAlign(Paint.Align.LEFT); ink.setStyle(Paint.Style.FILL);
        float wrapWidth = boxRight - boxLeft - 2 * pad;
        java.util.List<String> lines = wrapMessage(gameMessage, wrapWidth);
        int maxLines = Math.max(1, (int) ((pane.mapHeight * 0.6f - 2 * pad) / lineHeight));
        boolean clipped = lines.size() > maxLines;
        int shown = Math.min(lines.size(), maxLines);
        float boxHeight = shown * lineHeight + 2 * pad;
        float boxTop = boxBottom - boxHeight;
        // A white card with a black border, so the text reads over the map ink.
        ink.setColor(Color.WHITE); canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, ink);
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(Math.max(1, density));
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, ink);
        ink.setStyle(Paint.Style.FILL);
        float baseline = boxTop + pad + text;
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            if (clipped && i == shown - 1) line = ellipsize(line, wrapWidth);
            canvas.drawText(line, boxLeft + pad, baseline, ink);
            baseline += lineHeight;
        }
    }

    /** Break the message into lines that fit the given width, on spaces and the game's own returns. */
    private java.util.List<String> wrapMessage(String message, float width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : message.replace('\t', ' ').split("\r")) {
            String rest = paragraph.trim();
            if (rest.isEmpty()) { lines.add(""); continue; }
            while (!rest.isEmpty()) {
                int fit = ink.breakText(rest, true, width, null);
                if (fit >= rest.length()) { lines.add(rest); break; }
                int space = rest.lastIndexOf(' ', fit);
                int cut = space > 0 ? space : Math.max(1, fit);
                lines.add(rest.substring(0, cut).trim());
                rest = rest.substring(cut).trim();
            }
        }
        return lines;
    }

    private String ellipsize(String line, float width) {
        if (ink.measureText(line) <= width) return line;
        String out = line;
        while (out.length() > 1 && ink.measureText(out + "…") > width) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    private void drawMap(Canvas canvas, PartyPaneLayout pane) {
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(14 * density);
        String title = state != null && state.area != null ? state.area.label() : "AREA MAP";
        /*
         * How much of this area is done, beside its name. A bare count of
         * walked squares is a number with no scale; every local area is
         * sixteen by sixteen, so the fraction is the thing a mapper wants --
         * whether there is much left. Only once there is something to report,
         * and never over a reference map, whose coverage belongs to wherever
         * the party actually is.
         */
        if (state != null && state.area != null && positionAvailable && exploration.visitedCount() > 0)
            title += " · " + MapProgress.badge(exploration.visitedCount());
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
        float button = 0;
        float available = Math.max(0, pane.mapWidth - 24 * density - button);
        /*
         * The status gets more of the header in a battle. "BATTLE · overview"
         * is a short title and the tally beside it is the long part -- six of
         * yours, six others, one down, one lost -- and at the ordinary 48% it
         * lost the casualties to an ellipsis, which are the words worth reading.
         */
        float share = mode == MapMode.COMBAT ? .66f : .48f;
        float statusWidth = Math.min(ink.measureText(status), available * share);
        String clockLabel = gameClock == null ? "" : gameClock.label();
        float clockWidth = gameClock == null ? 0 : Math.min(ink.measureText(clockLabel),
                Math.max(0, Math.min(available * .42f, available - statusWidth - 24 * density)));
        float clockSpace = gameClock == null ? 0 : clockWidth + 12 * density;
        ink.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fitHeaderText(title, available - statusWidth - clockSpace - 12 * density),
                12 * density + button, 22 * density, ink);
        ink.setTextAlign(Paint.Align.RIGHT);
        if (gameClock != null)
            canvas.drawText(fitHeaderText(clockLabel, clockWidth),
                    pane.mapWidth - 24 * density - statusWidth, 22 * density, ink);
        canvas.drawText(fitHeaderText(status, statusWidth), pane.mapWidth - 12 * density, 22 * density, ink);
        // The whole header row is the target; it is a big thing to hit and it
        // does nothing dangerous.
        headerTarget.set(0, 0, pane.mapWidth, 30 * density);
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
        if (cell < 3) { combatCell = 0; return; }
        float left = viewport.left;
        ink.setTextSize(Math.min(11 * density, cell * .7f));
        ink.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 16; i += 4) {
            canvas.drawText(Integer.toString(i), left + (i + .5f) * cell, top - 5 * density, ink);
            canvas.drawText(Integer.toString(i), left - 11 * density, top + (i + .7f) * cell, ink);
        }
        artwork.drawExploration(canvas, state.map, exploration, visitedOnly, footprints, left, top, cell, density);
        artwork.drawMarkers(canvas, flags, state, positionAvailable, left, top, cell, density);
        if (positionAvailable && pinging()) drawPing(canvas, left, top, cell);
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
            combatCell = 0;
            canvas.drawText(fitHeaderText(MapMode.COMBAT.explanation(), available),
                    pane.mapWidth / 2f, pane.mapHeight / 2f, ink);
            return;
        }
        float margin = 26 * density, caption = 22 * density;
        float usableWidth = pane.mapWidth - 2 * margin;
        float usableHeight = pane.mapHeight - margin - caption - 10 * density;
        if (usableWidth <= 0 || usableHeight <= 0) { combatCell = 0; return; }
        float cell = Math.min(usableWidth / battle.width(), usableHeight / battle.height());
        cell = Math.min(cell, 34 * density);
        if (cell < 3) return;
        float gridWidth = cell * battle.width(), gridHeight = cell * battle.height();
        float left = (pane.mapWidth - gridWidth) / 2f, top = margin + (usableHeight - gridHeight) / 2f;
        combatLeft = left; combatTop = top; combatCell = cell;

        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1, density));
        canvas.drawRect(left, top, left + gridWidth, top + gridHeight, ink);
        /*
         * Whose turn it is, ringed. Everything else on this grid says what
         * somebody is; this says what the game is waiting for. The name comes
         * from the game's own Combat Message window, and the party's spots are
         * in the same order as the party's rows, so the nth party marker is the
         * nth character.
         */
        int actingAt = -1;
        if (battle.acting != null && party != null)
            for (int i = 0; i < party.members.size() && actingAt < 0; i++)
                if (battle.acting.equals(party.members.get(i).name)) actingAt = i;
        int index = -1;
        for (CombatSnapshot.Spot spot : battle.spots()) {
            index++;
            float cx = left + (spot.x - battle.left + .5f) * cell;
            float cy = top + (spot.y - battle.top + .5f) * cell;
            float radius = cell * .32f;
            if (actingAt >= 0 && partyIndexOf(battle, index) == actingAt) {
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(Math.max(1, density));
                canvas.drawCircle(cx, cy, radius * 1.9f, ink);
            }
            ink.setStyle(spot.party && !spot.fallen ? Paint.Style.FILL : Paint.Style.STROKE);
            ink.setStrokeWidth(Math.max(1.5f * density, cell * .09f));
            if (spot.fallen && spot.savable()) {
                // A diagonal cross: one of yours, down but still worth reaching.
                // Nothing else on this grid is diagonal.
                canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, ink);
                canvas.drawLine(cx - radius, cy + radius, cx + radius, cy - radius, ink);
            } else if (spot.fallen) {
                // An upright cross for dead or petrified: past bandaging, and a
                // different shape rather than a heavier version of the same one,
                // because weight alone does not read at this size on e-ink.
                canvas.drawLine(cx, cy - radius, cx, cy + radius, ink);
                canvas.drawLine(cx - radius * .7f, cy - radius * .3f,
                                cx + radius * .7f, cy - radius * .3f, ink);
            } else if (spot.party) canvas.drawCircle(cx, cy, radius, ink);
            else canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, ink);
        }
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(Math.min(11 * density, cell * .8f));
        canvas.drawText(fitHeaderText(battle.left + "," + battle.top + " to "
                        + battle.right + "," + battle.bottom, available),
                pane.mapWidth / 2f, top - 8 * density, ink);
        ink.setTextSize(11 * density);
        canvas.drawText(fitHeaderText(
                        (battle.fallenCount() == 0
                            ? "Filled is yours · reference only, tap the game below to act"
                            : "Filled is yours · ✕ still savable · ✝ past saving"), available),
                pane.mapWidth / 2f, pane.mapHeight - 7 * density, ink);
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

    /**
     * The party row a tap on the battle overview points at, or -1.
     *
     * Only the party's own markers answer: a monster has no row to light up,
     * and pretending otherwise would be the first step towards naming one.
     */
    private int combatantAt(float x, float y) {
        CombatSnapshot battle = combat;
        if (mode != MapMode.COMBAT || battle == null || party == null || combatCell < 3) return -1;
        int index = -1, found = -1;
        float closest = Float.MAX_VALUE;
        for (CombatSnapshot.Spot spot : battle.spots()) {
            index++;
            if (!spot.party) continue;
            float cx = combatLeft + (spot.x - battle.left + .5f) * combatCell;
            float cy = combatTop + (spot.y - battle.top + .5f) * combatCell;
            float distance = (float) Math.hypot(x - cx, y - cy);
            // Its own square, and the nearest one when squares are tiny.
            if (distance <= combatCell * .5f && distance < closest) {
                closest = distance; found = partyIndexOf(battle, index);
            }
        }
        return found >= 0 && found < party.members.size() ? found : -1;
    }

    /** True while the party's square is still ringed. */
    private boolean pinging() { return pingUntil > 0 && now() < pingUntil; }

    /**
     * Two rings around the party's own square, briefly.
     *
     * Rings rather than a fill, because the square underneath is the thing
     * being pointed at and filling it would hide the arrow, the footprints and
     * anything written there. Two of them because one at this size reads as
     * another map symbol.
     */
    private void drawPing(Canvas canvas, float left, float top, float cell) {
        float cx = left + (state.x + .5f) * cell, cy = top + (state.y + .5f) * cell;
        ink.setStyle(Paint.Style.STROKE);
        ink.setColor(Color.BLACK);
        ink.setStrokeWidth(Math.max(1.5f * density, cell * .09f));
        canvas.drawCircle(cx, cy, cell * 1.1f, ink);
        canvas.drawCircle(cx, cy, cell * 1.7f, ink);
        ink.setStyle(Paint.Style.FILL);
    }

    /** True while a tapped combatant's row is still lit. */
    private boolean highlightShowing() {
        return highlightedMember >= 0 && now() < highlightUntil;
    }

    /**
     * The highlight is the one thing here that ends on a clock rather than on a
     * reading, so the text describing it can go stale with no new sample to
     * trigger a rebuild. Settle it before anyone reads the description.
     */
    @Override public CharSequence getContentDescription() {
        if (highlightedMember >= 0 && !highlightShowing()) {
            highlightedMember = -1; highlightUntil = 0; refreshDescription();
        }
        if (pingUntil > 0 && !pinging()) { pingUntil = 0; refreshDescription(); }
        return super.getContentDescription();
    }

    /** How many party spots precede this one, or -1 if it is not one of ours. */
    private int partyIndexOf(CombatSnapshot battle, int index) {
        int seen = 0;
        for (int i = 0; i < battle.spots().size(); i++) {
            if (!battle.spots().get(i).party) continue;
            if (i == index) return seen;
            seen++;
        }
        return -1;
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
        float size = 16 * unit;
        float top = oneLineParty
                ? p.rowTop(index) + (p.rowHeight - size) / 2
                : p.rowTop(index) + (p.rowHeight - 48 * unit) / 2;
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

    private boolean selectedOutsideCombat(int index) {
        return party != null && party.selectedIndex == index
                && (mode == MapMode.EXPLORATION || mode == MapMode.CAMP || mode == MapMode.WILDERNESS);
    }

    private boolean markedPartyRow(int index, PartyState.Member member) {
        return mode == MapMode.COMBAT ? combat != null && combat.isActing(member.name)
                : selectedOutsideCombat(index);
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
        /*
         * The heading says what the party is waiting on, when it is waiting on
         * anything. All of it is already in the rows below -- a T here, a word
         * there, a bar shorter than it was -- and all of it is easy to miss
         * when six rows compete for one glance.
         */
        canvas.drawText(fitHeaderText(PartyChores.summary(party), p.columnWidth - 20 * unit),
                p.partyLeft + 10 * unit, p.partyTop + 16 * unit, ink);
        for (int i=1;i<p.columns;i++) {
            float divider=p.partyLeft+i*p.columnWidth;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(density);
            canvas.drawLine(divider,p.partyTop+p.headerHeight,divider,p.partyTop+p.partyHeight,ink);
        }
        /*
         * "W" for a character load has slowed. The game never says so anywhere
         * you are looking; it is the sort of thing you discover by being slow
         * in a fight. PartyState.slowedByLoad carries the reasoning.
         */
        for (int i=0;i<p.visibleMembers();i++) {
            PartyState.Member member=party.members.get(i);
            boolean slowed = party.slowedByLoad(member);
            float column=p.columnLeft(i);
            float left=column+44*unit, right=column+p.columnWidth-10*unit;
            if (oneLineParty) { drawOneLineRow(canvas, p, i, member, slowed, unit, column); continue; }
            float top=p.rowTop(i)+(p.rowHeight-48*unit)/2;
            if (member.badge().isEmpty()) drawClassSymbol(canvas, member, column+9*unit, top+8*unit, 27*unit);
            else drawConditionBadge(canvas,member.badge(),column+9*unit,top+8*unit,27*unit);
            /*
             * The selected/acting character's row, marked with a left-edge bar.
             * In a fight the question is not who is hurt but who the game is
             * waiting for, and the name is in the Combat Message window.
             */
            if (markedPartyRow(i, member)) {
                ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK);
                canvas.drawRect(column, p.rowTop(i) + 2 * unit,
                        column + 3 * unit, p.rowTop(i) + p.rowHeight - 2 * unit, ink);
            }
            /*
             * The row somebody just pointed at on the battle grid. A box, not
             * the acting character's bar and not a fill: it has to be legible
             * next to that bar without being confused for it, and inverting a
             * row on e-ink costs a full-row refresh to say something that lasts
             * three seconds.
             */
            if (highlightedMember == i && highlightShowing()) {
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(Math.max(1.5f * density, 2 * unit));
                ink.setColor(Color.BLACK);
                canvas.drawRect(column + 4 * unit, p.rowTop(i) + 2 * unit,
                        column + p.columnWidth - 4 * unit, p.rowTop(i) + p.rowHeight - 2 * unit, ink);
            }
            quickSquare(p, i, unit, quickButton);
            drawQuick(canvas, quickButton, member.quick, unit);
            drawPendingQuick(canvas, quickButton, member, unit);
            /*
             * Two marks the game already knows and never puts in front of you.
             *
             * "T" when the game's own experience threshold for one of this
             * character's classes has been passed, which saves a speculative
             * walk to the training hall. "R" when spells were chosen through
             * the Memorize screen and are still waiting on rest, which saves
             * making camp to find out nobody needed it. Neither is advice: the
             * app is not saying train or rest, only that the game would let you.
             *
             * They sit just left of the Q and take their width out of the
             * name's, so a long name shortens rather than running into them.
             */
            String marks = (member.readyToTrain() ? "T" : "")
                    + (member.spellsAwaitingRestTotal() > 0 ? "R" : "")
                    + (slowed ? "W" : "");
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK);
            float marksWidth = 0;
            if (!marks.isEmpty()) {
                ink.setTextSize(10 * unit);
                marksWidth = ink.measureText(marks) + 5 * unit;
                ink.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(marks, right - quickButton.width() - 4 * unit,
                        quickButton.bottom - 4 * unit, ink);
                ink.setTextAlign(Paint.Align.LEFT);
            }
            ink.setTextSize(13*unit);
            float available=Math.max(0,right-left-quickButton.width()-marksWidth-6*unit);
            String label = member.displayName();
            int chars=ink.breakText(label,true,available,null);
            String name=chars==label.length()?label:chars>1?label.substring(0,chars-1)+"…":"";
            ink.setTextAlign(Paint.Align.LEFT);canvas.drawText(name,left,top+14*unit,ink);
            ink.setTextSize(11*unit);
            // A narrow strip cell has no room for both readouts; health wins.
            boolean compact = p.columnWidth < PartyPaneLayout.COMPACT_COLUMN * unit;
            canvas.drawText((compact ? "" : "HP ")+member.currentHp+"/"+member.maxHp,left,top+28*unit,ink);
            /*
             * Say it, do not spell it. A character who is down has carried a
             * one-letter badge for a while -- `!` dying, `X` dead -- which is
             * fine once you know it and no use when you are scanning for who to
             * bandage. The word takes the armour class's place, because armour
             * class is the least interesting number about someone who is dying,
             * and it is drawn even in a narrow strip where the class is not.
             */
            String down = member.downLabel();
            ink.setTextAlign(Paint.Align.RIGHT);
            if (down != null) {
                String fitted = down;
                float room = Math.max(0, right - left - ink.measureText("00/00") - 6 * unit);
                if (ink.measureText(fitted) > room) fitted = down.substring(0, 4);
                canvas.drawText(fitted, right, top + 28 * unit, ink);
            } else if (!compact) {
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

    /**
     * A compact one-line party row (F69): class icon, name, HP (and armour
     * class where it fits) on a single line, with a thin health bar along the
     * bottom edge. Same information as the two-line row, in about 30dp, so a big
     * party keeps one column and the map keeps its full width. Opt-in.
     */
    private final android.graphics.RectF oneLineQuick = new android.graphics.RectF();
    private void drawOneLineRow(Canvas canvas, PartyPaneLayout p, int i,
            PartyState.Member member, boolean slowed, float unit, float column) {
        float rowTop = p.rowTop(i), rowBottom = rowTop + p.rowHeight;
        float colRight = column + p.columnWidth;
        // The acting character's bar and a pointed-at row's box, as in two-line.
        if (markedPartyRow(i, member)) {
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK);
            canvas.drawRect(column, rowTop + 2 * unit, column + 3 * unit, rowBottom - 2 * unit, ink);
        }
        if (highlightedMember == i && highlightShowing()) {
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(Math.max(1.5f * density, 2 * unit)); ink.setColor(Color.BLACK);
            canvas.drawRect(column + 4 * unit, rowTop + 2 * unit, colRight - 4 * unit, rowBottom - 2 * unit, ink);
        }
        // A small class symbol or condition badge, vertically centred.
        float icon = Math.min(20 * unit, p.rowHeight - 6 * unit);
        float iconTop = rowTop + (p.rowHeight - icon) / 2;
        if (member.badge().isEmpty()) drawClassSymbol(canvas, member, column + 5 * unit, iconTop, icon);
        else drawConditionBadge(canvas, member.badge(), column + 5 * unit, iconTop, icon);

        float left = column + 9 * unit + icon;
        float right = colRight - 10 * unit;
        // The quick square sits at the right, then the T/R/W marks to its left.
        quickSquare(p, i, unit, oneLineQuick);
        drawQuick(canvas, oneLineQuick, member.quick, unit);
        drawPendingQuick(canvas, oneLineQuick, member, unit);
        // Text baseline centred in the space above the bottom bar.
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(11 * unit);
        Paint.FontMetrics fm = ink.getFontMetrics();
        float baseline = rowTop + (p.rowHeight - 5 * unit) / 2 - (fm.ascent + fm.descent) / 2;

        float cursor = oneLineQuick.left - 4 * unit;
        String marks = (member.readyToTrain() ? "T" : "")
                + (member.spellsAwaitingRestTotal() > 0 ? "R" : "")
                + (slowed ? "W" : "");
        if (!marks.isEmpty()) {
            ink.setTextSize(10 * unit); ink.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(marks, cursor, baseline, ink);
            cursor -= ink.measureText(marks) + 4 * unit;
        }
        // HP, and armour class when there is room; a down word takes AC's place.
        ink.setTextSize(11 * unit); ink.setTextAlign(Paint.Align.RIGHT);
        String down = member.downLabel();
        String hp = member.currentHp + "/" + member.maxHp;
        String ac = down != null ? down
                : (p.columnWidth >= PartyPaneLayout.COMPACT_COLUMN * unit
                        ? "AC " + (member.armorClass == null ? "—" : member.armorClass) : null);
        if (ac != null) { canvas.drawText(ac, cursor, baseline, ink); cursor -= ink.measureText(ac) + 6 * unit; }
        canvas.drawText(hp, cursor, baseline, ink);
        cursor -= ink.measureText(hp) + 6 * unit;
        // The name fills what is left, truncated with an ellipsis.
        ink.setTextAlign(Paint.Align.LEFT); ink.setTextSize(13 * unit);
        float room = Math.max(0, cursor - left);
        String label = member.displayName();
        int chars = ink.breakText(label, true, room, null);
        String name = chars == label.length() ? label
                : chars > 1 ? label.substring(0, chars - 1) + "…" : "";
        canvas.drawText(name, left, baseline, ink);
        // A thin health bar along the bottom edge of the row.
        float barTop = rowBottom - 4 * unit, barBottom = rowBottom - 1.5f * unit;
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
        canvas.drawRect(left, barTop, right, barBottom, ink);
        ink.setStyle(Paint.Style.FILL);
        float fraction = Math.max(0, Math.min(1, member.healthFraction()));
        if (fraction > 0) canvas.drawRect(left, barTop, left + (right - left) * fraction, barBottom, ink);
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
