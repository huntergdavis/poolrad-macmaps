package name.osher.gil.minivmac;

/** Pixel rectangles only; independent of Android windows, tabs and map content. */
final class CompanionGeometry {
    private CompanionGeometry() { }

    static final class Bounds {
        final int left, top, right, bottom;
        Bounds(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int width() { return right - left; }
        int height() { return bottom - top; }
        boolean valid() {
            return right > left && bottom > top
                    && (long) right - left <= Integer.MAX_VALUE
                    && (long) bottom - top <= Integer.MAX_VALUE;
        }
    }

    /** A present but hidden/clipped companion must never fall back across the guest. */
    static Bounds dialog(Bounds visibleHost, boolean hasCompanion, Bounds visibleCompanion) {
        if (visibleHost == null || !visibleHost.valid()) return null;
        if (!hasCompanion) {
            int height = visibleHost.height() / 2;
            return height == 0 ? null : new Bounds(visibleHost.left, visibleHost.top,
                    visibleHost.right, visibleHost.top + height);
        }
        if (visibleCompanion == null || !visibleCompanion.valid()) return null;
        Bounds clipped = new Bounds(Math.max(visibleHost.left, visibleCompanion.left),
                Math.max(visibleHost.top, visibleCompanion.top),
                Math.min(visibleHost.right, visibleCompanion.right),
                Math.min(visibleHost.bottom, visibleCompanion.bottom));
        return clipped.valid() ? clipped : null;
    }

    /** Original MapStackLayout allocation, now shared by the entire companion. */
    static int allocation(int width, int available, int guestWidth, int guestHeight) {
        if (width <= 0 || available <= 0 || guestWidth <= 0 || guestHeight <= 0) return 0;
        long spare = available - (long) width * guestHeight / guestWidth;
        long height = Math.min(width, Math.max(spare, available / 3));
        return (int) Math.min(height, available / 2);
    }
}
