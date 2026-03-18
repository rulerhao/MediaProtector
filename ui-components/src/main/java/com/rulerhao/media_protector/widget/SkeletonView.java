package com.rulerhao.media_protector.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A simple shimmer/skeleton loading view without external dependencies.
 * Displays animated placeholder boxes while content is loading.
 */
public class SkeletonView extends View {

    private static final int ANIMATION_DURATION_MS = 1500;
    private static final int BASE_COLOR = 0xFF2C2C2C;        // Dark gray
    private static final int SHIMMER_COLOR = 0xFF3C3C3C;     // Lighter gray

    private Paint paint;
    private Paint shimmerPaint;
    private float shimmerPosition = 0f;
    private ValueAnimator shimmerAnimator;
    private int columns = 3;
    private int rows = 4;
    private float cornerRadius;
    private float spacing;
    private RectF itemRect = new RectF();

    public SkeletonView(Context context) {
        super(context);
        init(context);
    }

    public SkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SkeletonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 4 * density;
        spacing = 2 * density;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(BASE_COLOR);

        shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Start shimmer animation
        startShimmer();
    }

    public void setGridSize(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        invalidate();
    }

    private void startShimmer() {
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f);
        shimmerAnimator.setDuration(ANIMATION_DURATION_MS);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setInterpolator(new LinearInterpolator());
        shimmerAnimator.addUpdateListener(animation -> {
            shimmerPosition = (float) animation.getAnimatedValue();
            invalidate();
        });
        shimmerAnimator.start();
    }

    public void stopShimmer() {
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float itemWidth = (width - spacing * (columns - 1)) / columns;
        float itemHeight = itemWidth; // Square items

        // Create shimmer gradient
        float shimmerWidth = width * 0.3f;
        float shimmerStart = shimmerPosition * (width + shimmerWidth * 2) - shimmerWidth;

        LinearGradient gradient = new LinearGradient(
                shimmerStart, 0,
                shimmerStart + shimmerWidth, 0,
                new int[] { BASE_COLOR, SHIMMER_COLOR, BASE_COLOR },
                new float[] { 0f, 0.5f, 1f },
                Shader.TileMode.CLAMP
        );
        shimmerPaint.setShader(gradient);

        // Draw grid of skeleton items
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                float left = col * (itemWidth + spacing);
                float top = row * (itemHeight + spacing);

                if (top > height) break;

                itemRect.set(left, top, left + itemWidth, top + itemHeight);

                // Draw base
                canvas.drawRoundRect(itemRect, cornerRadius, cornerRadius, paint);

                // Draw shimmer overlay
                canvas.drawRoundRect(itemRect, cornerRadius, cornerRadius, shimmerPaint);
            }
        }
    }
}
