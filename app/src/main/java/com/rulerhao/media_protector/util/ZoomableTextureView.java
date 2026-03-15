package com.rulerhao.media_protector.util;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;

/**
 * TextureView with pinch-to-zoom and pan support for video playback.
 * <p>
 * Features:
 * <ul>
 *   <li>Pinch-to-zoom with configurable min/max scale</li>
 *   <li>Double-tap to zoom in/reset</li>
 *   <li>Pan when zoomed in</li>
 *   <li>Maintains video aspect ratio during zoom</li>
 * </ul>
 */
public class ZoomableTextureView extends TextureView {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;
    private static final float DOUBLE_TAP_SCALE = 2.0f;

    private final Matrix transformMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float currentScale = 1.0f;
    private boolean isZoomed = false;

    // Video dimensions for proper scaling
    private int videoWidth = 0;
    private int videoHeight = 0;
    private float baseScale = 1.0f;
    private float baseTransX = 0f;
    private float baseTransY = 0f;

    // For panning
    private final PointF lastTouch = new PointF();
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isDragging = false;

    // Listener for tap events when not zoomed (for UI toggle)
    private OnSingleTapListener singleTapListener;

    public interface OnSingleTapListener {
        void onSingleTap();
    }

    public ZoomableTextureView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        this.singleTapListener = listener;
    }

    /**
     * Sets the video dimensions for proper aspect ratio handling.
     * Call this when video is prepared.
     */
    public void setVideoSize(int width, int height) {
        this.videoWidth = width;
        this.videoHeight = height;
        resetZoom();
    }

    /**
     * Resets zoom to fit the video in center (maintaining aspect ratio).
     */
    public void resetZoom() {
        if (videoWidth == 0 || videoHeight == 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Calculate scale to fit video in view (fitCenter behavior)
        float scaleX = (float) viewWidth / videoWidth;
        float scaleY = (float) viewHeight / videoHeight;
        baseScale = Math.min(scaleX, scaleY);

        // Calculate translation to center the video
        float scaledWidth = videoWidth * baseScale;
        float scaledHeight = videoHeight * baseScale;
        baseTransX = (viewWidth - scaledWidth) / 2f;
        baseTransY = (viewHeight - scaledHeight) / 2f;

        transformMatrix.reset();
        transformMatrix.postScale(baseScale, baseScale);
        transformMatrix.postTranslate(baseTransX, baseTransY);

        currentScale = 1.0f;
        isZoomed = false;
        setTransform(transformMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (videoWidth > 0 && videoHeight > 0) {
            resetZoom();
        }
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
     * Translates the matrix while respecting video boundaries.
     */
    private void translateWithBounds(float dx, float dy) {
        if (videoWidth == 0 || videoHeight == 0) return;

        transformMatrix.getValues(matrixValues);
        float currentTransX = matrixValues[Matrix.MTRANS_X];
        float currentTransY = matrixValues[Matrix.MTRANS_Y];
        float scale = matrixValues[Matrix.MSCALE_X];

        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Calculate boundaries
        float minTransX, maxTransX, minTransY, maxTransY;

        if (scaledWidth <= viewWidth) {
            minTransX = maxTransX = (viewWidth - scaledWidth) / 2f;
        } else {
            minTransX = viewWidth - scaledWidth;
            maxTransX = 0;
        }

        if (scaledHeight <= viewHeight) {
            minTransY = maxTransY = (viewHeight - scaledHeight) / 2f;
        } else {
            minTransY = viewHeight - scaledHeight;
            maxTransY = 0;
        }

        // Clamp translation
        float newTransX = Math.max(minTransX, Math.min(maxTransX, currentTransX + dx));
        float newTransY = Math.max(minTransY, Math.min(maxTransY, currentTransY + dy));

        transformMatrix.postTranslate(newTransX - currentTransX, newTransY - currentTransY);
        setTransform(transformMatrix);
    }

    /**
     * Ensures the video stays within bounds after scaling.
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
            transformMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            currentScale = newScale;
            isZoomed = currentScale > 1.01f;

            constrainToBounds();
            setTransform(transformMatrix);
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
            // Forward tap for UI toggle
            if (singleTapListener != null) {
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
        transformMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
        currentScale = targetScale;
        isZoomed = true;
        constrainToBounds();
        setTransform(transformMatrix);
    }

    /**
     * Returns true if the video is currently zoomed in.
     */
    public boolean isZoomed() {
        return isZoomed;
    }
}
