package name.osher.gil.minivmac.mapper;

/** A camera over the real arena. Zoom changes the view, never the arena bounds. */
public final class CombatViewport {
    public final float left, top, cell, centerX, centerY, zoom;
    public final float clipLeft, clipTop, clipRight, clipBottom;

    public CombatViewport(int width, int height, float density, float zoom, float x, float y) {
        clipLeft = 26 * density;
        clipTop = 44 * density;
        clipRight = Math.max(clipLeft, width - 26 * density);
        clipBottom = Math.max(clipTop, height - 32 * density);
        this.zoom = Float.isNaN(zoom) ? 1 : Math.max(1, Math.min(8, zoom));
        float fit = Math.min((clipRight - clipLeft) / CombatSnapshot.ARENA_WIDTH,
                (clipBottom - clipTop) / CombatSnapshot.ARENA_HEIGHT);
        cell = fit * this.zoom;
        centerX = center(x, CombatSnapshot.ARENA_WIDTH, clipRight - clipLeft, cell);
        centerY = center(y, CombatSnapshot.ARENA_HEIGHT, clipBottom - clipTop, cell);
        left = (clipLeft + clipRight) / 2 - centerX * cell;
        top = (clipTop + clipBottom) / 2 - centerY * cell;
    }

    private static float center(float value, int tiles, float pixels, float cell) {
        if (cell <= 0 || pixels >= cell * tiles) return tiles / 2f;
        float half = pixels / cell / 2;
        if (Float.isNaN(value) || Float.isInfinite(value)) value = tiles / 2f;
        return Math.max(half, Math.min(tiles - half, value));
    }

    /** Two squares of breathing room around every known combatant, including fallen party. */
    public static CombatViewport action(int width, int height, float density, CombatSnapshot battle) {
        CombatViewport fit = new CombatViewport(width, height, density, 1, 25, 12.5f);
        if (battle == null || fit.cell <= 0) return fit;
        int minX = 49, minY = 24, maxX = 0, maxY = 0;
        for (CombatSnapshot.Spot spot : battle.spots()) {
            minX = Math.min(minX, spot.x); minY = Math.min(minY, spot.y);
            maxX = Math.max(maxX, spot.x); maxY = Math.max(maxY, spot.y);
        }
        int l = Math.max(0, minX - 2), t = Math.max(0, minY - 2);
        int r = Math.min(50, maxX + 3), b = Math.min(25, maxY + 3);
        float pitch = Math.min((fit.clipRight - fit.clipLeft) / (r-l),
                (fit.clipBottom - fit.clipTop) / (b-t));
        // A lone remaining combatant should not fill the whole screen.
        pitch = Math.min(pitch, 64 * density);
        return new CombatViewport(width, height, density, pitch / fit.cell, (l+r)/2f, (t+b)/2f);
    }

    public boolean contains(float x, float y) {
        return x >= clipLeft && x < clipRight && y >= clipTop && y < clipBottom;
    }
}
