package com.rulerhao.media_protector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import com.rulerhao.media_protector.util.SecurityHelper;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DecoyWeatherActivity extends Activity {

    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] CONDITIONS = {
        "Sunny", "Partly Cloudy", "Cloudy", "Sunny", "Light Rain", "Partly Cloudy", "Sunny"
    };

    private TextView tvCity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_decoy_weather);

        tvCity = findViewById(R.id.tvWeatherCity);
        setupWeatherUI();
        findViewById(R.id.btnWeatherLocation).setOnClickListener(v -> showLocationDialog());
    }

    private void setupWeatherUI() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH); // 0–11

        // Seasonal base temperature (Northern Hemisphere)
        int baseTemp;
        if (month >= 11 || month <= 1) baseTemp = 8;    // Winter
        else if (month <= 4)           baseTemp = 18;   // Spring
        else if (month <= 7)           baseTemp = 28;   // Summer
        else                           baseTemp = 16;   // Fall

        // Current date string
        String dateStr = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date());
        ((TextView) findViewById(R.id.tvWeatherDate)).setText(dateStr);

        // Current temp & condition
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sun
        ((TextView) findViewById(R.id.tvWeatherTemp)).setText(baseTemp + "°");
        ((TextView) findViewById(R.id.tvWeatherCondition)).setText(CONDITIONS[dayOfWeek]);
        ((TextView) findViewById(R.id.tvWeatherHigh)).setText("H:" + (baseTemp + 4) + "°");
        ((TextView) findViewById(R.id.tvWeatherLow)).setText("L:" + (baseTemp - 4) + "°");

        // 5-day forecast
        int[] dayIds  = {R.id.tvForecastDay0,  R.id.tvForecastDay1,  R.id.tvForecastDay2,  R.id.tvForecastDay3,  R.id.tvForecastDay4};
        int[] condIds = {R.id.tvForecastCond0, R.id.tvForecastCond1, R.id.tvForecastCond2, R.id.tvForecastCond3, R.id.tvForecastCond4};
        int[] lowIds  = {R.id.tvForecastLow0,  R.id.tvForecastLow1,  R.id.tvForecastLow2,  R.id.tvForecastLow3,  R.id.tvForecastLow4};
        int[] highIds = {R.id.tvForecastHigh0, R.id.tvForecastHigh1, R.id.tvForecastHigh2, R.id.tvForecastHigh3, R.id.tvForecastHigh4};

        Calendar forecastCal = Calendar.getInstance();
        for (int i = 0; i < 5; i++) {
            forecastCal.add(Calendar.DAY_OF_YEAR, 1);
            int dow = forecastCal.get(Calendar.DAY_OF_WEEK) - 1;
            int variation = (i % 3) - 1; // -1, 0, +1 repeating
            ((TextView) findViewById(dayIds[i])).setText(DAY_NAMES[dow]);
            ((TextView) findViewById(condIds[i])).setText(CONDITIONS[(dow + i) % CONDITIONS.length]);
            ((TextView) findViewById(lowIds[i])).setText((baseTemp - 3 + variation) + "°");
            ((TextView) findViewById(highIds[i])).setText((baseTemp + 4 + variation) + "°");
        }
    }

    private void showLocationDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter city name...");

        new AlertDialog.Builder(this)
                .setTitle("Change Location")
                .setView(input)
                .setPositiveButton("Set", (d, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) return;
                    if (checkPin(query)) {
                        launchRealApp();
                    } else {
                        tvCity.setText(query);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean checkPin(String input) {
        if (!SecurityHelper.isPinEnabled(this)) return true;
        for (int len = 1; len <= input.length(); len++) {
            String suffix = input.substring(input.length() - len);
            if (SecurityHelper.verifyPin(this, suffix)) return true;
        }
        return false;
    }

    private void launchRealApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("from_decoy", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
