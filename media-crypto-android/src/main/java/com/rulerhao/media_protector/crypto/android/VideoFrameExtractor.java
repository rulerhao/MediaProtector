package com.rulerhao.media_protector.crypto.android;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.io.File;

/**
 * Utility class for extracting video frames/thumbnails.
 * Handles both encrypted and unencrypted video files.
 */
public final class VideoFrameExtractor {

    private VideoFrameExtractor() {} // Prevent instantiation

    /**
     * Extracts a frame from a video file for use as a thumbnail.
     *
     * @param file The video file
     * @return A bitmap of the video frame, or null if extraction fails
     */
    public static Bitmap extractFrame(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (FileStreamFactory.isEncrypted(file)) {
                EncryptedMediaDataSource dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }
            return retriever.getFrameAtTime();
        } catch (Exception e) {
            // MIUI devices may throw RuntimeException from setDataSource
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Extracts a frame at a specific time position.
     *
     * @param file   The video file
     * @param timeUs Time position in microseconds
     * @return A bitmap of the video frame, or null if extraction fails
     */
    public static Bitmap extractFrameAt(File file, long timeUs) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (FileStreamFactory.isEncrypted(file)) {
                EncryptedMediaDataSource dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }
            return retriever.getFrameAtTime(timeUs);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Gets the duration of a video file in milliseconds.
     *
     * @param file The video file
     * @return Duration in milliseconds, or -1 if unknown
     */
    public static long getDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (FileStreamFactory.isEncrypted(file)) {
                EncryptedMediaDataSource dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return durationStr != null ? Long.parseLong(durationStr) : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }
}
