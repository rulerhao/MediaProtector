package com.rulerhao.media_protector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;
import com.rulerhao.media_protector.util.EncryptedMediaDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderAdapter extends BaseAdapter {

    private static final int SAMPLE_SIZE = 4; // ~1/4 resolution; sufficient for a 56dp thumbnail

    private final List<File>          folders     = new ArrayList<>();
    private final LayoutInflater      inflater;
    private final boolean             encrypted;
    private final ExecutorService     executor    = Executors.newFixedThreadPool(3);
    private final Handler             mainHandler = new Handler(Looper.getMainLooper());
    /** Caches the decoded preview bitmap per folder absolute path. */
    private final LruCache<String, Bitmap> cache  = new LruCache<>(60);
    private final HeaderObfuscator    obfuscator  = new HeaderObfuscator();

    public FolderAdapter(Context context, boolean encrypted) {
        this.inflater  = LayoutInflater.from(context);
        this.encrypted = encrypted;
    }

    public void setFolders(List<File> newFolders) {
        folders.clear();
        folders.addAll(newFolders);
        notifyDataSetChanged();
    }

    /** Shuts down the background thread pool and evicts all cached bitmaps. */
    public void destroy() {
        executor.shutdownNow();
        cache.evictAll();
    }

    @Override public int     getCount()              { return folders.size(); }
    @Override public Object  getItem(int position)   { return folders.get(position); }
    @Override public long    getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_folder, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.folderIcon);
            holder.name = convertView.findViewById(R.id.folderName);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File folder = folders.get(position);
        boolean isParent = folder.getName().equals("..");
        holder.name.setText(isParent ? ".." : folder.getName());

        if (isParent) {
            holder.icon.setTag(null);
            holder.icon.setImageResource(android.R.drawable.ic_menu_upload);
            holder.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            return convertView;
        }

        // Tag the view for stale-result detection when the list scrolls.
        String key = folder.getAbsolutePath();
        holder.icon.setTag(key);

        Bitmap cached = cache.get(key);
        if (cached != null) {
            holder.icon.setImageBitmap(cached);
            holder.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // Show generic folder icon while loading.
            holder.icon.setImageResource(android.R.drawable.ic_menu_view);
            holder.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            loadPreviewAsync(folder, key, holder.icon);
        }

        return convertView;
    }

    // -------------------------------------------------------------------------
    // Async preview loading
    // -------------------------------------------------------------------------

    private void loadPreviewAsync(File folder, String key, ImageView target) {
        executor.execute(() -> {
            File mediaFile = findNewestMediaFile(folder);
            if (mediaFile == null) return; // no preview available; keep folder icon

            Bitmap bmp = decodeThumbnail(mediaFile);
            if (bmp == null) return;

            cache.put(key, bmp);
            mainHandler.post(() -> {
                if (key.equals(target.getTag())) {
                    target.setImageBitmap(bmp);
                    target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            });
        });
    }

    /**
     * Scans the direct children of {@code folder} and returns the media file
     * with the latest {@code lastModified} timestamp, or {@code null} if none.
     * Only one level deep — no recursion — so this stays fast on the background thread.
     */
    private File findNewestMediaFile(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return null;

        File newest    = null;
        long newestTs  = 0;

        for (File f : files) {
            if (!f.isFile()) continue;
            boolean isMatch = encrypted
                    ? FileConfig.isEncryptedFile(f.getName())
                    : FileConfig.isRegularMediaFile(f.getName());
            if (isMatch && f.lastModified() > newestTs) {
                newestTs = f.lastModified();
                newest   = f;
            }
        }
        return newest;
    }

    /**
     * Decodes a thumbnail bitmap from {@code file}, handling both encrypted and
     * plain media (images and videos).
     */
    private Bitmap decodeThumbnail(File file) {
        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(file)
                : file.getName();

        if (FileConfig.isVideoFile(originalName)) {
            return decodeVideoFrame(file);
        }
        return decodeImageThumb(file);
    }

    private Bitmap decodeImageThumb(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = SAMPLE_SIZE;
        try {
            if (encrypted) {
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    return BitmapFactory.decodeStream(is, null, opts);
                }
            } else {
                return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private Bitmap decodeVideoFrame(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        EncryptedMediaDataSource dataSource = null;
        try {
            if (encrypted) {
                dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }
            Bitmap frame = retriever.getFrameAtTime(
                    0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            int w = Math.max(1, frame.getWidth()  / SAMPLE_SIZE);
            int h = Math.max(1, frame.getHeight() / SAMPLE_SIZE);
            Bitmap scaled = Bitmap.createScaledBitmap(frame, w, h, false);
            frame.recycle();
            return scaled;
        } catch (Exception e) {
            // RuntimeException from MediaMetadataRetriever on some OEM devices
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
            if (dataSource != null) {
                try { dataSource.close(); } catch (IOException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------

    private static class ViewHolder {
        ImageView icon;
        TextView  name;
    }
}
