package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.rulerhao.media_protector.util.SecurityHelper;
import com.rulerhao.media_protector.util.ThemeHelper;

/**
 * Lock screen activity for PIN and fingerprint authentication.
 * Supports three modes: UNLOCK (verify PIN), SETUP (create new PIN), CHANGE (change existing PIN).
 */
public class LockScreenActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_SETUP = 1;
    public static final int MODE_CHANGE = 2;

    public static final int RESULT_AUTHENTICATED = RESULT_OK;
    public static final int RESULT_CANCELLED = RESULT_CANCELED;

    private int mode = MODE_UNLOCK;
    private StringBuilder pinBuilder = new StringBuilder();
    private String firstPin = null; // Used in setup mode for confirmation

    private View[] pinDots;
    private TextView tvSubtitle;
    private TextView tvError;
    private ImageButton btnFingerprint;

    private CancellationSignal fingerprintCancellationSignal;
    private boolean fingerprintListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_lock_screen);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        // Initialize views
        tvSubtitle = findViewById(R.id.tvLockSubtitle);
        tvError = findViewById(R.id.tvError);
        btnFingerprint = findViewById(R.id.btnFingerprint);

        pinDots = new View[]{
                findViewById(R.id.pinDot1),
                findViewById(R.id.pinDot2),
                findViewById(R.id.pinDot3),
                findViewById(R.id.pinDot4)
        };

        // Set subtitle based on mode
        updateSubtitle();

        // Setup number pad buttons
        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };
        for (int id : buttonIds) {
            findViewById(id).setOnClickListener(this::onNumberClick);
        }

        // Backspace button
        findViewById(R.id.btnBackspace).setOnClickListener(v -> onBackspaceClick());

        // Fingerprint button (only shown in unlock mode if enabled)
        if (mode == MODE_UNLOCK
                && SecurityHelper.isFingerprintEnabled(this)
                && SecurityHelper.isFingerprintAvailable(this)) {
            btnFingerprint.setVisibility(View.VISIBLE);
            btnFingerprint.setOnClickListener(v -> startFingerprintAuth());
        } else {
            btnFingerprint.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-start fingerprint listening in unlock mode
        if (mode == MODE_UNLOCK
                && SecurityHelper.isFingerprintEnabled(this)
                && SecurityHelper.isFingerprintAvailable(this)) {
            startFingerprintAuth();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopFingerprintAuth();
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_UNLOCK) {
            // Don't allow back in unlock mode - user must authenticate
            moveTaskToBack(true);
        } else {
            setResult(RESULT_CANCELLED);
            finish();
        }
    }

    private void updateSubtitle() {
        if (mode == MODE_SETUP) {
            if (firstPin == null) {
                tvSubtitle.setText(R.string.lock_subtitle_setup);
            } else {
                tvSubtitle.setText(R.string.lock_subtitle_confirm);
            }
        } else if (mode == MODE_CHANGE) {
            if (firstPin == null) {
                tvSubtitle.setText(R.string.lock_subtitle_setup);
            } else {
                tvSubtitle.setText(R.string.lock_subtitle_confirm);
            }
        } else {
            tvSubtitle.setText(R.string.lock_subtitle_pin);
        }
    }

    private void onNumberClick(View v) {
        if (pinBuilder.length() >= 4) return;

        String digit = ((android.widget.Button) v).getText().toString();
        pinBuilder.append(digit);
        updatePinDots();
        tvError.setVisibility(View.GONE);

        if (pinBuilder.length() == 4) {
            // Small delay before processing
            v.postDelayed(this::processPin, 150);
        }
    }

    private void onBackspaceClick() {
        if (pinBuilder.length() > 0) {
            pinBuilder.deleteCharAt(pinBuilder.length() - 1);
            updatePinDots();
            tvError.setVisibility(View.GONE);
        }
    }

    private void updatePinDots() {
        for (int i = 0; i < pinDots.length; i++) {
            if (i < pinBuilder.length()) {
                pinDots[i].setBackgroundResource(R.drawable.pin_dot_filled);
            } else {
                pinDots[i].setBackgroundResource(R.drawable.pin_dot_empty);
            }
        }
    }

    private void processPin() {
        String pin = pinBuilder.toString();

        switch (mode) {
            case MODE_UNLOCK:
                if (SecurityHelper.verifyPin(this, pin)) {
                    onAuthSuccess();
                } else {
                    onAuthFailed();
                }
                break;

            case MODE_SETUP:
            case MODE_CHANGE:
                if (firstPin == null) {
                    // First entry - store and ask for confirmation
                    firstPin = pin;
                    pinBuilder.setLength(0);
                    updatePinDots();
                    updateSubtitle();
                } else {
                    // Confirmation entry
                    if (pin.equals(firstPin)) {
                        SecurityHelper.setPin(this, pin);
                        onAuthSuccess();
                    } else {
                        // Mismatch - reset
                        firstPin = null;
                        pinBuilder.setLength(0);
                        updatePinDots();
                        updateSubtitle();
                        showError(R.string.lock_error_mismatch);
                    }
                }
                break;
        }
    }

    private void onAuthSuccess() {
        setResult(RESULT_AUTHENTICATED);
        finish();
    }

    private void onAuthFailed() {
        pinBuilder.setLength(0);
        updatePinDots();
        showError(R.string.lock_error_wrong);

        // Shake animation on dots
        View container = findViewById(R.id.pinDotsContainer);
        container.animate()
                .translationX(10)
                .setDuration(50)
                .withEndAction(() -> container.animate()
                        .translationX(-10)
                        .setDuration(50)
                        .withEndAction(() -> container.animate()
                                .translationX(0)
                                .setDuration(50)
                                .start())
                        .start())
                .start();
    }

    private void showError(int resId) {
        tvError.setText(resId);
        tvError.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void startFingerprintAuth() {
        if (fingerprintListening) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        try {
            FingerprintManager fingerprintManager =
                    (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fingerprintManager == null || !fingerprintManager.isHardwareDetected()) {
                return;
            }

            fingerprintCancellationSignal = new CancellationSignal();
            fingerprintListening = true;

            fingerprintManager.authenticate(
                    null, // CryptoObject
                    fingerprintCancellationSignal,
                    0, // flags
                    new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                FingerprintManager.AuthenticationResult result) {
                            fingerprintListening = false;
                            onAuthSuccess();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            // Don't show error, user can retry
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            fingerprintListening = false;
                            // Silently handle errors - user can still use PIN
                        }
                    },
                    null // Handler
            );
        } catch (Exception e) {
            fingerprintListening = false;
        }
    }

    private void stopFingerprintAuth() {
        if (fingerprintCancellationSignal != null && !fingerprintCancellationSignal.isCanceled()) {
            fingerprintCancellationSignal.cancel();
        }
        fingerprintListening = false;
    }
}
