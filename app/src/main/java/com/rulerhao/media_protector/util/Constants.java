package com.rulerhao.media_protector.util;

/**
 * Application-wide constants for request codes, sizes, and timeouts.
 */
public final class Constants {

    private Constants() {} // Prevent instantiation

    // ─────────────────────────────────────────────────────────────────────
    // Activity Request Codes
    // ─────────────────────────────────────────────────────────────────────

    public static final int REQUEST_PERMISSION = 100;
    public static final int REQUEST_PIN_SETUP = 300;
    public static final int REQUEST_PIN_CHANGE = 301;
    public static final int REQUEST_LOCK_SCREEN = 302;
    public static final int REQUEST_SECTION_VIEW = 303;
    public static final int REQUEST_VIEWER = 304;

    // ─────────────────────────────────────────────────────────────────────
    // UI Sizes (dp)
    // ─────────────────────────────────────────────────────────────────────

    public static final int THUMBNAIL_SIZE_DP = 72;
    public static final int THUMBNAIL_MARGIN_DP = 4;
    public static final int FILMSTRIP_THUMB_SIZE_DP = 64;
    public static final int FILMSTRIP_MARGIN_DP = 4;

    // ─────────────────────────────────────────────────────────────────────
    // Gesture Thresholds
    // ─────────────────────────────────────────────────────────────────────

    public static final int SWIPE_MIN_DISTANCE = 80;
    public static final int SWIPE_MAX_OFF_PATH = 200;
    public static final int SWIPE_VELOCITY_THRESHOLD = 100;

    // ─────────────────────────────────────────────────────────────────────
    // Timeouts (ms)
    // ─────────────────────────────────────────────────────────────────────

    public static final int MODE_HIDE_DELAY_MS = 3000;
    public static final int SEEK_UPDATE_INTERVAL_MS = 500;

    // ─────────────────────────────────────────────────────────────────────
    // Cache Sizes
    // ─────────────────────────────────────────────────────────────────────

    public static final int THUMBNAIL_CACHE_SIZE = 50;
    public static final int THUMBNAIL_SAMPLE_SIZE = 4;

    // ─────────────────────────────────────────────────────────────────────
    // File System
    // ─────────────────────────────────────────────────────────────────────

    public static final int MAX_TRAVERSE_DEPTH = 3;
    public static final int CRYPTO_BUFFER_SIZE = 8192;
}
