package com.rulerhao.media_protector.ui;

import android.view.View;
import android.widget.AbsListView;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages scroll positions for multiple list views.
 * Allows saving and restoring scroll positions when switching between views.
 */
public class ScrollPositionManager {

    /**
     * Represents a saved scroll position.
     */
    public static class ScrollPosition {
        public final int position;
        public final int offset;

        public ScrollPosition(int position, int offset) {
            this.position = position;
            this.offset = offset;
        }

        public static final ScrollPosition ZERO = new ScrollPosition(0, 0);
    }

    private final Map<String, ScrollPosition> savedPositions = new HashMap<>();

    /**
     * Saves the scroll position of a list view.
     *
     * @param key      A unique key for this list view
     * @param listView The list view to save position from
     */
    public void save(String key, AbsListView listView) {
        if (listView == null) return;

        int position = listView.getFirstVisiblePosition();
        View child = listView.getChildAt(0);
        int offset = (child == null) ? 0 : child.getTop();

        savedPositions.put(key, new ScrollPosition(position, offset));
    }

    /**
     * Restores the scroll position of a list view.
     *
     * @param key      The key used when saving
     * @param listView The list view to restore position to
     */
    public void restore(String key, AbsListView listView) {
        if (listView == null) return;

        ScrollPosition pos = savedPositions.get(key);
        if (pos == null) {
            pos = ScrollPosition.ZERO;
        }

        final int position = pos.position;
        final int offset = pos.offset;

        listView.post(() -> listView.setSelectionFromTop(position, offset));
    }

    /**
     * Gets the saved scroll position for a key.
     *
     * @param key The key
     * @return The saved position, or ZERO if not saved
     */
    public ScrollPosition get(String key) {
        ScrollPosition pos = savedPositions.get(key);
        return pos != null ? pos : ScrollPosition.ZERO;
    }

    /**
     * Clears all saved positions.
     */
    public void clear() {
        savedPositions.clear();
    }

    /**
     * Clears the saved position for a specific key.
     *
     * @param key The key to clear
     */
    public void clear(String key) {
        savedPositions.remove(key);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Common Keys
    // ─────────────────────────────────────────────────────────────────────

    public static final String KEY_PROTECTED = "protected";
    public static final String KEY_BROWSE_DATE = "browse_date";
    public static final String KEY_BROWSE_FOLDER = "browse_folder";
}
