package com.rulerhao.media_protector.util;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * ImageView with pinch-to-zoom and pan support.
 * <p>
 * Features:
 * <ul>
 *   <li>Pinch-to-zoom with configurable min/max scale</li>
 *   <li>Double-tap to zoom in/reset</li>
 *   <li>Pan when zoomed in</li>
 *   <li>Boundary constraints to prevent panning outside image</li>
 *   <li>Smooth reset to fit-center on new image</li>
 * </ul>
 */
public class ZoomableImageView extends ImageView {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float currentScale = 1.0f;
    private boolean isZoomed = false;

    // For panning
    private final PointF lastTouch = new PointF();
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isDragging = false;

    // Image and view dimensions for boundary calculations
    private float imageWidth, imageHeight;
    private float viewWidth, viewHeight;
    private float baseTransX, baseTransY;

    // Listener for tap events when not zoomed (for UI toggle)
    private OnSingleTapListener singleTapListener;

    public interface OnSingleTapListener {
        void onSingleTap();
    }

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        this.singleTapListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        resetZoom();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        resetZoom();
    }

    /**
     * Resets zoom to fit the image in center (like fitCenter scaleType).
     */
    public void resetZoom() {
        Drawable drawable = getDrawable();
        if (drawable == null || viewWidth == 0 || viewHeight == 0) return;

        imageWidth = drawable.getIntrinsicWidth();
        imageHeight = drawable.getIntrinsicHeight();

        if (imageWidth <= 0 || imageHeight <= 0) return;

        // Calculate scale to fit image in view (fitCenter behavior)
        float scaleX = viewWidth / imageWidth;
        float scaleY = viewHeight / imageHeight;
        float baseScale = Math.min(scaleX, scaleY);

        // Calculate translation to center the image
        float scaledWidth = imageWidth * baseScale;
        float scaledHeight = imageHeight * baseScale;
        baseTransX = (viewWidth - scaledWidth) / 2f;
        baseTransY = (viewHeight - scaledHeight) / 2f;

        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(baseTransX, baseTransY);

        currentScale = 1.0f;
        isZoomed = false;
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        // Handle panning when zoomed
        if (isZoomed) {
            handlePan(event);
        }

        return true;
    }

    private void handlePan(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastTouch.set(event.getX(), event.getY());
                isDragging = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && !scaleDetector.isInProgress()) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        float dx = x - lastTouch.x;
                        float dy = y - lastTouch.y;

                        translateWithBounds(dx, dy);

                        lastTouch.set(x, y);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                isDragging = false;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    if (newPointerIndex < event.getPointerCount()) {
                        lastTouch.set(event.getX(newPointerIndex), event.getY(newPointerIndex));
                        activePointerId = event.getPointerId(newPointerIndex);
                    }
                }
                break;
        }
    }

    /**
     * Translates the matrix while respecting image boundaries.
     */
    private void translateWithBounds(float dx, float dy) {
        matrix.getValues(matrixValues);
        float currentTransX = matrixValues[Matrix.MTRANS_X];
        float currentTransY = matrixValues[Matrix.MTRANS_Y];
        float scale = matrixValues[Matrix.MSCALE_X];

        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        // Calculate boundaries
        float minTransX, maxTransX, minTransY, maxTransY;

        if (scaledWidth <= viewWidth) {
            // Image narrower than view - center horizontally
            minTransX = maxTransX = (viewWidth - scaledWidth) / 2f;
        } else {
            // Image wider than view - allow panning
            minTransX = viewWidth - scaledWidth;
            maxTransX = 0;
        }

        if (scaledHeight <= viewHeight) {
            // Image shorter than view - center vertically
            minTransY = maxTransY = (viewHeight - scaledHeight) / 2f;
        } else {
            // Image taller than view - allow panning
            minTransY = viewHeight - scaledHeight;
            maxTransY = 0;
        }

        // Clamp translation
        float newTransX = Math.max(minTransX, Math.min(maxTransX, currentTransX + dx));
        float newTransY = Math.max(minTransY, Math.min(maxTransY, currentTransY + dy));

        matrix.postTranslate(newTransX - currentTransX, newTransY - currentTransY);
        setImageMatrix(matrix);
    }

    /**
     * Ensures the image stays within bounds after scaling.
     */
    private void constrainToBounds() {
        translateWithBounds(0, 0);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = currentScale * scaleFactor;

            // Clamp scale
            if (newScale < MIN_SCALE) {
                scaleFactor = MIN_SCALE / currentScale;
                newScale = MIN_SCALE;
            } else if (newScale > MAX_SCALE) {
                scaleFactor = MAX_SCALE / currentScale;
                newScale = MAX_SCALE;
            }

            // Scale around the focus point
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            currentScale = newScale;
            isZoomed = currentScale > 1.01f;

            constrainToBounds();
            setImageMatrix(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isZoomed) {
                // Reset to fit
                resetZoom();
            } else {
                // Zoom in to focus point
                zoomTo(DOUBLE_TAP_SCALE, e.getX(), e.getY());
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // Only forward tap if not zoomed (for UI toggle)
            if (!isZoomed && singleTapListener != null) {
                singleTapListener.onSingleTap();
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }

    /**
     * Zooms to the specified scale centered on the given point.
     */
    private void zoomTo(float targetScale, float focusX, float focusY) {
        float scaleFactor = targetScale / currentScale;
        matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
        currentScale = targetScale;
        isZoomed = true;
        constrainToBounds();
        setImageMatrix(matrix);
    }

    /**
     * Returns true if the image is currently zoomed in.
     */
    public boolean isZoomed() {
        return isZoomed;
    }
}
