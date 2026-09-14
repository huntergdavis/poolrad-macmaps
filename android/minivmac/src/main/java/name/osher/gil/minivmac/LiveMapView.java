package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.MapViewport;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.notebook.NoteIcon;

/** Static black-on-white cartography: no animation, blink, or network access. */
public final class LiveMapView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MapArtwork artwork = new MapArtwork();
    private final float density;
    private PoolRadState state;
    private PartyState party;
    private boolean positionAvailable;
    private String notebook = "Loading notebook…";
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private Listener listener;
    private int touchPointer = -1, touchTile = -1;
    private float touchX, touchY;
    private String touchArea;
    private boolean preciseTouch;

    public interface Listener {
        void onAreaChanged(AreaIdentity area);
        void onTileTapped(AreaIdentity area, int x, int y);
        default void onNearbyFlagsTapped(AreaIdentity area, int x, int y, int[] candidates) {
            onTileTapped(area, x, y);
        }
    }

    public void setListener(Listener value) { listener = value; }
    public AreaIdentity currentArea() { return positionAvailable && state != null ? state.area : null; }
    public PoolRadState snapshot() { return positionAvailable ? state : null; }
    public void showPartySample(byte[] sample) {
        PartyState next = PartyState.parse(sample);
        if (party == null ? next == null : party.sameDisplay(next)) return;
        party = next; cancelTap(); refreshDescription(); invalidate();
    }
    private PartyPaneLayout pane() { return new PartyPaneLayout(getWidth(), getHeight(), density, party == null ? 0 : party.members.size()); }
    private MapViewport viewport() { PartyPaneLayout p=pane(); return new MapViewport(p.mapWidth,p.mapHeight,density); }
    public void showNotebook(String label, Map<Integer, NoteIcon> tiles) {
        Map<Integer, NoteIcon> copy = new HashMap<>(tiles);
        if (notebook.equals(label) && flags.equals(copy)) return;
        cancelTap(); notebook = label; flags = copy; refreshDescription(); invalidate();
    }

    private void refreshDescription() {
        String status = state == null ? "Waiting for party" : !positionAvailable ? "Position unavailable"
                : (state.area == null ? "Unidentified area" : state.area.label()) + ". Party at " + state.positionLabel();
        StringBuilder health = new StringBuilder();
        if (party != null) for (PartyState.Member member : party.members)
            health.append(' ').append(member.name).append(": ").append(member.currentHp).append(" of ").append(member.maxHp).append(" HP.");
        setContentDescription(status + ". Tap a tile to add a note; tap a symbol to reopen it. "
                + notebook + ". " + flags.size() + " flags." + health);
    }

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
        AreaIdentity previous = currentArea();
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
        AreaIdentity current = currentArea();
        if (!(previous == null ? current == null : current != null && previous.id().equals(current.id()))) {
            cancelTap();
            flags = Collections.emptyMap();
            if (listener != null) listener.onAreaChanged(current);
        }
        refreshDescription();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchPointer = event.getPointerId(0); touchX = event.getX(); touchY = event.getY();
            preciseTouch = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER;
            touchTile = viewport().tileAt(touchX, touchY);
            AreaIdentity area = currentArea(); touchArea = area == null ? null : area.id();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE) {
            int pointer = event.findPointerIndex(touchPointer);
            if (pointer < 0 || Math.hypot(event.getX(pointer) - touchX, event.getY(pointer) - touchY)
                    > ViewConfiguration.get(getContext()).getScaledTouchSlop()) touchTile = -1;
        } else if (action == MotionEvent.ACTION_UP) {
            AreaIdentity area = currentArea();
            int tile = viewport().tileAt(event.getX(), event.getY());
            if (tile >= 0 && tile == touchTile && event.getPointerId(0) == touchPointer
                    && (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0
                    && Math.hypot(event.getX() - touchX, event.getY() - touchY)
                        <= ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    && listener != null
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

    private void cancelTap() {
        touchPointer = touchTile = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH); cancelTap();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelTap();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PartyPaneLayout pane = pane();
        drawParty(canvas, pane);
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(14 * density);
        ink.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(state != null && state.area != null ? state.area.label() : "AREA MAP", 12 * density, 22 * density, ink);
        ink.setTextAlign(Paint.Align.RIGHT);
        String status = state == null ? "Waiting for party"
                : positionAvailable ? state.positionLabel() : "Last area · position unavailable";
        canvas.drawText(status, pane.mapWidth - 12 * density, 22 * density, ink);
        ink.setStrokeWidth(density);
        canvas.drawLine(0, getHeight() - density, getWidth(), getHeight() - density, ink);
        if (state == null) {
            ink.setTextAlign(Paint.Align.CENTER);
            ink.setTextSize(13 * density);
            canvas.drawText("Load a party in Pool of Radiance v1.1", pane.mapWidth / 2f,
                    Math.max(48 * density, pane.mapHeight / 2f), ink);
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
        artwork.drawGeometry(canvas, state.map, left, top, cell, density);
        artwork.drawMarkers(canvas, flags, state, positionAvailable, left, top, cell, density);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextSize(10 * density); ink.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("North up · outlined gaps are doors", pane.mapWidth / 2f, pane.mapHeight - 23 * density, ink);
        ink.setTextSize(11 * density);
        canvas.drawText("Tap a tile or symbol · " + notebook,
                pane.mapWidth / 2f, pane.mapHeight - 7 * density, ink);
    }

    private void drawParty(Canvas canvas, PartyPaneLayout p) {
        if (party == null || p.partyWidth <= 0 || p.partyHeight <= 0) return;
        canvas.save(); canvas.clipRect(p.partyLeft,p.partyTop,p.partyLeft+p.partyWidth,p.partyTop+p.partyHeight);
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
        if (p.columns == 1) canvas.drawLine(p.partyLeft,p.partyTop,p.partyLeft,p.partyTop+p.partyHeight,ink);
        else canvas.drawLine(p.partyLeft,p.partyTop,p.partyLeft+p.partyWidth,p.partyTop,ink);
        ink.setStyle(Paint.Style.FILL); ink.setTextAlign(Paint.Align.LEFT); ink.setTextSize(11*density);
        canvas.drawText("PARTY · CURRENT / MAX HP",p.partyLeft+10*density,p.partyTop+15*density,ink);
        float rowHeight=(p.partyHeight-22*density)/p.rows, columnWidth=p.partyWidth/(float)p.columns;
        if (rowHeight < 16*density) { canvas.restore(); return; }
        for (int i=0;i<party.members.size();i++) {
            PartyState.Member member=party.members.get(i);
            float left=p.partyLeft+(i%p.columns)*columnWidth+10*density;
            float right=p.partyLeft+(i%p.columns+1)*columnWidth-10*density;
            float top=p.partyTop+22*density+(i/p.columns)*rowHeight;
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK); ink.setTextSize(12*density);
            String hp=member.currentHp+"/"+member.maxHp;
            float available=Math.max(0,right-left-ink.measureText(hp)-8*density);
            int chars=ink.breakText(member.name,true,available,null);
            String name=chars==member.name.length()?member.name:chars>1?member.name.substring(0,chars-1)+"…":"";
            ink.setTextAlign(Paint.Align.LEFT);canvas.drawText(name,left,top+12*density,ink);
            ink.setTextAlign(Paint.Align.RIGHT);canvas.drawText(hp,right,top+12*density,ink);
            float barTop=top+17*density,barBottom=Math.min(top+23*density,top+rowHeight-2*density);
            if (right>left && barBottom>barTop) {
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(density);canvas.drawRect(left,barTop,right,barBottom,ink);
                ink.setStyle(Paint.Style.FILL);
                float fraction=Math.max(0,Math.min(1,member.healthFraction()));
                if(fraction>0)canvas.drawRect(left,barTop,left+(right-left)*fraction,barBottom,ink);
            }
        }
        canvas.restore();
    }
}
