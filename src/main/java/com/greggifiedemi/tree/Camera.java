package com.greggifiedemi.tree;

/** Pan and zoom of the tree canvas (port of NEI's crafting tree Camera). */
public final class Camera {

    private static final float ZOOM_MIN = 0.1f;
    private static final float ZOOM_MAX = 8.0f;
    private static final double ZOOM_STEP_BASE = 1.4;
    private static final float SCROLL_STEP_DIVISOR = 5f;
    private static final int CULL_MARGIN = 32;
    private static final int CULL_ROW_MARGIN = 16;
    private static final float INITIAL_SCROLL = -8f;

    public int widgetX;
    public int widgetY;
    public int widgetW;
    public int widgetH;

    public float scrollX = INITIAL_SCROLL;
    public float scrollY = INITIAL_SCROLL;
    public float zoomLevel = 1.0f;

    private double lastDragX;
    private double lastDragY;
    private boolean dragging;

    public void setViewport(int x, int y, int w, int h) {
        this.widgetX = x;
        this.widgetY = y;
        this.widgetW = w;
        this.widgetH = h;
    }

    public boolean isInWidget(double x, double y) {
        return x >= this.widgetX && y >= this.widgetY && x < this.widgetX + this.widgetW
                && y < this.widgetY + this.widgetH;
    }

    public void startDrag(double mouseX, double mouseY) {
        this.dragging = true;
        this.lastDragX = mouseX;
        this.lastDragY = mouseY;
    }

    public void drag(double mouseX, double mouseY) {
        if (!this.dragging) {
            return;
        }

        this.scrollX -= (float) ((mouseX - this.lastDragX) / this.zoomLevel);
        this.scrollY -= (float) ((mouseY - this.lastDragY) / this.zoomLevel);
        this.lastDragX = mouseX;
        this.lastDragY = mouseY;
    }

    public void endDrag() {
        this.dragging = false;
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public void scroll(double mouseX, double mouseY, int direction, boolean shiftKey, boolean ctrlKey) {
        if (shiftKey) {
            this.scrollX -= direction * this.widgetW / (SCROLL_STEP_DIVISOR * this.zoomLevel);
        } else if (ctrlKey) {
            final double localX = mouseX - this.widgetX;
            final double localY = mouseY - this.widgetY;
            final float oldZoom = this.zoomLevel;

            this.zoomLevel = clamp((float) (this.zoomLevel * Math.pow(ZOOM_STEP_BASE, direction)), ZOOM_MIN, ZOOM_MAX);
            this.scrollX += (float) (localX / oldZoom - localX / this.zoomLevel);
            this.scrollY += (float) (localY / oldZoom - localY / this.zoomLevel);
        } else {
            this.scrollY -= direction * this.widgetH / (SCROLL_STEP_DIVISOR * this.zoomLevel);
        }
    }

    public void centerOn(float worldX, float worldY) {
        this.scrollX = worldX - (this.widgetW / this.zoomLevel) / 2f;
        this.scrollY = worldY - (this.widgetH / this.zoomLevel) / 2f;
    }

    public void clampToBounds(int treeWidth, int treeHeight) {
        this.scrollX = clamp(this.scrollX, -this.widgetW / this.zoomLevel + 4, treeWidth - 4);
        this.scrollY = clamp(this.scrollY, -this.widgetH / this.zoomLevel + 4, treeHeight - 4);
    }

    public int visibleRowMin() {
        final float zScrollY = this.scrollY * this.zoomLevel;
        return (int) ((zScrollY - CULL_MARGIN) / this.zoomLevel) - CULL_ROW_MARGIN;
    }

    public int visibleRowMax() {
        final float zScrollY = this.scrollY * this.zoomLevel;
        return (int) ((zScrollY + CULL_MARGIN) / this.zoomLevel) + (int) (this.widgetH / this.zoomLevel)
                + CULL_ROW_MARGIN;
    }

    /** Screen point to tree coordinates. */
    public double[] toWorld(double mouseX, double mouseY) {
        final float zScrollX = this.scrollX * this.zoomLevel;
        final float zScrollY = this.scrollY * this.zoomLevel;
        return new double[] { (mouseX - this.widgetX + zScrollX) / this.zoomLevel,
                (mouseY - this.widgetY + zScrollY) / this.zoomLevel };
    }

    static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

}
