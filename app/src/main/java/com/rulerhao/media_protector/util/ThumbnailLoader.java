package com.rulerhao.media_protector.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads media thumbnails asynchronously with an LRU bitmap cache.
 *
 * <ul>
 *   <li>For encrypted files, uses {@link HeaderObfuscator#getDecryptedStream} to decode
 *       the thumbnail on-the-fly without writing a temporary file.</li>
 *   <li>Uses an {@link ImageView} tag to discard results that arrive for recycled views.</li>
 *   <li>Call {@link #destroy()} when the owning adapter/context is torn down to stop
 *       background threads and release cached bitmaps.</li>
 * </ul>
 */
public class ThumbnailLoader {

    private static final int THREAD_COUNT = 4;
    private static final int CACHE_SIZE   = 30; // max cached bitmaps

    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_SIZE);
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();

    /**
     * Loads a thumbnail for {@code file} into {@code target}.
     * Must be called from the main thread.
     *
     * @param file      the file to decode
     * @param encrypted whether the file is AES-encrypted (.mprot)
     * @param target    the ImageView to populate
     */
    public void loadThumbnail(File file, boolean encrypted, ImageView target) {
        String key = file.getAbsolutePath();
        target.setTag(key); // used to detect stale results after view recycling

        Bitmap cached = cache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        // Reset placeholder while loading
        target.setImageBitmap(null);

        executor.execute(() -> {
            Bitmap bmp = decode(file, encrypted);
            if (bmp != null) {
                cache.put(key, bmp);
                mainHandler.post(() -> {
                    // Only update if the view is still waiting for this file
                    if (key.equals(target.getTag())) {
                        target.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    /** Shuts down the thread pool and evicts all cached bitmaps. */
    public void destroy() {
        executor.shutdownNow();
        cache.evictAll();
    }

    // -------------------------------------------------------------------------

    private Bitmap decode(File file, boolean encrypted) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4; // 1/4 resolution for grid display
        try {
            if (encrypted) {
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    return BitmapFactory.decodeStream(is, null, opts);
                }
            } else {
                return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
        } catch (IOException e) {
            // File may be unreadable or corrupt; silently skip
            return null;
        }
    }
}
