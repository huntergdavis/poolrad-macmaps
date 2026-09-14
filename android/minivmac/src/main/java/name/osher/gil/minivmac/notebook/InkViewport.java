package name.osher.gil.minivmac.notebook;

/** Display-only transform. Stored ink remains in the original normalized paper coordinates. */
public final class InkViewport {
    public final InkSheetLayout paper;
    private final float width, height, margin;
    private float scale = 1, offsetX, offsetY;

    public InkViewport(float width, float height, float margin) {
        paper = new InkSheetLayout(width, height, margin);
        this.width = width; this.height = height; this.margin = margin;
    }

    public float scale() { return scale; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public float paperX(float screenX) { return (screenX - offsetX) / scale; }
    public float paperY(float screenY) { return (screenY - offsetY) / scale; }
    public void fit() { scale = 1; offsetX = offsetY = 0; }

    public void zoom(float factor, float focusX, float focusY) {
        if (!finite(factor) || factor <= 0 || !finite(focusX) || !finite(focusY)
                || paper.width <= 0 || paper.height <= 0) return;
        float next = Math.max(1, Math.min(4, scale * factor));
        float ratio = next / scale;
        offsetX = focusX - (focusX - offsetX) * ratio;
        offsetY = focusY - (focusY - offsetY) * ratio;
        scale = next;
        constrain();
    }

    public void pan(float dx, float dy) {
        if (!finite(dx) || !finite(dy)) return;
        offsetX += dx; offsetY += dy;
        constrain();
    }

    private void constrain() {
        offsetX = bound(offsetX, paper.left, paper.width, width);
        offsetY = bound(offsetY, paper.top, paper.height, height);
    }
    private float bound(float offset, float start, float size, float screen) {
        if (size * scale <= Math.max(0, screen - margin * 2))
            return (screen - size * scale) / 2 - start * scale;
        return Math.max(screen - margin - (start + size) * scale,
                Math.min(margin - start * scale, offset));
    }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
