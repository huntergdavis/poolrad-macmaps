package name.osher.gil.minivmac;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import name.osher.gil.minivmac.mapper.GeoMap;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.NoteIcon;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/** Shared map artwork, extracted from LiveMapView. Labels and user ink are separate layers. */
public final class MapArtwork {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private ExplorationTrail preparedTrail;
    private final int[] latestFrom = new int[GeoMap.WIDTH * GeoMap.WIDTH];

    public interface TileVisibility { boolean visible(int tile); }

    public void drawGeometry(Canvas canvas, GeoMap map, float left, float top,
            float cell, float density) {
        drawGeometry(canvas,map,null,left,top,cell,density);
    }

    /** A hidden neighbor never contributes its own wall or door to an explored tile. */
    public void drawGeometry(Canvas canvas, GeoMap map, TileVisibility visible, float left, float top,
            float cell, float density) {
        if (map == null || !(cell > 0) || !(density > 0)) return;
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        for (int y = 0; y <= GeoMap.WIDTH; y++) for (int x = 0; x <= GeoMap.WIDTH; x++)
            canvas.drawCircle(left + x * cell, top + y * cell, .65f * density, ink);
        for (int y = 0; y < GeoMap.WIDTH; y++) for (int x = 0; x < GeoMap.WIDTH; x++) {
            if(visible!=null && !visible.visible(y*GeoMap.WIDTH+x)) continue;
            for (int direction = 0; direction < 4; direction++) {
                if (map.edgeKind(x, y, direction) == GeoMap.EdgeKind.OPEN) continue;
                float x1 = left + x * cell, y1 = top + y * cell;
                float x2 = x1, y2 = y1;
                if (direction == 0 || direction == 2) {
                    if (direction == 2) y1 += cell;
                    x2 += cell; y2 = y1;
                } else {
                    if (direction == 1) x1 += cell;
                    y2 += cell; x2 = x1;
                }
                ink.setColor(Color.BLACK);
                ink.setStrokeWidth(Math.max(1.5f * density, cell * .07f));
                canvas.drawLine(x1, y1, x2, y2, ink);
            }
        }
        // Doors are painted after all walls so adjacent cells cannot close their openings.
        for (int y = 0; y < GeoMap.WIDTH; y++) for (int x = 0; x < GeoMap.WIDTH; x++) {
            if(visible!=null && !visible.visible(y*GeoMap.WIDTH+x)) continue;
            for (int direction = 0; direction < 4; direction++) {
                if (map.edgeKind(x, y, direction) != GeoMap.EdgeKind.DOORWAY) continue;
                float cx = left + (x + .5f) * cell, cy = top + (y + .5f) * cell;
                float dx = (direction == 1 ? .5f : direction == 3 ? -.5f : 0) * cell;
                float dy = (direction == 2 ? .5f : direction == 0 ? -.5f : 0) * cell;
                float hw = (direction % 2 == 0 ? .24f : .09f) * cell;
                float hh = (direction % 2 == 0 ? .09f : .24f) * cell;
                ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.FILL);
                canvas.drawRect(cx + dx - hw, cy + dy - hh, cx + dx + hw, cy + dy + hh, ink);
                ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(density);
                canvas.drawRect(cx + dx - hw, cy + dy - hh, cx + dx + hw, cy + dy + hh, ink);
            }
        }
    }

    /**
     * Live-map replacement for drawGeometry, not an extra overlay after unfiltered geometry.
     * Paint order: visited stipple, feet, walls/doors; caller then paints manual markers/party.
     * Note sheets keep calling the original unfiltered drawGeometry overload.
     */
    public void drawExploration(Canvas canvas, GeoMap map, ExplorationTrail trail,
            boolean visitedOnly, boolean footprints, float left,float top,float cell,float density) {
        if(map==null || !(cell>0) || !(density>0) || Float.isInfinite(cell*GeoMap.WIDTH) || Float.isInfinite(density)
                || Float.isNaN(left) || Float.isNaN(top) || Float.isInfinite(left) || Float.isInfinite(top)) return;
        int saved=canvas.save();
        try {
            canvas.clipRect(left,top,left+GeoMap.WIDTH*cell,top+GeoMap.WIDTH*cell);
            ink.setColor(Color.WHITE);ink.setStyle(Paint.Style.FILL);
            canvas.drawRect(left,top,left+GeoMap.WIDTH*cell,top+GeoMap.WIDTH*cell,ink);
            if(trail!=null) {
                ink.setColor(Color.BLACK);ink.setStyle(Paint.Style.FILL);
                float dot=Math.min(cell*.045f,Math.max(.55f*density,cell*.022f));
                for(int tile=0;tile<256;tile++) if(trail.visited(tile)) {
                    float x=left+(tile%16)*cell,y=top+(tile/16)*cell;
                    canvas.drawCircle(x+.2f*cell,y+.2f*cell,dot,ink);
                    canvas.drawCircle(x+.8f*cell,y+.2f*cell,dot,ink);
                    canvas.drawCircle(x+.2f*cell,y+.8f*cell,dot,ink);
                    canvas.drawCircle(x+.8f*cell,y+.8f*cell,dot,ink);
                }
                if(footprints && cell>=12*density) {
                    prepareTrail(trail);
                    for(int tile=0;tile<256;tile++) {
                        int from=latestFrom[tile];
                        if(from<0 || !trail.visited(tile)) continue;
                        int dx=tile%16-from%16,dy=tile/16-from/16;
                        if(Math.abs(dx)+Math.abs(dy)!=1) continue; // No row-wrap or gap arrows.
                        float degrees=dx==1 ? 90 : dx==-1 ? 270 : dy==1 ? 180 : 0;
                        drawFeet(canvas,left+(tile%16+.5f)*cell,top+(tile/16+.5f)*cell,cell,degrees);
                    }
                }
            }
            TileVisibility visibility=visitedOnly ? tile -> trail!=null && trail.visited(tile) : null;
            drawGeometry(canvas,map,visibility,left,top,cell,density);
        } finally { canvas.restoreToCount(saved); }
    }

    private void prepareTrail(ExplorationTrail trail) {
        if(preparedTrail==trail) return;
        Arrays.fill(latestFrom,-1);
        // A footprint represents the latest recorded travel into this tile, not
        // an inferred arrival after an observation gap. Anchors contain no
        // movement: they neither invent a new direction nor erase recorded feet.
        for(ExplorationTrail.Step step:trail.steps)
            if(step.to>=0 && step.to<256 && step.from>=0 && step.from<256)
                latestFrom[step.to]=step.from;
        preparedTrail=trail;
    }

    /** Two staggered soles with separate heels, facing north before rotation. */
    private void drawFeet(Canvas canvas,float cx,float cy,float cell,float degrees) {
        int saved=canvas.save();
        try {
            canvas.translate(cx,cy);canvas.rotate(degrees);canvas.scale(cell,cell);
            ink.setColor(Color.BLACK);ink.setStyle(Paint.Style.FILL);
            canvas.drawOval(-.24f,-.32f,-.045f,-.035f,ink);
            canvas.drawOval(-.215f,.005f,-.075f,.145f,ink);
            canvas.drawOval(.045f,-.145f,.24f,.14f,ink);
            canvas.drawOval(.075f,.18f,.215f,.32f,ink);
        } finally { canvas.restoreToCount(saved); }
    }

    public void drawMarkers(Canvas canvas, Set<Integer> flags, PoolRadState state,
            boolean showPosition, float left, float top, float cell, float density) {
        Map<Integer, NoteIcon> icons = new HashMap<>();
        if (flags != null) for (int tile : flags) icons.put(tile, NoteIcon.FLAG);
        drawMarkers(canvas, icons, state, showPosition, left, top, cell, density);
    }

    public void drawMarkers(Canvas canvas, Map<Integer, NoteIcon> flags, PoolRadState state,
            boolean showPosition, float left, float top, float cell, float density) {
        if (!(cell > 0) || !(density > 0)) return;
        if (flags != null) for (Map.Entry<Integer, NoteIcon> entry : flags.entrySet()) {
            int tile = entry.getKey();
            if (tile < 0 || tile >= GeoMap.WIDTH * GeoMap.WIDTH) continue;
            if (entry.getValue() != null && entry.getValue() != NoteIcon.FLAG) {
                drawIcon(canvas, entry.getValue(), left + tile % 16 * cell, top + tile / 16 * cell, cell, density);
                continue;
            }
            float cx = left + (tile % GeoMap.WIDTH + .25f) * cell;
            float cy = top + (tile / GeoMap.WIDTH + .25f) * cell;
            arrow.reset(); arrow.moveTo(cx, cy + cell * .55f); arrow.lineTo(cx, cy);
            arrow.lineTo(cx + cell * .5f, cy + cell * .12f); arrow.lineTo(cx, cy + cell * .28f);
            ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(4 * density);
            canvas.drawPath(arrow, ink);
            ink.setColor(Color.BLACK); ink.setStrokeWidth(1.5f * density);
            canvas.drawPath(arrow, ink);
        }
        if (showPosition && state != null) {
            int saved = canvas.save();
            try {
                canvas.translate(left + (state.x + .5f) * cell, top + (state.y + .5f) * cell);
                canvas.rotate(state.facing * 90);
                arrow.reset(); arrow.moveTo(0, -cell * .42f); arrow.lineTo(cell * .32f, cell * .32f);
                arrow.lineTo(0, cell * .15f); arrow.lineTo(-cell * .32f, cell * .32f); arrow.close();
                ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(3 * density);
                canvas.drawPath(arrow, ink);
                ink.setColor(Color.BLACK); ink.setStyle(Paint.Style.FILL);
                canvas.drawPath(arrow, ink);
            } finally { canvas.restoreToCount(saved); }
        }
    }

    /** Original vector glyphs; user labels, never automatic claims about the game tile. */
    private void drawIcon(Canvas canvas, NoteIcon icon, float left, float top, float cell, float density) {
        int saved = canvas.save();
        try {
            canvas.translate(left, top); canvas.scale(cell, cell);
            arrow.reset();
            switch (icon) {
                case SMITHY:
                    arrow.moveTo(.12f,.26f); arrow.lineTo(.88f,.26f); arrow.lineTo(.72f,.47f);
                    arrow.lineTo(.59f,.47f); arrow.lineTo(.59f,.66f); arrow.lineTo(.76f,.77f);
                    arrow.lineTo(.24f,.77f); arrow.lineTo(.41f,.66f); arrow.lineTo(.41f,.47f);
                    arrow.lineTo(.24f,.42f); arrow.close();
                    break;
                case TEMPLE:
                    arrow.moveTo(.13f,.36f); arrow.lineTo(.5f,.13f); arrow.lineTo(.87f,.36f); arrow.close();
                    arrow.moveTo(.2f,.45f); arrow.lineTo(.2f,.76f); arrow.moveTo(.5f,.45f); arrow.lineTo(.5f,.76f);
                    arrow.moveTo(.8f,.45f); arrow.lineTo(.8f,.76f); arrow.moveTo(.12f,.83f); arrow.lineTo(.88f,.83f);
                    break;
                case INN:
                    arrow.moveTo(.15f,.27f); arrow.lineTo(.15f,.83f); arrow.moveTo(.85f,.49f); arrow.lineTo(.85f,.83f);
                    arrow.moveTo(.15f,.63f); arrow.lineTo(.85f,.63f); arrow.lineTo(.85f,.47f); arrow.lineTo(.43f,.47f);
                    arrow.lineTo(.43f,.63f); arrow.moveTo(.19f,.38f); arrow.lineTo(.36f,.38f);
                    arrow.lineTo(.36f,.53f); arrow.lineTo(.19f,.53f); arrow.close();
                    break;
                case SHOP:
                    arrow.moveTo(.21f,.2f); arrow.lineTo(.79f,.2f); arrow.lineTo(.89f,.43f); arrow.lineTo(.11f,.43f); arrow.close();
                    arrow.moveTo(.23f,.49f); arrow.lineTo(.23f,.8f); arrow.lineTo(.77f,.8f); arrow.lineTo(.77f,.49f);
                    arrow.moveTo(.23f,.63f); arrow.lineTo(.77f,.63f); arrow.moveTo(.4f,.21f); arrow.lineTo(.36f,.43f);
                    arrow.moveTo(.6f,.21f); arrow.lineTo(.64f,.43f);
                    break;
                case MONSTER:
                    arrow.moveTo(.23f,.4f); arrow.lineTo(.17f,.13f); arrow.lineTo(.43f,.31f); arrow.lineTo(.57f,.31f);
                    arrow.lineTo(.83f,.13f); arrow.lineTo(.77f,.4f); arrow.lineTo(.8f,.63f);
                    arrow.lineTo(.64f,.83f); arrow.lineTo(.36f,.83f); arrow.lineTo(.2f,.63f); arrow.close();
                    arrow.moveTo(.32f,.49f); arrow.lineTo(.43f,.52f); arrow.moveTo(.57f,.52f); arrow.lineTo(.68f,.49f);
                    arrow.moveTo(.38f,.68f); arrow.lineTo(.62f,.68f);
                    break;
                case HIDDEN_WALL:
                    arrow.moveTo(.13f,.2f); arrow.lineTo(.87f,.2f); arrow.moveTo(.13f,.4f); arrow.lineTo(.87f,.4f);
                    arrow.moveTo(.13f,.6f); arrow.lineTo(.34f,.6f); arrow.moveTo(.66f,.6f); arrow.lineTo(.87f,.6f);
                    arrow.moveTo(.13f,.8f); arrow.lineTo(.34f,.8f); arrow.moveTo(.66f,.8f); arrow.lineTo(.87f,.8f);
                    arrow.moveTo(.37f,.2f); arrow.lineTo(.37f,.4f); arrow.moveTo(.65f,.2f); arrow.lineTo(.65f,.4f);
                    arrow.moveTo(.34f,.8f); arrow.lineTo(.34f,.48f); arrow.lineTo(.66f,.48f); arrow.lineTo(.66f,.8f);
                    break;
                case DISTRICT:
                    arrow.moveTo(.16f,.16f); arrow.lineTo(.84f,.16f); arrow.lineTo(.84f,.84f); arrow.lineTo(.16f,.84f); arrow.close();
                    arrow.moveTo(.16f,.5f); arrow.lineTo(.84f,.5f); arrow.moveTo(.5f,.16f); arrow.lineTo(.5f,.84f);
                    break;
                case TREASURE:
                    arrow.moveTo(.16f,.45f); arrow.cubicTo(.16f,.13f,.84f,.13f,.84f,.45f);
                    arrow.lineTo(.84f,.8f); arrow.lineTo(.16f,.8f); arrow.close();
                    arrow.moveTo(.16f,.45f); arrow.lineTo(.84f,.45f); arrow.moveTo(.44f,.41f);
                    arrow.lineTo(.56f,.41f); arrow.lineTo(.56f,.6f); arrow.lineTo(.44f,.6f); arrow.close();
                    break;
                default: return;
            }
            ink.setStyle(Paint.Style.STROKE); ink.setColor(Color.WHITE); ink.setStrokeWidth(4 * density / cell);
            canvas.drawPath(arrow, ink);
            ink.setColor(Color.BLACK); ink.setStrokeWidth(Math.max(2f * density / cell, .065f));
            canvas.drawPath(arrow, ink);
        } finally { canvas.restoreToCount(saved); }
    }
}
