package com.rulerhao.media_protector.media;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.rulerhao.media_protector.widget.ZoomableImageView;
import com.rulerhao.media_protector.widget.ZoomableTextureView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import android.widget.Toast;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.album.AlbumManager;
import com.rulerhao.media_protector.core.FileConfig;
import com.rulerhao.media_protector.crypto.android.EncryptedMediaDataSource;
import com.rulerhao.media_protector.security.OriginalPathStore;
import com.rulerhao.media_protector.shared.ThemeHelper;

import android.app.AlertDialog;
import android.widget.EditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen viewer with two explicit display modes:
 *
 * <ul>
 *   <li><b>Operator mode</b> — top bar, video controls (when applicable), and thumbnail
 *       filmstrip at the bottom are all visible. Tap the media area to enter immersive mode.</li>
 *   <li><b>Immersive mode</b> — all overlays are hidden; pure full-screen view.
 *       Tap the media area to return to operator mode.</li>
 * </ul>
 *
 * <p>Swipe left/right anywhere outside the bottom area to navigate to the previous/next file.
 * Tapping a thumbnail in the filmstrip jumps directly to that file.
 */
public class MediaViewerActivity extends Activity implements TextureView.SurfaceTextureListener {

    /** String[] — all file paths in the current view (enables swipe navigation). */
    public static final String EXTRA_FILE_LIST  = "file_list";
    /** int — index within EXTRA_FILE_LIST for the initially displayed file. */
    public static final String EXTRA_FILE_INDEX = "file_index";
    /** String — legacy single-file path; used when EXTRA_FILE_LIST is absent. */
    public static final String EXTRA_FILE_PATH  = "file_path";
    /** boolean — whether the files are AES-encrypted (.mprot). */
    public static final String EXTRA_ENCRYPTED  = "encrypted";

    private enum ViewerMode { OPERATOR, IMMERSIVE }

    private static final int SEEK_UPDATE_MS     = 500;
    private static final int SWIPE_MIN_DISTANCE = 80;    // px
    private static final int SWIPE_MIN_VELOCITY = 100;   // px/s
    private static final int THUMB_SIZE_DP      = 64;
    private static final int THUMB_MARGIN_DP    = 4;

    // ── Views ─────────────────────────────────────────────────────────────
    private View             viewerTopBar;
    private TextView         tvFilename;
    private ZoomableImageView imageView;
    private ZoomableTextureView textureView;
    private Surface          videoSurface;
    private LinearLayout     bottomArea;       // wraps videoControls + thumbnailStrip
    private LinearLayout     videoControls;
    private ImageButton      btnPlayPause;
    private SeekBar          seekBar;
    private TextView         tvDuration;
    private HorizontalScrollView thumbnailStrip;
    private LinearLayout     thumbnailContainer;
    private ProgressBar      progressBar;
    private TextView         tvError;

    // ── Thumbnail filmstrip ────────────────────────────────────────────────
    private FrameLayout[]  thumbContainers;
    private ThumbnailLoader stripLoader;

    // ── Crypto ───────────────────────────────────────────────────────────────
    private Button btnCrypto;
    private Button btnMoveToAlbum;
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();
    private volatile boolean cryptoInProgress = false;

    /** Tracks files that were encrypted/decrypted during this session */
    private final List<String> processedFiles = new ArrayList<>();
    public static final String EXTRA_PROCESSED_FILES = "processed_files";

    // ── Playback ──────────────────────────────────────────────────────────
    private MediaPlayer              mediaPlayer;
    private EncryptedMediaDataSource mediaDataSource;
    private boolean surfaceReady    = false;
    private boolean playerPrepared  = false;
    private boolean startOnPrepared = false;
    private boolean isVideoMode     = false;

    // ── Navigation & mode ─────────────────────────────────────────────────
    private String[]   fileList;
    private int        currentIndex;
    private boolean    encrypted;
    private File       mediaFile;
    private ViewerMode viewerMode = ViewerMode.OPERATOR;

    // ── Misc ──────────────────────────────────────────────────────────────
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable        seekTick    = this::tickSeekBar;
    private GestureDetector       gestureDetector;
    private final ExecutorService ioExecutor  = Executors.newSingleThreadExecutor();

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_media_viewer);

        viewerTopBar       = findViewById(R.id.viewerTopBar);
        tvFilename         = findViewById(R.id.tvFilename);
        imageView          = findViewById(R.id.imageView);
        textureView        = findViewById(R.id.textureView);
        bottomArea         = findViewById(R.id.bottomArea);
        videoControls      = findViewById(R.id.videoControls);
        btnPlayPause       = findViewById(R.id.btnPlayPause);
        seekBar            = findViewById(R.id.seekBar);
        tvDuration         = findViewById(R.id.tvDuration);
        thumbnailStrip     = findViewById(R.id.thumbnailStrip);
        thumbnailContainer = findViewById(R.id.thumbnailContainer);
        progressBar        = findViewById(R.id.progressBar);
        tvError            = findViewById(R.id.tvError);

        textureView.setSurfaceTextureListener(this);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        btnCrypto = findViewById(R.id.btnCrypto);
        btnCrypto.setOnClickListener(v -> {
            if (encrypted) {
                // Decrypt directly
                performCrypto();
            } else {
                // Encrypt - show album selection dialog
                showEncryptToAlbumDialog();
            }
        });

        // Move to Album button (only visible for encrypted files)
        btnMoveToAlbum = findViewById(R.id.btnMoveToAlbum);
        if (btnMoveToAlbum != null) {
            btnMoveToAlbum.setOnClickListener(v -> showMoveToAlbumDialog());
        }

        ImageButton btnInfo = findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(v -> showFileInfo());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && playerPrepared && mediaPlayer != null)
                    mediaPlayer.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { stopSeekTicks(); }
            @Override public void onStopTrackingTouch(SeekBar bar)  { startSeekTicks(); }
        });

        // Resolve file list from intent.
        encrypted = getIntent().getBooleanExtra(EXTRA_ENCRYPTED, false);
        String[] list = getIntent().getStringArrayExtra(EXTRA_FILE_LIST);
        if (list != null && list.length > 0) {
            fileList     = list;
            currentIndex = getIntent().getIntExtra(EXTRA_FILE_INDEX, 0);
            currentIndex = Math.max(0, Math.min(currentIndex, fileList.length - 1));
        } else {
            String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
            if (path == null) { showError(getString(R.string.error_generic)); return; }
            fileList     = new String[]{ path };
            currentIndex = 0;
        }

        stripLoader = ThumbnailLoader.getInstance();
        buildThumbnailStrip();

        // Set up tap listener on ZoomableImageView for UI toggle when not zoomed
        imageView.setOnSingleTapListener(this::toggleMode);
        // Set up tap listener on ZoomableTextureView for UI toggle
        textureView.setOnSingleTapListener(this::toggleMode);

        // Gesture: single tap on media area → toggle mode; horizontal fling → navigate.
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // Let ZoomableImageView/ZoomableTextureView handle taps
                // They will call toggleMode() via their tap listeners
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null) return false;
                // Don't intercept flings when image is zoomed (allow panning)
                if (!isVideoMode && imageView.isZoomed()) return false;
                // Don't intercept flings when video is zoomed (allow panning)
                if (isVideoMode && textureView.isZoomed()) return false;
                // Don't intercept flings that start inside the bottom area.
                if (bottomArea.getVisibility() == View.VISIBLE
                        && e1.getY() >= bottomArea.getTop()) return false;
                float dX = e2.getX() - e1.getX();
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dX) > Math.abs(dY)
                        && Math.abs(dX) > SWIPE_MIN_DISTANCE
                        && Math.abs(vX) > SWIPE_MIN_VELOCITY) {
                    if (dX > 0) navigatePrev();
                    else        navigateNext();
                    return true;
                }
                return false;
            }
        });

        loadCurrentMedia();
        hideSystemBars();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            stopSeekTicks();
        }
    }

    @Override
    public void finish() {
        // Return list of processed files to caller
        if (!processedFiles.isEmpty()) {
            Intent data = new Intent();
            data.putStringArrayListExtra(EXTRA_PROCESSED_FILES, new ArrayList<>(processedFiles));
            setResult(RESULT_OK, data);
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        stopSeekTicks();
        releasePlayer();
        // ThumbnailLoader is a singleton, no need to destroy; cache persists.
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode: OPERATOR ↔ IMMERSIVE
    // ─────────────────────────────────────────────────────────────────────

    private void toggleMode() {
        setMode(viewerMode == ViewerMode.OPERATOR ? ViewerMode.IMMERSIVE : ViewerMode.OPERATOR);
    }

    private void setMode(ViewerMode mode) {
        viewerMode = mode;
        boolean show = (mode == ViewerMode.OPERATOR);

        // Top bar
        viewerTopBar.animate().cancel();
        if (show) {
            if (viewerTopBar.getVisibility() != View.VISIBLE) {
                viewerTopBar.setAlpha(0f);
                viewerTopBar.setVisibility(View.VISIBLE);
                viewerTopBar.animate().alpha(1f).setDuration(200).start();
            }
        } else {
            viewerTopBar.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        if (viewerMode == ViewerMode.IMMERSIVE)
                            viewerTopBar.setVisibility(View.GONE);
                    }).start();
        }

        // Bottom area (video controls + filmstrip)
        bottomArea.animate().cancel();
        if (show) {
            if (bottomArea.getVisibility() != View.VISIBLE) {
                bottomArea.setAlpha(0f);
                bottomArea.setVisibility(View.VISIBLE);
                bottomArea.animate().alpha(1f).setDuration(200).start();
            }
        } else {
            bottomArea.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        if (viewerMode == ViewerMode.IMMERSIVE)
                            bottomArea.setVisibility(View.GONE);
                    }).start();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────

    private void navigatePrev() {
        if (fileList != null && currentIndex > 0) navigateTo(currentIndex - 1);
    }

    private void navigateNext() {
        if (fileList != null && currentIndex < fileList.length - 1) navigateTo(currentIndex + 1);
    }

    private void navigateTo(int index) {
        currentIndex = index;
        loadCurrentMedia();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Load media (called on every navigation)
    // ─────────────────────────────────────────────────────────────────────

    private void loadCurrentMedia() {
        stopSeekTicks();
        releasePlayer();

        playerPrepared  = false;
        startOnPrepared = false;
        progressBar.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        seekBar.setProgress(0);
        tvDuration.setText("0:00 / 0:00");

        mediaFile = new File(fileList[currentIndex]);
        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(mediaFile)
                : mediaFile.getName();
        tvFilename.setText(originalName);

        // Update crypto button text based on current mode
        updateCryptoButton();
        updateMoveButtonVisibility();

        // Update filmstrip selection and scroll it into view.
        updateThumbnailSelection(currentIndex);
        scrollStripToIndex(currentIndex);

        isVideoMode = FileConfig.isVideoFile(originalName);
        if (isVideoMode) {
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            setupVideo();
        } else {
            textureView.setVisibility(View.GONE);
            videoControls.setVisibility(View.GONE);
            surfaceReady = false;
            loadImage();
        }

        // Always return to operator mode when navigating so the user can see
        // the filename, filmstrip, and video controls.
        setMode(ViewerMode.OPERATOR);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Image loading
    // ─────────────────────────────────────────────────────────────────────

    private void loadImage() {
        imageView.setVisibility(View.VISIBLE);
        imageView.resetZoom(); // Reset zoom state for new image
        progressBar.setVisibility(View.VISIBLE);
        final File target = mediaFile;
        final String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(target)
                : target.getName();
        final boolean isGif = FileConfig.isGifFile(originalName);
        final boolean isHeif = FileConfig.isHeifFile(originalName);

        ioExecutor.execute(() -> {
            if (isGif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for animated GIF support (API 28+)
                Drawable drawable = decodeWithImageDecoder(target);
                mainHandler.post(() -> {
                    if (!target.equals(mediaFile)) return;
                    progressBar.setVisibility(View.GONE);
                    if (drawable != null) {
                        imageView.setImageDrawable(drawable);
                        if (drawable instanceof AnimatedImageDrawable) {
                            ((AnimatedImageDrawable) drawable).start();
                        }
                    } else {
                        showError(getString(R.string.error_load_media));
                    }
                });
            } else if (isHeif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for HEIC/HEIF support (API 28+)
                Bitmap bmp = decodeHeifImage(target);
                mainHandler.post(() -> {
                    if (!target.equals(mediaFile)) return;
                    progressBar.setVisibility(View.GONE);
                    if (bmp != null) imageView.setImageBitmap(bmp);
                    else             showError(getString(R.string.error_load_media));
                });
            } else {
                // Regular image or API < 28 - use bitmap
                Bitmap bmp = decodeImage(target);
                mainHandler.post(() -> {
                    if (!target.equals(mediaFile)) return;
                    progressBar.setVisibility(View.GONE);
                    if (bmp != null) imageView.setImageBitmap(bmp);
                    else             showError(getString(R.string.error_load_media));
                });
            }
        });
    }

    /** Maximum file size (50MB) to load entirely into memory for ImageDecoder. */
    private static final long MAX_MEMORY_DECODE_SIZE = 50 * 1024 * 1024;

    /**
     * Decodes an image using ImageDecoder (API 28+).
     * Supports GIF, HEIC, HEIF, and other formats.
     * For large files, falls back to sampled bitmap decoding to avoid OOM.
     */
    private Drawable decodeWithImageDecoder(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null;
        }

        // Check file size to avoid OOM on large files
        if (file.length() > MAX_MEMORY_DECODE_SIZE && encrypted) {
            // For large encrypted files, fall back to bitmap decode with sampling
            Bitmap bmp = decodeImage(file);
            if (bmp != null) {
                return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
            }
            return null;
        }

        try {
            ImageDecoder.Source source;
            if (encrypted) {
                // For encrypted files, we need to decrypt to a byte array first
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] data = baos.toByteArray();
                    source = ImageDecoder.createSource(ByteBuffer.wrap(data));
                }
            } else {
                source = ImageDecoder.createSource(file);
            }
            return ImageDecoder.decodeDrawable(source);
        } catch (IOException | OutOfMemoryError e) {
            // Fall back to sampled bitmap on OOM
            Bitmap bmp = decodeImage(file);
            if (bmp != null) {
                return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
            }
            return null;
        }
    }

    /**
     * Decodes a HEIC/HEIF image to bitmap using ImageDecoder (API 28+).
     * For large files, uses sampling to avoid OOM.
     */
    private Bitmap decodeHeifImage(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null;
        }

        // Check file size to avoid OOM on large files
        if (file.length() > MAX_MEMORY_DECODE_SIZE && encrypted) {
            return decodeImage(file);
        }

        try {
            ImageDecoder.Source source;
            if (encrypted) {
                // For encrypted files, decrypt to byte array
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] data = baos.toByteArray();
                    source = ImageDecoder.createSource(ByteBuffer.wrap(data));
                }
            } else {
                source = ImageDecoder.createSource(file);
            }
            return ImageDecoder.decodeBitmap(source);
        } catch (IOException | OutOfMemoryError e) {
            // Fall back to regular decode on OOM
            return decodeImage(file);
        }
    }

    private Bitmap decodeImage(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxW = dm.widthPixels;
            int maxH = dm.heightPixels;

            // Pass 1: read image dimensions without allocating any pixel memory.
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            if (encrypted) {
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    BitmapFactory.decodeStream(is, null, opts);
                }
            } else {
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }

            // Pass 2: decode with an inSampleSize that keeps the bitmap within
            // screen dimensions so it never exceeds the Canvas texture limit.
            opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxW, maxH);
            opts.inJustDecodeBounds = false;
            if (encrypted) {
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    return BitmapFactory.decodeStream(is, null, opts);
                }
            } else {
                return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Returns the smallest power-of-2 {@code inSampleSize} such that the decoded
     * image fits within {@code maxW × maxH} pixels.
     */
    private static int calculateInSampleSize(int imgW, int imgH, int maxW, int maxH) {
        int sample = 1;
        while ((imgW / sample) > maxW || (imgH / sample) > maxH) {
            sample *= 2;
        }
        return sample;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Video playback
    // ─────────────────────────────────────────────────────────────────────

    private void setupVideo() {
        videoControls.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        textureView.resetZoom(); // Reset zoom for new video
        if (textureView.getVisibility() == View.VISIBLE) {
            // Video → video: TextureView handles this smoothly
            // We can reuse it, just need to reinit the player
            if (surfaceReady && videoSurface != null) {
                initPlayer();
            }
        } else {
            // Image → video (or first load): show texture view
            textureView.setVisibility(View.VISIBLE);
            // If surface is already ready, init player now
            if (surfaceReady && videoSurface != null) {
                initPlayer();
            }
        }
    }

    private void initPlayer() {
        try {
            mediaPlayer = new MediaPlayer();
            if (encrypted) {
                mediaDataSource = new EncryptedMediaDataSource(mediaFile);
                mediaPlayer.setDataSource(mediaDataSource);
            } else {
                mediaPlayer.setDataSource(mediaFile.getAbsolutePath());
            }
            mediaPlayer.setSurface(videoSurface);

            mediaPlayer.setOnPreparedListener(mp -> {
                playerPrepared = true;
                progressBar.setVisibility(View.GONE);
                seekBar.setMax(mp.getDuration());
                // Set video size for proper zoom handling
                textureView.setVideoSize(mp.getVideoWidth(), mp.getVideoHeight());
                tickSeekBar();
                if (startOnPrepared) {
                    mp.start();
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    startSeekTicks();
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                stopSeekTicks();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                progressBar.setVisibility(View.GONE);
                showError(getString(R.string.error_load_media));
                return true;
            });

            startOnPrepared = true;
            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            progressBar.setVisibility(View.GONE);
            showError(getString(R.string.error_load_media));
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null || !playerPrepared) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            stopSeekTicks();
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            startSeekTicks();
        }
    }

    private void startSeekTicks() { mainHandler.postDelayed(seekTick, SEEK_UPDATE_MS); }
    private void stopSeekTicks()  { mainHandler.removeCallbacks(seekTick); }

    private void tickSeekBar() {
        if (mediaPlayer == null || !playerPrepared) return;
        int pos = mediaPlayer.getCurrentPosition();
        int dur = mediaPlayer.getDuration();
        seekBar.setProgress(pos);
        tvDuration.setText(String.format(Locale.getDefault(),
                "%s / %s", formatMs(pos), formatMs(dur)));
        if (mediaPlayer.isPlaying()) mainHandler.postDelayed(seekTick, SEEK_UPDATE_MS);
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            // Disconnect from surface before release
            try { mediaPlayer.setSurface(null); } catch (IllegalStateException ignored) {}
            try { mediaPlayer.stop(); }          catch (IllegalStateException ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaDataSource != null) {
            try { mediaDataSource.close(); } catch (IOException ignored) {}
            mediaDataSource = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TextureView.SurfaceTextureListener
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        videoSurface = new Surface(surface);
        surfaceReady = true;
        if (isVideoMode && mediaPlayer == null) {
            initPlayer();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Update texture view with new size if needed
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surfaceReady = false;
        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // No action needed
    }

    // ─────────────────────────────────────────────────────────────────────
    // Thumbnail filmstrip
    // ─────────────────────────────────────────────────────────────────────

    private void buildThumbnailStrip() {
        thumbnailContainer.removeAllViews();
        if (fileList == null || fileList.length == 0) return;

        thumbContainers = new FrameLayout[fileList.length];
        int sizePx   = dpToPx(THUMB_SIZE_DP);
        int marginPx = dpToPx(THUMB_MARGIN_DP);

        for (int i = 0; i < fileList.length; i++) {
            final int index = i;
            File file = new File(fileList[i]);

            FrameLayout container = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    sizePx + marginPx * 2, LinearLayout.LayoutParams.MATCH_PARENT);
            container.setLayoutParams(lp);
            container.setOnClickListener(v -> navigateTo(index));

            ImageView thumb = new ImageView(this);
            FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(sizePx, sizePx);
            thumbLp.gravity = Gravity.CENTER;
            thumb.setLayoutParams(thumbLp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF2C2C2C); // dark placeholder while loading
            container.addView(thumb);

            thumbContainers[i] = container;
            thumbnailContainer.addView(container);

            stripLoader.loadThumbnail(file, encrypted, thumb);
        }
    }

    /** Highlights the item at {@code index} with a red border; clears all others. */
    private void updateThumbnailSelection(int index) {
        if (thumbContainers == null) return;
        for (int i = 0; i < thumbContainers.length; i++) {
            if (i == index) {
                GradientDrawable border = new GradientDrawable();
                border.setShape(GradientDrawable.RECTANGLE);
                border.setStroke(dpToPx(2), getColor(R.color.primary_red));
                border.setColor(Color.TRANSPARENT);
                thumbContainers[i].setBackground(border);
            } else {
                thumbContainers[i].setBackground(null);
            }
        }
    }

    /** Smooth-scrolls the filmstrip so the item at {@code index} is centered. */
    private void scrollStripToIndex(int index) {
        int itemWidth = dpToPx(THUMB_SIZE_DP + THUMB_MARGIN_DP * 2);
        thumbnailStrip.post(() -> {
            int target = index * itemWidth - thumbnailStrip.getWidth() / 2 + itemWidth / 2;
            thumbnailStrip.smoothScrollTo(Math.max(0, target), 0);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Immersive system bars
    // ─────────────────────────────────────────────────────────────────────

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String formatMs(int ms) {
        int s = ms / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    private void showFileInfo() {
        if (mediaFile == null) return;

        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(mediaFile)
                : mediaFile.getName();

        StringBuilder info = new StringBuilder();

        // File name
        info.append(getString(R.string.info_filename, originalName)).append("\n\n");

        // File size
        long sizeBytes = mediaFile.length();
        String sizeStr = formatFileSize(sizeBytes);
        info.append(getString(R.string.info_size, sizeStr)).append("\n\n");

        // Dimensions (if image and available)
        if (!isVideoMode) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                if (encrypted) {
                    try (InputStream is = obfuscator.getDecryptedStream(mediaFile)) {
                        BitmapFactory.decodeStream(is, null, opts);
                    }
                } else {
                    BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), opts);
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    info.append(getString(R.string.info_dimensions, opts.outWidth, opts.outHeight)).append("\n\n");
                }
            } catch (IOException ignored) {}
        } else if (playerPrepared && mediaPlayer != null) {
            // Video dimensions
            int w = mediaPlayer.getVideoWidth();
            int h = mediaPlayer.getVideoHeight();
            if (w > 0 && h > 0) {
                info.append(getString(R.string.info_dimensions, w, h)).append("\n\n");
            }
        }

        // Modified date
        long lastModified = mediaFile.lastModified();
        if (lastModified > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            String dateStr = sdf.format(new Date(lastModified));
            info.append(getString(R.string.info_date, dateStr)).append("\n\n");
        }

        // File path
        info.append(getString(R.string.info_path, mediaFile.getAbsolutePath()));

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.info_dialog_title)
                .setMessage(info.toString())
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Encrypt / Decrypt
    // ─────────────────────────────────────────────────────────────────────

    private void updateCryptoButton() {
        btnCrypto.setText(encrypted ? R.string.btn_decrypt_single : R.string.btn_encrypt_single);
        btnCrypto.setEnabled(!cryptoInProgress);
    }

    private void performCrypto() {
        if (cryptoInProgress || mediaFile == null) return;

        cryptoInProgress = true;
        btnCrypto.setEnabled(false);

        // Stop video playback before crypto operation
        if (isVideoMode && mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                stopSeekTicks();
            }
        }
        releasePlayer();

        final File sourceFile = mediaFile;
        final boolean wasEncrypted = encrypted;

        ioExecutor.execute(() -> {
            File newFile = null;
            boolean success = false;
            try {
                if (wasEncrypted) {
                    // Decrypt: .mprot → original
                    String originalName = HeaderObfuscator.getOriginalName(sourceFile);
                    newFile = new File(sourceFile.getParent(), originalName);
                    obfuscator.decrypt(sourceFile, newFile);
                    sourceFile.delete();
                } else {
                    // Encrypt: original → .mprot
                    newFile = HeaderObfuscator.getObfuscatedFile(sourceFile);
                    obfuscator.encrypt(sourceFile, newFile);
                    sourceFile.delete();
                }
                success = true;
            } catch (Exception e) {
                // Crypto failed
            }

            final boolean opSuccess = success;
            final File resultFile = newFile;
            final boolean newEncrypted = !wasEncrypted;

            final String originalPath = sourceFile.getAbsolutePath();

            mainHandler.post(() -> {
                cryptoInProgress = false;

                if (opSuccess && resultFile != null) {
                    // Track the processed file (original path before crypto)
                    processedFiles.add(originalPath);

                    Toast.makeText(this,
                            newEncrypted ? R.string.toast_encrypted : R.string.toast_decrypted,
                            Toast.LENGTH_SHORT).show();

                    // Remove current file from list and navigate
                    removeCurrentAndNavigate();
                } else {
                    btnCrypto.setEnabled(true);
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void rebuildThumbnailStrip() {
        thumbnailContainer.removeAllViews();
        buildThumbnailStrip();
        updateThumbnailSelection(currentIndex);
    }

    /**
     * Removes current file from list and navigates to next, prev, or exits if empty.
     */
    private void removeCurrentAndNavigate() {
        if (fileList == null || fileList.length == 0) {
            finish();
            return;
        }

        if (fileList.length == 1) {
            // Only one file, exit viewer
            finish();
            return;
        }

        // Build new file list without current file
        List<String> newList = new ArrayList<>();
        for (int i = 0; i < fileList.length; i++) {
            if (i != currentIndex) {
                newList.add(fileList[i]);
            }
        }
        fileList = newList.toArray(new String[0]);

        // Adjust index: prefer next file, fall back to previous
        if (currentIndex >= fileList.length) {
            currentIndex = fileList.length - 1;
        }

        // Rebuild strip and load new current file
        rebuildThumbnailStrip();
        loadCurrentMedia();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Album dialogs
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Shows album selection dialog for encrypting the current file.
     */
    private void showEncryptToAlbumDialog() {
        if (mediaFile == null || encrypted) return;

        File protectedRoot = FileConfig.getProtectedFolder();
        List<File> albumDirs = AlbumManager.getAlbumDirs(protectedRoot);

        List<String> options = new java.util.ArrayList<>();
        options.add(getString(R.string.encrypt_to_main));       // "Main Collection"
        for (File dir : albumDirs) options.add(dir.getName());
        options.add(getString(R.string.album_create));          // "New Album"

        new AlertDialog.Builder(this)
                .setTitle(R.string.encrypt_to_album_title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        // Encrypt to main protected folder
                        performCrypto();
                    } else if (which == options.size() - 1) {
                        // Create new album and encrypt to it
                        showCreateAlbumForEncryptDialog();
                    } else {
                        // Encrypt to selected album
                        File targetAlbum = albumDirs.get(which - 1);
                        performEncryptToAlbum(targetAlbum);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows dialog to create a new album and encrypt the current file into it.
     */
    private void showCreateAlbumForEncryptDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.album_name_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 32, 48, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.album_create_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!AlbumManager.isValidName(name)) return;
                    File protectedRoot = FileConfig.getProtectedFolder();
                    if (AlbumManager.albumExists(protectedRoot, name)) {
                        Toast.makeText(this, R.string.album_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newAlbum = AlbumManager.createAlbum(protectedRoot, name);
                    if (newAlbum == null) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.album_created, Toast.LENGTH_SHORT).show();
                    performEncryptToAlbum(newAlbum);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Encrypts the current file to a specific album folder.
     */
    private void performEncryptToAlbum(File targetAlbum) {
        if (cryptoInProgress || mediaFile == null || encrypted) return;

        cryptoInProgress = true;
        btnCrypto.setEnabled(false);

        // Stop video playback
        if (isVideoMode && mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                stopSeekTicks();
            }
        }
        releasePlayer();

        final File sourceFile = mediaFile;

        ioExecutor.execute(() -> {
            File newFile = null;
            boolean success = false;
            try {
                // Create encrypted file in target album
                String encryptedName = sourceFile.getName() + FileConfig.ENCRYPTED_EXTENSION;
                newFile = new File(targetAlbum, encryptedName);
                // Handle duplicates
                if (newFile.exists()) {
                    String base = sourceFile.getName();
                    String ext = FileConfig.ENCRYPTED_EXTENSION;
                    int counter = 1;
                    while (newFile.exists()) {
                        newFile = new File(targetAlbum, base + "(" + counter + ")" + ext);
                        counter++;
                    }
                }

                // Store original path
                OriginalPathStore.storePath(this, newFile.getName(), sourceFile.getAbsolutePath());

                obfuscator.encrypt(sourceFile, newFile);
                sourceFile.delete();
                success = true;
            } catch (Exception e) {
                // Crypto failed
            }

            final boolean opSuccess = success;
            final String originalPath = sourceFile.getAbsolutePath();

            mainHandler.post(() -> {
                cryptoInProgress = false;

                if (opSuccess) {
                    processedFiles.add(originalPath);
                    Toast.makeText(this, R.string.toast_encrypted, Toast.LENGTH_SHORT).show();
                    removeCurrentAndNavigate();
                } else {
                    btnCrypto.setEnabled(true);
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Shows dialog to move the current protected file to another album.
     */
    private void showMoveToAlbumDialog() {
        if (mediaFile == null || !encrypted) return;

        File protectedRoot = FileConfig.getProtectedFolder();
        List<File> albumDirs = AlbumManager.getAlbumDirs(protectedRoot);

        // Determine current album (if any)
        File currentParent = mediaFile.getParentFile();
        boolean inAlbum = currentParent != null && !currentParent.equals(protectedRoot);

        List<String> options = new java.util.ArrayList<>();
        options.add(getString(R.string.album_create));          // "New Album"
        for (File dir : albumDirs) {
            // Skip current album
            if (!dir.equals(currentParent)) {
                options.add(dir.getName());
            }
        }
        if (inAlbum) {
            options.add(getString(R.string.move_to_main));      // "Main Collection"
        }

        // Build parallel list of target directories
        List<File> targetDirs = new java.util.ArrayList<>();
        targetDirs.add(null); // New Album placeholder
        for (File dir : albumDirs) {
            if (!dir.equals(currentParent)) {
                targetDirs.add(dir);
            }
        }
        if (inAlbum) {
            targetDirs.add(protectedRoot); // Main collection
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.album_move_to)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        // Create new album
                        showCreateAlbumForMoveDialog();
                    } else {
                        File targetDir = targetDirs.get(which);
                        performMoveToAlbum(targetDir);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows dialog to create a new album and move the current file into it.
     */
    private void showCreateAlbumForMoveDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.album_name_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 32, 48, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.album_create_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!AlbumManager.isValidName(name)) return;
                    File protectedRoot = FileConfig.getProtectedFolder();
                    if (AlbumManager.albumExists(protectedRoot, name)) {
                        Toast.makeText(this, R.string.album_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newAlbum = AlbumManager.createAlbum(protectedRoot, name);
                    if (newAlbum == null) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.album_created, Toast.LENGTH_SHORT).show();
                    performMoveToAlbum(newAlbum);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Moves the current protected file to the target album directory.
     */
    private void performMoveToAlbum(File targetDir) {
        if (mediaFile == null || !encrypted) return;

        // Stop video playback
        if (isVideoMode && mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                stopSeekTicks();
            }
        }
        releasePlayer();

        final File sourceFile = mediaFile;

        ioExecutor.execute(() -> {
            boolean success = false;
            File destFile = null;
            try {
                targetDir.mkdirs();
                destFile = new File(targetDir, sourceFile.getName());
                // Handle duplicates
                if (destFile.exists()) {
                    String name = sourceFile.getName();
                    String base, ext;
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        base = name.substring(0, dot);
                        ext = name.substring(dot);
                    } else {
                        base = name;
                        ext = "";
                    }
                    int counter = 1;
                    while (destFile.exists()) {
                        destFile = new File(targetDir, base + "(" + counter + ")" + ext);
                        counter++;
                    }
                }
                success = sourceFile.renameTo(destFile);
            } catch (Exception e) {
                success = false;
            }

            final boolean opSuccess = success;
            final File newFile = destFile;
            final String originalPath = sourceFile.getAbsolutePath();

            mainHandler.post(() -> {
                if (opSuccess && newFile != null) {
                    processedFiles.add(originalPath);
                    Toast.makeText(this, R.string.album_moved,
                            Toast.LENGTH_SHORT).show();
                    // Update file list with new path
                    fileList[currentIndex] = newFile.getAbsolutePath();
                    mediaFile = newFile;
                    // Refresh display
                    loadCurrentMedia();
                } else {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Updates the visibility of the Move to Album button based on encryption state.
     */
    private void updateMoveButtonVisibility() {
        if (btnMoveToAlbum != null) {
            btnMoveToAlbum.setVisibility(encrypted ? View.VISIBLE : View.GONE);
        }
    }
}
