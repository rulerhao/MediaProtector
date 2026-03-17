package com.rulerhao.media_protector.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.widget.ImageView;

import com.rulerhao.media_protector.data.FileConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads media thumbnails asynchronously with an LRU bitmap cache.
 *
 * <p>This class is implemented as a singleton to share the cache and thread pool
 * across all adapters (MediaAdapter, FolderAdapter), avoiding duplicate memory usage
 * and thread creation.
 *
 * <ul>
 *   <li>For encrypted files, uses {@link FileStreamFactory#createInputStream} to decode
 *       the thumbnail on-the-fly without writing a temporary file.</li>
 *   <li>Uses an {@link ImageView} tag to discard results that arrive for recycled views.</li>
 *   <li>Call {@link #clearCache()} to release cached bitmaps when switching modes.</li>
 * </ul>
 */
public class ThumbnailLoader {

    private static final int THREAD_COUNT = 4;
    /**
     * Minimum cache size regardless of memory constraints.
     */
    private static final int MIN_CACHE_SIZE = 20;
    /**
     * Maximum cache size to prevent excessive memory usage.
     */
    private static final int MAX_CACHE_SIZE = 100;

    // ─── Singleton ───────────────────────────────────────────────────────
    private static volatile ThumbnailLoader instance;
    private static int targetThumbnailSize = 200; // default, will be calculated based on screen
    private static int dynamicCacheSize = 50; // default, recalculated based on available memory

    public static ThumbnailLoader getInstance() {
        if (instance == null) {
            synchronized (ThumbnailLoader.class) {
                if (instance == null) {
                    instance = new ThumbnailLoader();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize with context to calculate optimal thumbnail size based on screen density
     * and dynamic cache size based on available memory.
     * Should be called once from Application or MainActivity.
     */
    public static void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // Calculate thumbnail size: screen width / 3 columns (grid), capped at 300dp
        int columnWidth = metrics.widthPixels / 3;
        targetThumbnailSize = Math.min(columnWidth, (int) (300 * metrics.density));

        // Calculate dynamic cache size based on available memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory(); // max heap in bytes

        // Estimate average thumbnail size (ARGB_8888 = 4 bytes per pixel)
        // Using target thumbnail size squared * 4 bytes
        long avgThumbnailBytes = (long) targetThumbnailSize * targetThumbnailSize * 4;

        // Allocate ~15% of max heap for thumbnail cache
        long cacheMemory = maxMemory / 7;
        int calculatedSize = (int) (cacheMemory / avgThumbnailBytes);

        // Clamp to reasonable bounds
        dynamicCacheSize = Math.max(MIN_CACHE_SIZE, Math.min(MAX_CACHE_SIZE, calculatedSize));
    }

    private ThumbnailLoader() {
        // Private constructor for singleton
    }

    // ─── Instance fields ─────────────────────────────────────────────────
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    {
        // Initialize cache with dynamic size (defaults to 50 if init() not called)
        cache = new LruCache<>(dynamicCacheSize);
    }

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

    /**
     * Clears all cached bitmaps. Call when switching modes to free memory.
     * Does NOT shut down the executor since this is a shared singleton.
     */
    public void clearCache() {
        cache.evictAll();
    }

    // -------------------------------------------------------------------------

    private Bitmap decode(File file, boolean encrypted) {
        // Resolve the original filename so we can detect the media type correctly.
        String originalName = FileStreamFactory.getOriginalName(file);

        if (FileConfig.isVideoFile(originalName)) {
            return decodeVideoFrame(file, encrypted);
        }

        // Image path: decode with adaptive sampling based on target thumbnail size.
        try {
            // First, decode bounds only to calculate optimal sample size
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            try (InputStream is = FileStreamFactory.createInputStream(file)) {
                BitmapFactory.decodeStream(is, null, boundsOpts);
            }

            // Calculate optimal sample size
            int sampleSize = calculateSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetThumbnailSize);

            // Now decode with calculated sample size
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            try (InputStream is = FileStreamFactory.createInputStream(file)) {
                return BitmapFactory.decodeStream(is, null, decodeOpts);
            }
        } catch (IOException e) {
            // File may be unreadable or corrupt; silently skip
            return null;
        }
    }

    /**
     * Calculates the optimal sample size for decoding an image.
     * Returns a power of 2 that will result in a bitmap close to the target size.
     */
    private int calculateSampleSize(int width, int height, int targetSize) {
        int sampleSize = 1;
        int minDimension = Math.min(width, height);

        // Double sample size until image fits within target
        while (minDimension / sampleSize > targetSize * 2) {
            sampleSize *= 2;
        }

        return Math.max(1, sampleSize);
    }

    /**
     * Extracts a representative frame from a video file using {@link MediaMetadataRetriever}.
     * For encrypted files, feeds the retriever via {@link EncryptedMediaDataSource} so no
     * temporary copy is written to disk.
     */
    private Bitmap decodeVideoFrame(File file, boolean encrypted) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        EncryptedMediaDataSource dataSource = null;
        try {
            if (FileStreamFactory.isEncrypted(file)) {
                dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }

            Bitmap frame = retriever.getFrameAtTime(
                    0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;

            // Scale down to target thumbnail size while maintaining aspect ratio
            int origW = frame.getWidth();
            int origH = frame.getHeight();
            float scale = (float) targetThumbnailSize / Math.min(origW, origH);
            int w = Math.max(1, (int) (origW * scale));
            int h = Math.max(1, (int) (origH * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(frame, w, h, true);
            frame.recycle();
            return scaled;

        } catch (Exception e) {
            // MediaMetadataRetriever.setDataSource(MediaDataSource) can throw RuntimeException
            // (status 0x80000000) on some OEM/MIUI devices that don't fully support the
            // MediaDataSource callback interface.  Fall through to return null so the grid
            // shows the placeholder rather than crashing.
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
            if (dataSource != null) {
                try { dataSource.close(); } catch (IOException ignored) {}
            }
        }
    }
}
