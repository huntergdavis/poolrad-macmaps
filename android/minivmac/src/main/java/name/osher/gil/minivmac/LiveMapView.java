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
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.mapper.PartyState;
import name.osher.gil.minivmac.mapper.PartyPaneLayout;
import name.osher.gil.minivmac.notebook.NoteIcon;

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
    private final SparseArray<PartyState.Member> partyActions = new SparseArray<>();
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
    private PartyState touchParty;
    private int touchMember = -1;

    public interface Listener {
        void onAreaChanged(AreaIdentity area);
        void onTileTapped(AreaIdentity area, int x, int y);
        default void onNearbyFlagsTapped(AreaIdentity area, int x, int y, int[] candidates) {
            onTileTapped(area, x, y);
        }
        default void onPartyMemberTapped(PartyState.Member member) { }
    }

    public void setListener(Listener value) { listener = value; }
    public AreaIdentity currentArea() { return positionAvailable && state != null ? state.area : null; }
    public PoolRadState snapshot() { return positionAvailable ? state : null; }
    public void showPartySample(byte[] sample) {
        PartyState next = PartyState.parse(sample);
        if (party == null ? next == null : party.sameDisplay(next)) return;
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

    private void refreshDescription() {
        String status = state == null ? "Waiting for party" : !positionAvailable ? "Position unavailable"
                : (state.area == null ? "Unidentified area" : state.area.label()) + ". Party at " + state.positionLabel();
        StringBuilder health = new StringBuilder();
        if (party != null) for (PartyState.Member member : party.members)
            health.append(' ').append(member.name).append(": ").append(member.currentHp).append(" of ").append(member.maxHp)
                    .append(" HP; AC ").append(member.armorClass == null ? "unavailable" : member.armorClass)
                    .append("; ").append(member.classLabel()).append('.');
        setContentDescription(status + ". Tap a tile to add a note; tap a symbol to reopen it. "
                + notebook + ". " + flags.size() + " flags." + health);
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
            cancelTap();
            touchPointer = event.getPointerId(0); touchX = event.getX(); touchY = event.getY();
            preciseTouch = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER;
            touchMember = pane().memberAt(touchX, touchY);
            touchParty = touchMember < 0 ? null : party;
            touchTile = touchMember < 0 ? viewport().tileAt(touchX, touchY) : -1;
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
            if (valid && touchMember >= 0 && touchParty == party && party != null
                    && pane().memberAt(event.getX(), event.getY()) == touchMember) {
                PartyState.Member selected = party.members.get(touchMember);
                cancelTap(); performClick(); listener.onPartyMemberTapped(selected);
            } else if (valid && tile >= 0 && tile == touchTile
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
                    "Details for " + member.name + ", " + member.classLabel()));
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
        if (party == null || p.rows == 0) return;
        canvas.save(); canvas.clipRect(p.partyLeft,p.partyTop,p.partyLeft+p.partyWidth,p.partyTop+p.partyHeight);
        float unit = density * textScale;
        ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
        canvas.drawLine(p.partyLeft,p.partyTop,p.partyLeft,p.partyTop+p.partyHeight,ink);
        ink.setStyle(Paint.Style.FILL); ink.setTextAlign(Paint.Align.LEFT); ink.setTextSize(10*unit);
        canvas.drawText("PARTY · TAP FOR DETAILS",p.partyLeft+10*unit,p.partyTop+16*unit,ink);
        for (int i=0;i<party.members.size();i++) {
            PartyState.Member member=party.members.get(i);
            float left=p.partyLeft+44*unit, right=p.partyLeft+p.partyWidth-10*unit;
            float top=p.rowTop(i)+(p.rowHeight-48*unit)/2;
            drawClassSymbol(canvas, member, p.partyLeft+9*unit, top+8*unit, 27*unit);
            ink.setStyle(Paint.Style.FILL); ink.setColor(Color.BLACK); ink.setTextSize(13*unit);
            float available=Math.max(0,right-left);
            int chars=ink.breakText(member.name,true,available,null);
            String name=chars==member.name.length()?member.name:chars>1?member.name.substring(0,chars-1)+"…":"";
            ink.setTextAlign(Paint.Align.LEFT);canvas.drawText(name,left,top+14*unit,ink);
            ink.setTextSize(11*unit);
            canvas.drawText("HP "+member.currentHp+"/"+member.maxHp,left,top+28*unit,ink);
            ink.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("AC "+(member.armorClass==null ? "—" : member.armorClass),right,top+28*unit,ink);
            float barTop=top+34*unit,barBottom=top+40*unit;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(density);
            canvas.drawRect(left,barTop,right,barBottom,ink);
            ink.setStyle(Paint.Style.FILL);
            float fraction=Math.max(0,Math.min(1,member.healthFraction()));
            if(fraction>0)canvas.drawRect(left,barTop,left+(right-left)*fraction,barBottom,ink);
        }
        canvas.restore();
    }

    /** Original monochrome class marks; unknown classes remain a neutral question mark. */
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
