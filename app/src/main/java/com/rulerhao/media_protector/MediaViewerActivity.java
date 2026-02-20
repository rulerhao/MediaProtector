package com.rulerhao.media_protector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * Full-screen viewer for both images and videos, supporting encrypted (.mprot) files.
 *
 * <p>Pass {@link #EXTRA_FILE_PATH} (the absolute path) and {@link #EXTRA_ENCRYPTED}
 * (true for .mprot files) via the starting Intent.
 *
 * <ul>
 *   <li>Images are decoded on a background thread via {@link HeaderObfuscator#getDecryptedStream}
 *       (encrypted) or {@link BitmapFactory#decodeFile} (plain).</li>
 *   <li>Videos use {@link MediaPlayer} on a {@link SurfaceView}; encrypted files are fed via
 *       {@link EncryptedMediaDataSource} (no temp file written to disk).</li>
 * </ul>
 */
public class MediaViewerActivity extends Activity implements SurfaceHolder.Callback {

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_ENCRYPTED = "encrypted";

    private static final int SEEK_UPDATE_MS = 500;

    private ImageView    imageView;
    private SurfaceView  surfaceView;
    private LinearLayout videoControls;
    private ImageButton  btnPlayPause;
    private SeekBar      seekBar;
    private TextView     tvDuration;
    private ProgressBar  progressBar;
    private TextView     tvError;

    private MediaPlayer              mediaPlayer;
    private EncryptedMediaDataSource mediaDataSource;
    private boolean surfaceReady   = false;
    private boolean playerPrepared = false;
    private boolean startOnPrepared = false;

    private final Handler         mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor  = Executors.newSingleThreadExecutor();
    private final Runnable        seekTick    = this::tickSeekBar;

    private File    mediaFile;
    private boolean encrypted;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_media_viewer);

        imageView     = findViewById(R.id.imageView);
        surfaceView   = findViewById(R.id.surfaceView);
        videoControls = findViewById(R.id.videoControls);
        btnPlayPause  = findViewById(R.id.btnPlayPause);
        seekBar       = findViewById(R.id.seekBar);
        tvDuration    = findViewById(R.id.tvDuration);
        progressBar   = findViewById(R.id.progressBar);
        tvError       = findViewById(R.id.tvError);

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        encrypted   = getIntent().getBooleanExtra(EXTRA_ENCRYPTED, false);

        if (path == null) {
            showError(getString(R.string.error_generic));
            return;
        }

        mediaFile = new File(path);

        // Determine original file type (strip .mprot extension if needed)
        String originalName = encrypted
                ? HeaderObfuscator.getOriginalName(mediaFile)
                : mediaFile.getName();

        // Top bar: close button + filename
        Button btnBack = findViewById(R.id.btnBack);
        TextView tvFilename = findViewById(R.id.tvFilename);
        btnBack.setOnClickListener(v -> finish());
        tvFilename.setText(originalName);

        if (FileConfig.isVideoFile(originalName)) {
            setupVideo();
        } else {
            loadImage();
        }
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
        stopSeekTicks();
        releasePlayer();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Image
    // -------------------------------------------------------------------------

    private void loadImage() {
        imageView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        ioExecutor.execute(() -> {
            Bitmap bmp = decodeImage();
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                } else {
                    showError(getString(R.string.error_load_media));
                }
            });
        });
    }

    private Bitmap decodeImage() {
        try {
            if (encrypted) {
                HeaderObfuscator obfuscator = new HeaderObfuscator();
                try (InputStream is = obfuscator.getDecryptedStream(mediaFile)) {
                    return BitmapFactory.decodeStream(is);
                }
            } else {
                return BitmapFactory.decodeFile(mediaFile.getAbsolutePath());
            }
        } catch (IOException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Video
    // -------------------------------------------------------------------------

    private void setupVideo() {
        surfaceView.setVisibility(View.VISIBLE);
        videoControls.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        surfaceView.getHolder().addCallback(this);

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && playerPrepared) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { stopSeekTicks(); }
            @Override public void onStopTrackingTouch(SeekBar bar)  { startSeekTicks(); }
        });
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

            if (surfaceReady) {
                mediaPlayer.setDisplay(surfaceView.getHolder());
            }

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

    private void startSeekTicks() {
        mainHandler.postDelayed(seekTick, SEEK_UPDATE_MS);
    }

    private void stopSeekTicks() {
        mainHandler.removeCallbacks(seekTick);
    }

    private void tickSeekBar() {
        if (mediaPlayer == null || !playerPrepared) return;
        int pos = mediaPlayer.getCurrentPosition();
        int dur = mediaPlayer.getDuration();
        seekBar.setProgress(pos);
        tvDuration.setText(String.format(Locale.getDefault(),
                "%s / %s", formatMs(pos), formatMs(dur)));
        if (mediaPlayer.isPlaying()) {
            mainHandler.postDelayed(seekTick, SEEK_UPDATE_MS);
        }
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

    // -------------------------------------------------------------------------
    // SurfaceHolder.Callback
    // -------------------------------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (mediaPlayer == null) {
            initPlayer();
        } else {
            mediaPlayer.setDisplay(holder);
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int fmt, int w, int ht) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (mediaPlayer != null) mediaPlayer.setDisplay(null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private static String formatMs(int ms) {
        int s = ms / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }
}
