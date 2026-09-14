package name.osher.gil.minivmac.notebook;

/** Two fixed 4:3 halves: a 90%-fit map at left and blank writing space at right. */
public final class InkSheetLayout {
    public static final float ASPECT = 8f / 3f;
    public final float left, top, width, height;
    public final float mapLeft, mapTop, mapSize;

    public InkSheetLayout(float viewWidth, float viewHeight, float margin) {
        requireSize(viewWidth); requireSize(viewHeight); requireSize(margin);
        double availableWidth = Math.max(0, (double) viewWidth - 2d * margin);
        double availableHeight = Math.max(0, (double) viewHeight - 2d * margin);
        width = (float) Math.min(availableWidth, availableHeight * (8d / 3d));
        height = width * (3f / 8f);
        left = (viewWidth - width) / 2;
        top = (viewHeight - height) / 2;
        mapSize = .9f * Math.min(width / 2, height);
        mapLeft = left + (width / 2 - mapSize) / 2;
        mapTop = top + (height - mapSize) / 2;
    }

    public boolean contains(float x, float y) {
        return width > 0 && height > 0 && x >= left && x < left + width
                && y >= top && y < top + height;
    }

    public float toX(float fraction) { return left + fraction * width; }
    public float toY(float fraction) { return top + fraction * height; }
    public float normalX(float x) { return width > 0 ? clamp((x - left) / width) : 0; }
    public float normalY(float y) { return height > 0 ? clamp((y - top) / height) : 0; }
    public float mapTileX(float tileX) { return mapLeft + tileX * mapSize / 16; }
    public float mapTileY(float tileY) { return mapTop + tileY * mapSize / 16; }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static void requireSize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0)
            throw new IllegalArgumentException("Sheet dimensions must be finite and nonnegative");
    }
}
