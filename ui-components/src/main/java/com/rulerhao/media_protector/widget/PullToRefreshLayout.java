package com.rulerhao.media_protector.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/**
 * Custom pull-to-refresh layout without external dependencies.
 * Wraps a scrollable child (GridView, ListView) and shows a progress indicator when pulled down.
 */
public class PullToRefreshLayout extends FrameLayout {

    private static final int PULL_THRESHOLD_DP = 80;
    private static final int MAX_PULL_DP = 120;
    private static final float DRAG_RATE = 0.5f;

    private View childView;
    private ProgressBar progressBar;
    private OnRefreshListener refreshListener;

    private boolean isRefreshing = false;
    private boolean isDragging = false;
    private float initialY;
    private float currentPull = 0;
    private int pullThreshold;
    private int maxPull;

    public interface OnRefreshListener {
        void onRefresh();
    }

    public PullToRefreshLayout(Context context) {
        super(context);
        init(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        pullThreshold = (int) (PULL_THRESHOLD_DP * density);
        maxPull = (int) (MAX_PULL_DP * density);

        // Create progress indicator
        progressBar = new ProgressBar(context);
        LayoutParams lp = new LayoutParams(
                (int) (40 * density),
                (int) (40 * density)
        );
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
        lp.topMargin = (int) (16 * density);
        progressBar.setLayoutParams(lp);
        progressBar.setVisibility(View.GONE);
        addView(progressBar);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Find the scrollable child (skip progress bar)
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != progressBar) {
                childView = child;
                break;
            }
        }
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.refreshListener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        isRefreshing = refreshing;
        if (refreshing) {
            progressBar.setVisibility(View.VISIBLE);
            if (childView != null) {
                childView.setTranslationY(pullThreshold);
            }
        } else {
            progressBar.setVisibility(View.GONE);
            if (childView != null) {
                childView.animate()
                        .translationY(0)
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
            currentPull = 0;
        }
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    private boolean canChildScrollUp() {
        if (childView == null) return false;

        if (childView instanceof AbsListView) {
            AbsListView absListView = (AbsListView) childView;
            if (absListView.getChildCount() == 0) return false;
            int firstVisiblePosition = absListView.getFirstVisiblePosition();
            if (firstVisiblePosition > 0) return true;
            View firstChild = absListView.getChildAt(0);
            return firstChild != null && firstChild.getTop() < absListView.getPaddingTop();
        }

        return childView.canScrollVertically(-1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isRefreshing) return false;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialY = ev.getY();
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float dy = ev.getY() - initialY;
                if (dy > 0 && !canChildScrollUp() && !isDragging) {
                    isDragging = true;
                    return true;
                }
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isRefreshing) return super.onTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float dy = (ev.getY() - initialY) * DRAG_RATE;
                    currentPull = Math.max(0, Math.min(dy, maxPull));

                    if (childView != null) {
                        childView.setTranslationY(currentPull);
                    }

                    // Show/hide progress based on pull amount
                    float alpha = Math.min(1f, currentPull / pullThreshold);
                    progressBar.setAlpha(alpha);
                    progressBar.setVisibility(currentPull > 10 ? View.VISIBLE : View.GONE);

                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    if (currentPull >= pullThreshold) {
                        // Trigger refresh
                        setRefreshing(true);
                        if (refreshListener != null) {
                            refreshListener.onRefresh();
                        }
                    } else {
                        // Snap back
                        progressBar.setVisibility(View.GONE);
                        if (childView != null) {
                            childView.animate()
                                    .translationY(0)
                                    .setDuration(200)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                        }
                    }
                    isDragging = false;
                    currentPull = 0;
                    return true;
                }
                break;
        }

        return super.onTouchEvent(ev);
    }
}
