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

public class AlbumAdapter extends BaseAdapter {

    public static final int TYPE_ALBUM = 0;
    public static final int TYPE_ADD   = 1;

    public static class AlbumItem {
        public final File   dir;   // null = "All Files"
        public final String name;
        public final int    count;
        public final File   cover;
        public final int    type;

        public AlbumItem(File dir, String name, int count, File cover) {
            this.dir = dir; this.name = name; this.count = count;
            this.cover = cover; this.type = TYPE_ALBUM;
        }

        /** "New Album" placeholder entry. */
        public AlbumItem() {
            this.dir = null; this.name = null; this.count = 0;
            this.cover = null; this.type = TYPE_ADD;
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
    @Override public int getViewTypeCount()          { return 2; }
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
