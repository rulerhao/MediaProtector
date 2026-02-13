package com.rulerhao.media_protector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaAdapter extends BaseAdapter {

    private final List<File> files = new ArrayList<>();
    private final LayoutInflater inflater;
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MediaAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setFiles(List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return files.size();
    }

    @Override
    public Object getItem(int position) {
        return files.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private boolean showEncrypted = true;
    private final java.util.Set<File> selectedFiles = new java.util.HashSet<>();

    public void setShowEncrypted(boolean showEncrypted) {
        this.showEncrypted = showEncrypted;
    }

    public void updateSelection(java.util.Set<File> selection) {
        this.selectedFiles.clear();
        this.selectedFiles.addAll(selection);
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_media, parent, false);
            holder = new ViewHolder();
            holder.thumbnail = convertView.findViewById(R.id.thumbnail);
            holder.filename = convertView.findViewById(R.id.filename);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File file = files.get(position);
        holder.filename.setText(showEncrypted ? HeaderObfuscator.getOriginalName(file) : file.getName());
        holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery); // Placeholder
        holder.thumbnail.setTag(file.getAbsolutePath()); // For async checking

        // Selection Visual
        if (selectedFiles.contains(file)) {
            holder.thumbnail.setAlpha(0.5f);
            holder.filename.setTextColor(0xFFFF0000); // Red for selected
        } else {
            holder.thumbnail.setAlpha(1.0f);
            holder.filename.setTextColor(0xFF000000); // Black for normal
        }

        // Load thumbnail asynchronously
        executor.execute(() -> {
            try {
                final Bitmap bitmap;
                if (showEncrypted) {
                    // Decrypt on the fly!
                    InputStream componentStream = obfuscator.getDecryptedStream(file);

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4; // Downsample for grid

                    bitmap = BitmapFactory.decodeStream(componentStream, null, options);
                    componentStream.close();
                } else {
                    // Load normal file
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4;
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                }

                if (bitmap != null) {
                    mainHandler.post(() -> {
                        // Check if view is still reused for the same file
                        if (file.getAbsolutePath().equals(holder.thumbnail.getTag())) {
                            holder.thumbnail.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        ImageView thumbnail;
        TextView filename;
    }
}
