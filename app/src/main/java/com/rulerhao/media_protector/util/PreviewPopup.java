package com.rulerhao.media_protector.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a larger preview popup when long-pressing on a thumbnail.
 */
public class PreviewPopup {

    private static final int PREVIEW_SIZE_DP = 280;
    private static final int PADDING_DP = 16;
    private static final int FILENAME_HEIGHT_DP = 32;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();

    private PopupWindow popupWindow;
    private ImageView previewImage;
    private ProgressBar progressBar;
    private TextView tvFilename;

    public PreviewPopup(Context context) {
        this.context = context;
        createPopup();
    }

    private void createPopup() {
        float density = context.getResources().getDisplayMetrics().density;
        int previewSize = (int) (PREVIEW_SIZE_DP * density);
        int padding = (int) (PADDING_DP * density);
        int filenameHeight = (int) (FILENAME_HEIGHT_DP * density);

        // Create container
        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(0xE6000000); // Semi-transparent black
        container.setPadding(padding, padding, padding, padding);

        // Create preview image
        previewImage = new ImageView(context);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(
                previewSize, previewSize);
        previewImage.setLayoutParams(imageLp);
        container.addView(previewImage);

        // Create progress bar
        progressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(progressLp);
        container.addView(progressBar);

        // Create filename text
        tvFilename = new TextView(context);
        tvFilename.setTextColor(Color.WHITE);
        tvFilename.setTextSize(12);
        tvFilename.setGravity(Gravity.CENTER);
        tvFilename.setMaxLines(2);
        tvFilename.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        FrameLayout.LayoutParams filenameLp = new FrameLayout.LayoutParams(
                previewSize, filenameHeight);
        filenameLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tvFilename.setLayoutParams(filenameLp);
        container.addView(tvFilename);

        // Create popup window
        int totalSize = previewSize + padding * 2;
        popupWindow = new PopupWindow(container, totalSize, totalSize + filenameHeight);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(16 * density);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
    }

    /**
     * Shows the preview popup for the given file at the specified anchor view.
     */
    public void show(View anchor, File file, boolean encrypted) {
        if (popupWindow == null || popupWindow.isShowing()) return;

        // Reset state
        previewImage.setImageBitmap(null);
        progressBar.setVisibility(View.VISIBLE);

        // Set filename
        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(file)
                : file.getName();
        tvFilename.setText(originalName);

        // Show popup centered on screen
        popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);

        // Load preview image
        loadPreview(file, encrypted, originalName);
    }

    private void loadPreview(File file, boolean encrypted, String originalName) {
        executor.execute(() -> {
            Bitmap bitmap = null;

            // Check if it's a video - for videos, just show a larger thumbnail
            if (FileConfig.isVideoFile(originalName)) {
                // Use ThumbnailLoader's video frame extraction
                bitmap = decodeVideoFrame(file, encrypted);
            } else {
                // For images, decode at a larger size
                bitmap = decodeImage(file, encrypted);
            }

            final Bitmap result = bitmap;
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (result != null) {
                    previewImage.setImageBitmap(result);
                }
            });
        });
    }

    private Bitmap decodeImage(File file, boolean encrypted) {
        try {
            float density = context.getResources().getDisplayMetrics().density;
            int targetSize = (int) (PREVIEW_SIZE_DP * density);

            // First pass: get dimensions
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;

            if (encrypted) {
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    BitmapFactory.decodeStream(is, null, opts);
                }
            } else {
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }

            // Calculate sample size
            int sampleSize = 1;
            int minDim = Math.min(opts.outWidth, opts.outHeight);
            while (minDim / sampleSize > targetSize * 2) {
                sampleSize *= 2;
            }

            // Second pass: decode
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;

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

    private Bitmap decodeVideoFrame(File file, boolean encrypted) {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        EncryptedMediaDataSource dataSource = null;
        try {
            if (encrypted) {
                dataSource = new EncryptedMediaDataSource(file);
                retriever.setDataSource(dataSource);
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }

            return retriever.getFrameAtTime(0,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
            if (dataSource != null) {
                try { dataSource.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Dismisses the preview popup.
     */
    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /**
     * Returns true if the popup is currently showing.
     */
    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    /**
     * Cleans up resources.
     */
    public void destroy() {
        dismiss();
        executor.shutdownNow();
    }
}
