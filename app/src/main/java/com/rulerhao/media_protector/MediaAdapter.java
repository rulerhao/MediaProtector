package com.rulerhao.media_protector;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;
import com.rulerhao.media_protector.util.ThumbnailLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaAdapter extends BaseAdapter {

    private final List<File> files = new ArrayList<>();
    private final LayoutInflater inflater;
    private final ThumbnailLoader thumbnailLoader;

    private boolean showEncrypted = true;
    private final Set<File> selectedFiles = new HashSet<>();

    public MediaAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
        this.thumbnailLoader = new ThumbnailLoader();
    }

    public void setFiles(List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    public void setShowEncrypted(boolean showEncrypted) {
        this.showEncrypted = showEncrypted;
    }

    public void updateSelection(Set<File> selection) {
        selectedFiles.clear();
        selectedFiles.addAll(selection);
        notifyDataSetChanged();
    }

    /** Must be called from {@code Activity.onDestroy()} to release background threads. */
    public void destroy() {
        thumbnailLoader.destroy();
    }

    @Override public int getCount() { return files.size(); }
    @Override public Object getItem(int position) { return files.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_media, parent, false);
            holder = new ViewHolder();
            holder.thumbnail        = convertView.findViewById(R.id.thumbnail);
            holder.filename         = convertView.findViewById(R.id.filename);
            holder.videoBadge       = convertView.findViewById(R.id.videoBadge);
            holder.selectionOverlay = convertView.findViewById(R.id.selectionOverlay);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File file = files.get(position);
        String originalName = showEncrypted
                ? HeaderObfuscator.getOriginalName(file)
                : file.getName();

        holder.filename.setText(originalName);

        // Show video badge for video files
        holder.videoBadge.setVisibility(
                FileConfig.isVideoFile(originalName) ? View.VISIBLE : View.GONE);

        // Show selection overlay instead of alpha-dimming
        boolean selected = selectedFiles.contains(file);
        holder.thumbnail.setAlpha(1.0f);
        holder.selectionOverlay.setVisibility(selected ? View.VISIBLE : View.GONE);

        thumbnailLoader.loadThumbnail(file, showEncrypted, holder.thumbnail);

        return convertView;
    }

    private static class ViewHolder {
        ImageView thumbnail;
        TextView  filename;
        TextView  videoBadge;
        View      selectionOverlay;
    }
}
