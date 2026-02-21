package com.rulerhao.media_protector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;
import com.rulerhao.media_protector.util.EncryptedMediaDataSource;
import com.rulerhao.media_protector.util.ThemeHelper;
import com.rulerhao.media_protector.util.ThumbnailLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
public class MediaViewerActivity extends Activity implements SurfaceHolder.Callback {

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
    private ImageView        imageView;
    private SurfaceView      surfaceView;
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
        surfaceView        = findViewById(R.id.surfaceView);
        bottomArea         = findViewById(R.id.bottomArea);
        videoControls      = findViewById(R.id.videoControls);
        btnPlayPause       = findViewById(R.id.btnPlayPause);
        seekBar            = findViewById(R.id.seekBar);
        tvDuration         = findViewById(R.id.tvDuration);
        thumbnailStrip     = findViewById(R.id.thumbnailStrip);
        thumbnailContainer = findViewById(R.id.thumbnailContainer);
        progressBar        = findViewById(R.id.progressBar);
        tvError            = findViewById(R.id.tvError);

        surfaceView.getHolder().addCallback(this);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

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

        stripLoader = new ThumbnailLoader();
        buildThumbnailStrip();

        // Gesture: single tap on media area → toggle mode; horizontal fling → navigate.
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // Ignore taps that land on the top bar or bottom area so those
                // views can handle their own click events normally.
                if (viewerTopBar.getVisibility() == View.VISIBLE
                        && e.getY() <= viewerTopBar.getBottom()) return false;
                if (bottomArea.getVisibility() == View.VISIBLE
                        && e.getY() >= bottomArea.getTop()) return false;
                toggleMode();
                return false; // don't consume — let normal dispatch continue
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null) return false;
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
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        stopSeekTicks();
        releasePlayer();
        stripLoader.destroy();
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

        // Update filmstrip selection and scroll it into view.
        updateThumbnailSelection(currentIndex);
        scrollStripToIndex(currentIndex);

        isVideoMode = FileConfig.isVideoFile(originalName);
        if (isVideoMode) {
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            setupVideo();
        } else {
            surfaceView.setVisibility(View.GONE);
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
        progressBar.setVisibility(View.VISIBLE);
        final File target = mediaFile;
        ioExecutor.execute(() -> {
            Bitmap bmp = decodeImage(target);
            mainHandler.post(() -> {
                if (!target.equals(mediaFile)) return; // stale load; user navigated away
                progressBar.setVisibility(View.GONE);
                if (bmp != null) imageView.setImageBitmap(bmp);
                else             showError(getString(R.string.error_load_media));
            });
        });
    }

    private Bitmap decodeImage(File file) {
        try {
            if (encrypted) {
                HeaderObfuscator obfuscator = new HeaderObfuscator();
                try (InputStream is = obfuscator.getDecryptedStream(file)) {
                    return BitmapFactory.decodeStream(is);
                }
            } else {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (IOException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Video playback
    // ─────────────────────────────────────────────────────────────────────

    private void setupVideo() {
        videoControls.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        if (surfaceView.getVisibility() == View.VISIBLE) {
            // Video → video: force surface destruction so surfaceCreated() fires
            // fresh for the new player (avoids BufferQueue "already connected").
            surfaceView.setVisibility(View.GONE);
            surfaceReady = false;
        } else {
            // Image → video (or first load): surface already gone; show to trigger
            // surfaceCreated() → initPlayer().
            surfaceView.setVisibility(View.VISIBLE);
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
            mediaPlayer.setDisplay(surfaceView.getHolder());

            mediaPlayer.setOnPreparedListener(mp -> {
                playerPrepared = true;
                progressBar.setVisibility(View.GONE);
                seekBar.setMax(mp.getDuration());
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
            // Disconnect from surface before release to clear the BufferQueue producer
            // connection; without this the next player's prepareAsync() throws.
            try { mediaPlayer.setDisplay(null); } catch (IllegalStateException ignored) {}
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
    // SurfaceHolder.Callback
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (isVideoMode) {
            if (mediaPlayer == null) initPlayer();
            else                     mediaPlayer.setDisplay(holder);
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int fmt, int w, int ht) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (mediaPlayer != null) mediaPlayer.setDisplay(null);
        // If a new video is waiting (player already released), recreate the surface
        // so surfaceCreated() → initPlayer() can connect a fresh player.
        if (isVideoMode && mediaPlayer == null) {
            mainHandler.post(() -> {
                if (isVideoMode && mediaPlayer == null)
                    surfaceView.setVisibility(View.VISIBLE);
            });
        }
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
}
