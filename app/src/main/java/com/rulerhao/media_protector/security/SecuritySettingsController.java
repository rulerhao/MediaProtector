package com.rulerhao.media_protector.security;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.shared.Constants;

/**
 * Controller for security settings UI (PIN lock, fingerprint unlock).
 * Extracted from MainActivity to improve separation of concerns.
 */
public class SecuritySettingsController {

    public interface Callback {
        void startPinSetup();
        void startPinChange();
    }

    private final Activity activity;
    private final Callback callback;

    private Switch switchPinLock;
    private Switch switchFingerprint;
    private View fingerprintRow;
    private View fingerprintDivider;
    private View changePinRow;
    private View changePinDivider;

    public SecuritySettingsController(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * Binds the controller to UI views.
     */
    public void bind(Switch switchPinLock, Switch switchFingerprint,
                     View fingerprintRow, View fingerprintDivider,
                     View changePinRow, View changePinDivider) {
        this.switchPinLock = switchPinLock;
        this.switchFingerprint = switchFingerprint;
        this.fingerprintRow = fingerprintRow;
        this.fingerprintDivider = fingerprintDivider;
        this.changePinRow = changePinRow;
        this.changePinDivider = changePinDivider;

        setupListeners();
        refreshUI();
    }

    private void setupListeners() {
        // PIN lock switch
        switchPinLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isPinEnabled(activity)) {
                // Enable PIN - launch setup
                callback.startPinSetup();
            } else if (!isChecked && SecurityHelper.isPinEnabled(activity)) {
                // Disable PIN
                SecurityHelper.clearPin(activity);
                Toast.makeText(activity, R.string.toast_pin_disabled, Toast.LENGTH_SHORT).show();
                refreshUI();
            }
        });

        // Fingerprint switch
        switchFingerprint.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isFingerprintAvailable(activity)) {
                Toast.makeText(activity, R.string.toast_fingerprint_not_available, Toast.LENGTH_SHORT).show();
                switchFingerprint.setChecked(false);
                return;
            }
            SecurityHelper.setFingerprintEnabled(activity, isChecked);
            Toast.makeText(activity,
                    isChecked ? R.string.toast_fingerprint_enabled : R.string.toast_fingerprint_disabled,
                    Toast.LENGTH_SHORT).show();
        });

        // Change PIN row click
        changePinRow.setOnClickListener(v -> callback.startPinChange());
    }

    /**
     * Refreshes the UI to reflect current security settings.
     * Call this after PIN setup/change completes.
     */
    public void refreshUI() {
        boolean pinEnabled = SecurityHelper.isPinEnabled(activity);
        boolean fingerprintAvailable = SecurityHelper.isFingerprintAvailable(activity);

        // Update PIN switch without triggering listener
        switchPinLock.setOnCheckedChangeListener(null);
        switchPinLock.setChecked(pinEnabled);
        switchPinLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isPinEnabled(activity)) {
                callback.startPinSetup();
            } else if (!isChecked && SecurityHelper.isPinEnabled(activity)) {
                SecurityHelper.clearPin(activity);
                Toast.makeText(activity, R.string.toast_pin_disabled, Toast.LENGTH_SHORT).show();
                refreshUI();
            }
        });

        // Show/hide fingerprint option (only when PIN is enabled and fingerprint is available)
        if (pinEnabled && fingerprintAvailable) {
            fingerprintRow.setVisibility(View.VISIBLE);
            fingerprintDivider.setVisibility(View.VISIBLE);
            switchFingerprint.setChecked(SecurityHelper.isFingerprintEnabled(activity));
        } else {
            fingerprintRow.setVisibility(View.GONE);
            fingerprintDivider.setVisibility(View.GONE);
        }

        // Show/hide change PIN option
        if (pinEnabled) {
            changePinRow.setVisibility(View.VISIBLE);
            changePinDivider.setVisibility(View.VISIBLE);
        } else {
            changePinRow.setVisibility(View.GONE);
            changePinDivider.setVisibility(View.GONE);
        }
    }

    /**
     * Handles activity result for PIN setup/change.
     * @return true if the result was handled
     */
    public boolean handleActivityResult(int requestCode, int resultCode) {
        if (requestCode == Constants.REQUEST_PIN_SETUP) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(activity, R.string.toast_pin_enabled, Toast.LENGTH_SHORT).show();
                refreshUI();
            } else {
                // User cancelled - reset switch
                switchPinLock.setChecked(false);
            }
            return true;
        } else if (requestCode == Constants.REQUEST_PIN_CHANGE) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(activity, R.string.toast_pin_enabled, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }
}
