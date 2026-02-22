package com.rulerhao.media_protector;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rulerhao.media_protector.util.ThumbnailLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-type list adapter for the browse screen.
 *
 * <p>Supports three item types:
 * <ul>
 *   <li>{@link #TYPE_DATE_HEADER} — date section separator ("FEBRUARY 19, 2026")</li>
 *   <li>{@link #TYPE_FOLDER_HEADER} — folder row; tap selects the folder</li>
 *   <li>{@link #TYPE_MEDIA_STRIP} — horizontal scrolling thumbnail strip for a section's files</li>
 * </ul>
 *
 * <p>Long-pressing any thumbnail enters selection mode. Tapping in selection mode
 * toggles the file; tapping outside selection mode opens the viewer via
 * {@link OnFileClickListener}. Call {@link #clearSelection()} to exit.
 */
public class FolderAdapter extends BaseAdapter {

    static final int TYPE_DATE_HEADER   = 0;
    static final int TYPE_FOLDER_HEADER = 1;
    static final int TYPE_MEDIA_STRIP   = 2;

    // ─── Callbacks ────────────────────────────────────────────────────────

    interface OnFileClickListener {
        void onFileClick(String[] sectionPaths, int index);
    }

    interface OnHeaderClickListener {
        /** Called when a date or folder header is tapped. Opens section view with all files in that section. */
        void onHeaderClick(String title, String[] sectionPaths);
    }

    interface OnSelectionChangedListener {
        /** @param count number of currently selected files; 0 means selection mode exited */
        void onChanged(int count);
    }

    // ─── Data model ───────────────────────────────────────────────────────

    static class BrowseItem {
        final int type;

        // ── Header fields (DATE_HEADER and FOLDER_HEADER) ──
        String title;
        String subtitle;

        // ── Folder-header-only fields ──
        File   folder;

        // ── Strip fields ──
        File[]   files;
        String[] paths;

        BrowseItem(int type) { this.type = type; }
    }

    // ─── Adapter state ────────────────────────────────────────────────────

    private final List<BrowseItem> items     = new ArrayList<>();
    private final LayoutInflater   inflater;
    private final Context          context;
    private final ThumbnailLoader  loader;
    private final boolean          encrypted;

    private OnFileClickListener        fileClickListener;
    private OnHeaderClickListener      headerClickListener;
    private OnSelectionChangedListener selectionChangedListener;

    /** Files currently selected (by the user's long-press → tap flow). */
    private final Set<File> selectedFiles = new HashSet<>();
    private boolean selectionActive = false;

    public FolderAdapter(Context context, boolean encrypted) {
        this.context  = context;
        this.inflater = LayoutInflater.from(context);
        this.encrypted = encrypted;
        this.loader   = ThumbnailLoader.getInstance();
    }

    public void setItems(List<BrowseItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnFileClickListener(OnFileClickListener l) {
        this.fileClickListener = l;
    }

    public void setOnHeaderClickListener(OnHeaderClickListener l) {
        this.headerClickListener = l;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionChangedListener = l;
    }

    // ─── Selection ────────────────────────────────────────────────────────

    public Set<File> getSelectedFiles() {
        return new HashSet<>(selectedFiles);
    }

    public void clearSelection() {
        selectedFiles.clear();
        selectionActive = false;
        notifyDataSetChanged();
    }

    /** Selects every file in every strip. */
    public void selectAll() {
        for (BrowseItem item : items) {
            if (item.type == TYPE_MEDIA_STRIP && item.files != null) {
                for (File f : item.files) selectedFiles.add(f);
            }
        }
        selectionActive = !selectedFiles.isEmpty();
        notifyDataSetChanged();
        if (selectionChangedListener != null)
            selectionChangedListener.onChanged(selectedFiles.size());
    }

    private void toggleSelection(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
            if (selectedFiles.isEmpty()) selectionActive = false;
        } else {
            selectedFiles.add(file);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null)
            selectionChangedListener.onChanged(selectedFiles.size());
    }

    /** Clears thumbnail cache. Call when switching modes to free memory. */
    public void clearCache() {
        loader.clearCache();
    }

    // ─── BaseAdapter ──────────────────────────────────────────────────────

    @Override public int    getCount()              { return items.size(); }
    @Override public Object getItem(int pos)        { return items.get(pos); }
    @Override public long   getItemId(int pos)      { return pos; }
    @Override public int    getViewTypeCount()       { return 3; }
    @Override public int    getItemViewType(int pos) { return items.get(pos).type; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BrowseItem item = items.get(position);
        switch (item.type) {
            case TYPE_DATE_HEADER:   return getDateHeaderView(item, convertView, parent);
            case TYPE_FOLDER_HEADER: return getFolderHeaderView(item, convertView, parent);
            default:                 return getMediaStripView(item, parent);
        }
    }

    // ─── View builders ────────────────────────────────────────────────────

    private View getDateHeaderView(BrowseItem item, View convertView, ViewGroup parent) {
        DateHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_browse_header_date, parent, false);
            holder = new DateHolder();
            holder.label = convertView.findViewById(R.id.tvDateLabel);
            holder.count = convertView.findViewById(R.id.tvDateCount);
            convertView.setTag(holder);
        } else {
            holder = (DateHolder) convertView.getTag();
        }
        holder.label.setText(item.title);
        holder.count.setText(item.subtitle);

        // Click header to open section view with all files in this date group
        final String title = item.title;
        final String[] paths = item.paths;
        convertView.setOnClickListener(v -> {
            if (headerClickListener != null && paths != null && paths.length > 0) {
                headerClickListener.onHeaderClick(title, paths);
            }
        });

        return convertView;
    }

    private View getFolderHeaderView(BrowseItem item, View convertView, ViewGroup parent) {
        FolderHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_browse_header_folder, parent, false);
            holder = new FolderHolder();
            holder.name  = convertView.findViewById(R.id.tvFolderName);
            holder.count = convertView.findViewById(R.id.tvFolderCount);
            convertView.setTag(holder);
        } else {
            holder = (FolderHolder) convertView.getTag();
        }
        holder.name.setText(item.title);
        holder.count.setText(item.subtitle);

        // Click header to open section view with all files in this folder
        final String title = item.title;
        final String[] paths = item.paths;
        convertView.setOnClickListener(v -> {
            if (headerClickListener != null && paths != null && paths.length > 0) {
                headerClickListener.onHeaderClick(title, paths);
            }
        });

        return convertView;
    }

    /**
     * Strips are never recycled (child count varies per section); always freshly inflated.
     * Each thumbnail is wrapped in a FrameLayout so a semi-transparent overlay can indicate
     * selection state.
     */
    private View getMediaStripView(BrowseItem item, ViewGroup parent) {
        View strip = inflater.inflate(R.layout.item_browse_media_strip, parent, false);
        LinearLayout container = strip.findViewById(R.id.stripContainer);

        int thumbSize   = dpToPx(72);
        int thumbMargin = dpToPx(4);

        if (item.files != null) {
            for (int i = 0; i < item.files.length; i++) {
                final File     file  = item.files[i];
                final String[] paths = item.paths;
                final int      index = i;

                // ── Cell (FrameLayout for thumbnail + selection overlay) ──
                FrameLayout cell = new FrameLayout(context);
                LinearLayout.LayoutParams cellLp =
                        new LinearLayout.LayoutParams(thumbSize, thumbSize);
                cellLp.setMarginEnd(thumbMargin);
                cell.setLayoutParams(cellLp);

                // Thumbnail
                ImageView iv = new ImageView(context);
                iv.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                loader.loadThumbnail(file, encrypted, iv);

                // Selection overlay (semi-transparent blue tint)
                View overlay = new View(context);
                overlay.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                overlay.setBackgroundColor(0x880088CC);
                overlay.setVisibility(
                        selectedFiles.contains(file) ? View.VISIBLE : View.GONE);

                cell.addView(iv);
                cell.addView(overlay);

                // Tap: toggle selection (if active) or open viewer
                cell.setOnClickListener(v -> {
                    if (selectionActive) {
                        toggleSelection(file);
                    } else if (fileClickListener != null) {
                        fileClickListener.onFileClick(paths, index);
                    }
                });

                // Long-press: enter selection mode and toggle this file
                cell.setOnLongClickListener(v -> {
                    selectionActive = true;
                    toggleSelection(file);
                    return true;
                });

                container.addView(cell);
            }
        }
        return strip;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────

    private static class DateHolder   { TextView label, count; }
    private static class FolderHolder { TextView name, count; }
}
