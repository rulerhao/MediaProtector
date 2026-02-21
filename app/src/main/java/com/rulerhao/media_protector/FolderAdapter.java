package com.rulerhao.media_protector;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rulerhao.media_protector.util.ThumbnailLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-type list adapter for the browse screen.
 *
 * <p>Supports three item types:
 * <ul>
 *   <li>{@link #TYPE_DATE_HEADER} — date section separator ("FEBRUARY 19, 2026")</li>
 *   <li>{@link #TYPE_FOLDER_HEADER} — folder with thumbnail; tap selects the folder</li>
 *   <li>{@link #TYPE_MEDIA} — individual media file row with thumbnail</li>
 * </ul>
 */
public class FolderAdapter extends BaseAdapter {

    static final int TYPE_DATE_HEADER   = 0;
    static final int TYPE_FOLDER_HEADER = 1;
    static final int TYPE_MEDIA         = 2;

    // ─── Data model ───────────────────────────────────────────────────────

    static class BrowseItem {
        final int type;

        // ── Header fields (used by DATE_HEADER and FOLDER_HEADER) ──
        String title;        // date string or folder name
        String subtitle;     // file count label

        // ── Folder-header-only fields ──
        File   folder;       // folder to return when tapped
        File   previewFile;  // file whose thumbnail represents this folder

        // ── Media-item fields ──
        File     file;
        String   displayName;
        String[] sectionPaths; // all file paths in the same section (for viewer)
        int      sectionIndex; // this file's position in sectionPaths

        BrowseItem(int type) { this.type = type; }
    }

    // ─── Adapter state ────────────────────────────────────────────────────

    private final List<BrowseItem> items     = new ArrayList<>();
    private final LayoutInflater   inflater;
    private final ThumbnailLoader  loader;
    private final boolean          encrypted;

    public FolderAdapter(Context context, boolean encrypted) {
        this.inflater   = LayoutInflater.from(context);
        this.encrypted  = encrypted;
        this.loader     = new ThumbnailLoader();
    }

    public void setItems(List<BrowseItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Must be called from the owning Activity's {@code onDestroy()}. */
    public void destroy() {
        loader.destroy();
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
            default:                 return getMediaView(item, convertView, parent);
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
        return convertView;
    }

    private View getFolderHeaderView(BrowseItem item, View convertView, ViewGroup parent) {
        FolderHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_browse_header_folder, parent, false);
            holder = new FolderHolder();
            holder.thumb = convertView.findViewById(R.id.folderThumb);
            holder.name  = convertView.findViewById(R.id.tvFolderName);
            holder.count = convertView.findViewById(R.id.tvFolderCount);
            convertView.setTag(holder);
        } else {
            holder = (FolderHolder) convertView.getTag();
        }
        holder.name.setText(item.title);
        holder.count.setText(item.subtitle);
        if (item.previewFile != null) {
            loader.loadThumbnail(item.previewFile, encrypted, holder.thumb);
        } else {
            holder.thumb.setTag(null);
            holder.thumb.setImageResource(android.R.drawable.ic_menu_view);
            holder.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
        return convertView;
    }

    private View getMediaView(BrowseItem item, View convertView, ViewGroup parent) {
        MediaHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_browse_media, parent, false);
            holder = new MediaHolder();
            holder.thumb = convertView.findViewById(R.id.mediaThumb);
            holder.name  = convertView.findViewById(R.id.tvMediaName);
            convertView.setTag(holder);
        } else {
            holder = (MediaHolder) convertView.getTag();
        }
        holder.name.setText(item.displayName);
        loader.loadThumbnail(item.file, encrypted, holder.thumb);
        return convertView;
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────

    private static class DateHolder   { TextView label, count; }
    private static class FolderHolder { ImageView thumb; TextView name, count; }
    private static class MediaHolder  { ImageView thumb; TextView name; }
}
