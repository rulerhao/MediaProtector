package com.rulerhao.media_protector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen viewer for images and videos with swipe-to-navigate and
 * auto-hiding overlays (top bar + video controls hide after 3 s of inactivity;
 * any touch reveals them again).
 *
 * <p>Pass {@link #EXTRA_FILE_LIST} (String[]) and {@link #EXTRA_FILE_INDEX} (int)
 * to enable left/right swipe navigation.  Legacy single-file mode still works
 * via {@link #EXTRA_FILE_PATH}.
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

    private static final int  SEEK_UPDATE_MS          = 500;
    private static final long OVERLAY_HIDE_DELAY_MS   = 3_000;
    private static final int  SWIPE_MIN_DISTANCE      = 80;   // px
    private static final int  SWIPE_MIN_VELOCITY      = 100;  // px/s

    // ── Views ─────────────────────────────────────────────────────────────
    private View         viewerTopBar;
    private TextView     tvFilename;
    private ImageView    imageView;
    private SurfaceView  surfaceView;
    private LinearLayout videoControls;
    private ImageButton  btnPlayPause;
    private SeekBar      seekBar;
    private TextView     tvDuration;
    private ProgressBar  progressBar;
    private TextView     tvError;

    // ── Playback ──────────────────────────────────────────────────────────
    private MediaPlayer              mediaPlayer;
    private EncryptedMediaDataSource mediaDataSource;
    private boolean surfaceReady    = false;
    private boolean playerPrepared  = false;
    private boolean startOnPrepared = false;
    private boolean isVideoMode     = false;

    // ── Navigation ────────────────────────────────────────────────────────
    private String[] fileList;
    private int      currentIndex;
    private boolean  encrypted;
    private File     mediaFile;

    // ── Overlay auto-hide ─────────────────────────────────────────────────
    private boolean overlaysVisible = true;
    private final Handler  mainHandler  = new Handler(Looper.getMainLooper());
    private final Runnable seekTick     = this::tickSeekBar;
    private final Runnable hideRunnable = this::hideOverlays;

    // ── Misc ──────────────────────────────────────────────────────────────
    private GestureDetector       gestureDetector;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_media_viewer);

        viewerTopBar  = findViewById(R.id.viewerTopBar);
        tvFilename    = findViewById(R.id.tvFilename);
        imageView     = findViewById(R.id.imageView);
        surfaceView   = findViewById(R.id.surfaceView);
        videoControls = findViewById(R.id.videoControls);
        btnPlayPause  = findViewById(R.id.btnPlayPause);
        seekBar       = findViewById(R.id.seekBar);
        tvDuration    = findViewById(R.id.tvDuration);
        progressBar   = findViewById(R.id.progressBar);
        tvError       = findViewById(R.id.tvError);

        // Register surface callback once for the entire activity lifetime.
        surfaceView.getHolder().addCallback(this);

        // Close button
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Video control listeners are set once; safe regardless of how many videos we navigate.
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && playerPrepared && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { stopSeekTicks(); }
            @Override public void onStopTrackingTouch(SeekBar bar)  { startSeekTicks(); }
        });

        // Read intent extras — support both multi-file and legacy single-file mode.
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

        // Gesture: any tap → show overlays; horizontal fling → navigate prev/next.
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                showOverlays();
                return false; // don't consume — let child views keep handling clicks
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null) return false;
                // Don't intercept flings that start inside visible video controls.
                if (isVideoMode && videoControls.getVisibility() == View.VISIBLE
                        && e1.getY() >= videoControls.getTop()) {
                    return false;
                }
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
        // Feed every touch to the gesture detector before letting child views handle it.
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
        ioExecutor.shutdownNow();
        super.onDestroy();
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
        mainHandler.removeCallbacks(hideRunnable);

        // Reset shared UI state.
        playerPrepared  = false;
        startOnPrepared = false;
        progressBar.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        seekBar.setProgress(0);
        tvDuration.setText("0:00 / 0:00");

        // Resolve file and detect type.
        mediaFile = new File(fileList[currentIndex]);
        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(mediaFile)
                : mediaFile.getName();
        tvFilename.setText(originalName);

        isVideoMode = FileConfig.isVideoFile(originalName);
        if (isVideoMode) {
            imageView.setImageBitmap(null);
            imageView.setVisibility(View.GONE);
            setupVideo();
        } else {
            // Navigating away from video: tear down the surface.
            surfaceView.setVisibility(View.GONE);
            videoControls.setVisibility(View.GONE);
            surfaceReady = false;
            loadImage();
        }

        showOverlays();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Image loading
    // ─────────────────────────────────────────────────────────────────────

    private void loadImage() {
        imageView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        // Capture the target so the callback can detect a stale load after navigation.
        final File target = mediaFile;
        ioExecutor.execute(() -> {
            Bitmap bmp = decodeImage(target);
            mainHandler.post(() -> {
                if (!target.equals(mediaFile)) return; // user navigated away; discard
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
        surfaceView.setVisibility(View.VISIBLE);
        videoControls.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        // If the surface is already alive (video → video navigation), start immediately.
        // Otherwise surfaceCreated() will fire and call initPlayer().
        if (surfaceReady) initPlayer();
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
            try { mediaPlayer.stop(); } catch (IllegalStateException ignored) {}
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
    }

    // ─────────────────────────────────────────────────────────────────────
    // Overlay auto-hide
    // ─────────────────────────────────────────────────────────────────────

    private void showOverlays() {
        mainHandler.removeCallbacks(hideRunnable);
        overlaysVisible = true;

        viewerTopBar.animate().cancel();
        viewerTopBar.setAlpha(1f);
        viewerTopBar.setVisibility(View.VISIBLE);

        if (isVideoMode) {
            videoControls.animate().cancel();
            videoControls.setAlpha(1f);
            videoControls.setVisibility(View.VISIBLE);
        }

        mainHandler.postDelayed(hideRunnable, OVERLAY_HIDE_DELAY_MS);
    }

    private void hideOverlays() {
        overlaysVisible = false;
        viewerTopBar.animate()
                .alpha(0f).setDuration(300)
                .withEndAction(() -> { if (!overlaysVisible) viewerTopBar.setVisibility(View.GONE); })
                .start();
        if (isVideoMode && videoControls.getVisibility() == View.VISIBLE) {
            videoControls.animate()
                    .alpha(0f).setDuration(300)
                    .withEndAction(() -> { if (!overlaysVisible) videoControls.setVisibility(View.GONE); })
                    .start();
        }
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

    private static String formatMs(int ms) {
        int s = ms / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }
}
