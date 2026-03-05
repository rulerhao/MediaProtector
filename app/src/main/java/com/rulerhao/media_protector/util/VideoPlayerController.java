package com.rulerhao.media_protector.util;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * Controller for video playback with MediaPlayer.
 * Handles setup, play/pause, seek, and progress updates.
 */
public class VideoPlayerController implements SurfaceHolder.Callback {

    public interface Listener {
        void onPrepared(int duration);
        void onError(String message);
        void onCompletion();
    }

    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private SeekBar seekBar;
    private TextView durationText;
    private Listener listener;
    private File currentFile;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPrepared = false;
    private boolean surfaceReady = false;
    private boolean pendingSetup = false;

    private final Runnable seekUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                int pos = mediaPlayer.getCurrentPosition();
                int dur = mediaPlayer.getDuration();
                if (seekBar != null) {
                    seekBar.setProgress(pos);
                }
                if (durationText != null) {
                    durationText.setText(formatDuration(pos) + " / " + formatDuration(dur));
                }
                handler.postDelayed(this, Constants.SEEK_UPDATE_INTERVAL_MS);
            }
        }
    };

    public VideoPlayerController() {}

    /**
     * Binds the controller to UI components.
     */
    public void bind(SurfaceView surfaceView, SeekBar seekBar, TextView durationText, Listener listener) {
        this.surfaceView = surfaceView;
        this.seekBar = seekBar;
        this.durationText = durationText;
        this.listener = listener;

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null && isPrepared) {
                        mediaPlayer.seekTo(progress);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar bar) {}
                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            });
        }
    }

    /**
     * Sets up video playback for the given file.
     */
    public void setupVideo(File file) {
        release();
        currentFile = file;

        if (!surfaceReady) {
            pendingSetup = true;
            return;
        }

        setupMediaPlayer();
    }

    private void setupMediaPlayer() {
        if (currentFile == null) return;

        try {
            mediaPlayer = new MediaPlayer();

            if (FileStreamFactory.isEncrypted(currentFile)) {
                EncryptedMediaDataSource dataSource = new EncryptedMediaDataSource(currentFile);
                mediaPlayer.setDataSource(dataSource);
            } else {
                mediaPlayer.setDataSource(currentFile.getAbsolutePath());
            }

            mediaPlayer.setDisplay(surfaceHolder);

            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                int duration = mp.getDuration();
                if (seekBar != null) {
                    seekBar.setMax(duration);
                }
                if (durationText != null) {
                    durationText.setText("00:00 / " + formatDuration(duration));
                }
                if (listener != null) {
                    listener.onPrepared(duration);
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                handler.removeCallbacks(seekUpdater);
                if (listener != null) {
                    listener.onCompletion();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (listener != null) {
                    listener.onError("Playback error: " + what);
                }
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    /**
     * Toggles play/pause state.
     * @return true if now playing, false if paused
     */
    public boolean togglePlayPause() {
        if (mediaPlayer == null || !isPrepared) return false;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            handler.removeCallbacks(seekUpdater);
            return false;
        } else {
            mediaPlayer.start();
            handler.post(seekUpdater);
            return true;
        }
    }

    /**
     * @return true if video is currently playing
     */
    public boolean isPlaying() {
        return mediaPlayer != null && isPrepared && mediaPlayer.isPlaying();
    }

    /**
     * @return true if player is prepared and ready
     */
    public boolean isPrepared() {
        return isPrepared;
    }

    /**
     * Releases all resources.
     */
    public void release() {
        handler.removeCallbacks(seekUpdater);
        isPrepared = false;

        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(null);
            } catch (IllegalStateException ignored) {}
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    /**
     * Formats milliseconds to MM:SS or HH:MM:SS.
     */
    public static String formatDuration(int millis) {
        int totalSec = millis / 1000;
        int hours = totalSec / 3600;
        int mins = (totalSec % 3600) / 60;
        int secs = totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs);
        } else {
            return String.format(Locale.US, "%02d:%02d", mins, secs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SurfaceHolder.Callback
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (pendingSetup) {
            pendingSetup = false;
            setupMediaPlayer();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        release();
    }
}
