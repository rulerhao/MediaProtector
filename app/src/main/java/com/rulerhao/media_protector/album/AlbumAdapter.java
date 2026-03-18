package com.rulerhao.media_protector.album;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.media.ThumbnailLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AlbumAdapter extends BaseAdapter {

    public static final int TYPE_ALBUM  = 0;
    public static final int TYPE_ADD    = 1;
    /** Special action card (e.g. "Main Collection", "Remove from Album"). */
    public static final int TYPE_ACTION = 2;

    public static class AlbumItem {
        public final File   dir;   // null = "All Files" / action placeholder
        public final String name;
        public final int    count;
        public final File   cover;
        public final int    type;
        /** Icon resource for TYPE_ACTION items (0 = none). */
        public final int    actionIconRes;

        public AlbumItem(File dir, String name, int count, File cover) {
            this.dir = dir; this.name = name; this.count = count;
            this.cover = cover; this.type = TYPE_ALBUM; this.actionIconRes = 0;
        }

        /** "New Album" placeholder entry. */
        public AlbumItem() {
            this.dir = null; this.name = null; this.count = 0;
            this.cover = null; this.type = TYPE_ADD; this.actionIconRes = 0;
        }

        /** Action card (Main Collection, Remove from Album, etc.). */
        public AlbumItem(String actionName, int iconRes) {
            this.dir = null; this.name = actionName; this.count = -1;
            this.cover = null; this.type = TYPE_ACTION; this.actionIconRes = iconRes;
        }
    }

    private final List<AlbumItem> items = new ArrayList<>();
    private final LayoutInflater  inflater;
    private final ThumbnailLoader thumbnailLoader;

    public AlbumAdapter(Context context) {
        this.inflater       = LayoutInflater.from(context);
        this.thumbnailLoader = ThumbnailLoader.getInstance();
    }

    public void setItems(List<AlbumItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override public int getCount()                  { return items.size(); }
    @Override public AlbumItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position)    { return position; }
    @Override public int getViewTypeCount()          { return 3; }
    @Override public int getItemViewType(int pos)    { return items.get(pos).type; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AlbumItem item = items.get(position);

        if (item.type == TYPE_ADD) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_album_add, parent, false);
            }
            return convertView;
        }

        if (item.type == TYPE_ACTION) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_album_action, parent, false);
            }
            ImageView icon  = convertView.findViewById(R.id.actionIcon);
            TextView  label = convertView.findViewById(R.id.actionLabel);
            label.setText(item.name);
            if (item.actionIconRes != 0) {
                icon.setImageResource(item.actionIconRes);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
            return convertView;
        }

        // Normal album card
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_album, parent, false);
            holder = new ViewHolder();
            holder.cover = convertView.findViewById(R.id.albumCover);
            holder.name  = convertView.findViewById(R.id.albumName);
            holder.count = convertView.findViewById(R.id.albumCount);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.name.setText(item.name);
        holder.count.setText(item.count + " files");

        if (item.cover != null) {
            thumbnailLoader.loadThumbnail(item.cover, true, holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.ic_empty_folder);
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView cover;
        TextView  name;
        TextView  count;
    }
}
