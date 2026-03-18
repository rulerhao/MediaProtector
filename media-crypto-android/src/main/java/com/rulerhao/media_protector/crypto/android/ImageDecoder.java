package com.rulerhao.media_protector.crypto.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.InputStream;

/**
 * Utility class for decoding images with sample size calculation.
 * Handles both encrypted and unencrypted image files.
 */
public final class ImageDecoder {

    private ImageDecoder() {} // Prevent instantiation

    /**
     * Decodes an image file with automatic sample size calculation.
     *
     * @param file      The image file to decode
     * @param maxWidth  Maximum desired width
     * @param maxHeight Maximum desired height
     * @return Decoded bitmap, or null if decoding fails
     */
    public static Bitmap decode(File file, int maxWidth, int maxHeight) {
        try {
            // Pass 1: Get dimensions without loading pixels
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;

            try (InputStream in = FileStreamFactory.createInputStream(file)) {
                BitmapFactory.decodeStream(in, null, opts);
            }

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }

            // Calculate sample size
            opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, maxWidth, maxHeight);
            opts.inJustDecodeBounds = false;

            // Pass 2: Decode with sample size
            try (InputStream in = FileStreamFactory.createInputStream(file)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decodes an image file for thumbnail use with a fixed sample size.
     *
     * @param file       The image file to decode
     * @param sampleSize The sample size to use (1 = full size, 2 = 1/2, 4 = 1/4, etc.)
     * @return Decoded bitmap, or null if decoding fails
     */
    public static Bitmap decodeWithSampleSize(File file, int sampleSize) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;

            try (InputStream in = FileStreamFactory.createInputStream(file)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calculates the optimal sample size for loading an image.
     *
     * @param srcWidth   Source image width
     * @param srcHeight  Source image height
     * @param maxWidth   Maximum desired width
     * @param maxHeight  Maximum desired height
     * @return Sample size (power of 2)
     */
    public static int calculateSampleSize(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
        int sampleSize = 1;
        while (srcWidth / (sampleSize * 2) >= maxWidth && srcHeight / (sampleSize * 2) >= maxHeight) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * Decodes an image from an input stream with the given sample size.
     *
     * @param inputStream The input stream to decode from
     * @param sampleSize  The sample size to use
     * @return Decoded bitmap, or null if decoding fails
     */
    public static Bitmap decodeStream(InputStream inputStream, int sampleSize) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            return BitmapFactory.decodeStream(inputStream, null, opts);
        } catch (Exception e) {
            return null;
        }
    }
}
