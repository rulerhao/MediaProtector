package com.rulerhao.media_protector;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;

import com.rulerhao.media_protector.util.ThemeHelper;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_settings);

        Button btnBack       = findViewById(R.id.btnBack);
        Switch switchDarkMode = findViewById(R.id.switchDarkMode);

        btnBack.setOnClickListener(v -> finish());

        // Set initial state before attaching the listener so the programmatic
        // setChecked() call does not trigger a spurious toggle.
        switchDarkMode.setChecked(ThemeHelper.isDarkMode(this));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != ThemeHelper.isDarkMode(this)) {
                ThemeHelper.toggleTheme(this);
                recreate();
            }
        });
    }
}
