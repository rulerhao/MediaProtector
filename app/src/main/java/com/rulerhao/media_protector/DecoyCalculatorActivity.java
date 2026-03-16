package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.rulerhao.media_protector.util.SecurityHelper;
import com.rulerhao.media_protector.util.ThemeHelper;

/**
 * A functional calculator that serves as a decoy/disguise for the app.
 * Entering the secret code (default: pressing "=" after typing the code)
 * will reveal the real app.
 */
public class DecoyCalculatorActivity extends Activity {

    private TextView tvDisplay;
    private StringBuilder currentInput = new StringBuilder();
    private StringBuilder secretBuffer = new StringBuilder();
    private double firstOperand = 0;
    private String pendingOperator = null;
    private boolean newInput = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_decoy_calculator);

        tvDisplay = findViewById(R.id.tvCalcDisplay);
        tvDisplay.setText("0");

        // Number buttons
        int[] numberIds = {
            R.id.btnCalc0, R.id.btnCalc1, R.id.btnCalc2, R.id.btnCalc3, R.id.btnCalc4,
            R.id.btnCalc5, R.id.btnCalc6, R.id.btnCalc7, R.id.btnCalc8, R.id.btnCalc9
        };
        for (int i = 0; i < numberIds.length; i++) {
            final int digit = i;
            findViewById(numberIds[i]).setOnClickListener(v -> onDigitPress(digit));
        }

        // Operator buttons
        findViewById(R.id.btnCalcAdd).setOnClickListener(v -> onOperatorPress("+"));
        findViewById(R.id.btnCalcSubtract).setOnClickListener(v -> onOperatorPress("-"));
        findViewById(R.id.btnCalcMultiply).setOnClickListener(v -> onOperatorPress("×"));
        findViewById(R.id.btnCalcDivide).setOnClickListener(v -> onOperatorPress("÷"));

        // Equals - also checks for secret code
        findViewById(R.id.btnCalcEquals).setOnClickListener(v -> onEqualsPress());

        // Clear
        findViewById(R.id.btnCalcClear).setOnClickListener(v -> onClearPress());

        // Decimal
        findViewById(R.id.btnCalcDecimal).setOnClickListener(v -> onDecimalPress());

        // Plus/Minus toggle
        findViewById(R.id.btnCalcPlusMinus).setOnClickListener(v -> onPlusMinusPress());

        // Percent
        findViewById(R.id.btnCalcPercent).setOnClickListener(v -> onPercentPress());
    }

    private void onDigitPress(int digit) {
        // Track digits for secret code detection
        secretBuffer.append(digit);
        if (secretBuffer.length() > 10) {
            secretBuffer.deleteCharAt(0);
        }

        if (newInput) {
            currentInput.setLength(0);
            newInput = false;
        }

        // Prevent leading zeros (except for "0.")
        if (currentInput.length() == 1 && currentInput.charAt(0) == '0'
                && !currentInput.toString().contains(".")) {
            currentInput.setLength(0);
        }

        currentInput.append(digit);
        updateDisplay();
    }

    private void onOperatorPress(String operator) {
        if (pendingOperator != null && !newInput) {
            calculate();
        } else if (currentInput.length() > 0) {
            firstOperand = parseDisplay();
        }
        pendingOperator = operator;
        newInput = true;
        // Clear secret buffer on operator (secret code should be continuous digits)
        secretBuffer.setLength(0);
    }

    private void onEqualsPress() {
        // Check if the input ends with the PIN code
        if (SecurityHelper.isPinEnabled(this)) {
            String buffer = secretBuffer.toString();
            for (int len = 1; len <= buffer.length(); len++) {
                String suffix = buffer.substring(buffer.length() - len);
                if (SecurityHelper.verifyPin(this, suffix)) {
                    launchRealApp();
                    return;
                }
            }
        }

        if (pendingOperator != null) {
            calculate();
            pendingOperator = null;
        }
        newInput = true;
        secretBuffer.setLength(0);
    }

    private void calculate() {
        if (pendingOperator == null) return;

        double secondOperand = parseDisplay();
        double result = 0;

        switch (pendingOperator) {
            case "+":
                result = firstOperand + secondOperand;
                break;
            case "-":
                result = firstOperand - secondOperand;
                break;
            case "×":
                result = firstOperand * secondOperand;
                break;
            case "÷":
                if (secondOperand != 0) {
                    result = firstOperand / secondOperand;
                } else {
                    tvDisplay.setText("Error");
                    currentInput.setLength(0);
                    newInput = true;
                    return;
                }
                break;
        }

        firstOperand = result;
        currentInput.setLength(0);

        // Format result nicely
        if (result == (long) result) {
            currentInput.append((long) result);
        } else {
            currentInput.append(result);
        }
        updateDisplay();
    }

    private void onClearPress() {
        currentInput.setLength(0);
        firstOperand = 0;
        pendingOperator = null;
        newInput = true;
        tvDisplay.setText("0");
        secretBuffer.setLength(0);
    }

    private void onDecimalPress() {
        if (newInput) {
            currentInput.setLength(0);
            currentInput.append("0");
            newInput = false;
        }
        if (!currentInput.toString().contains(".")) {
            currentInput.append(".");
            updateDisplay();
        }
    }

    private void onPlusMinusPress() {
        if (currentInput.length() == 0) return;

        if (currentInput.charAt(0) == '-') {
            currentInput.deleteCharAt(0);
        } else {
            currentInput.insert(0, '-');
        }
        updateDisplay();
    }

    private void onPercentPress() {
        if (currentInput.length() == 0) return;

        double value = parseDisplay() / 100.0;
        currentInput.setLength(0);
        if (value == (long) value) {
            currentInput.append((long) value);
        } else {
            currentInput.append(value);
        }
        updateDisplay();
    }

    private double parseDisplay() {
        if (currentInput.length() == 0) return 0;
        try {
            return Double.parseDouble(currentInput.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateDisplay() {
        if (currentInput.length() == 0) {
            tvDisplay.setText("0");
        } else {
            tvDisplay.setText(currentInput.toString());
        }
    }

    private void launchRealApp() {
        // Clear the decoy state
        onClearPress();

        // Launch MainActivity directly using explicit component
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.rulerhao.media_protector.MainActivity");
            intent.putExtra("from_decoy", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            // Fallback: try using class reference
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("from_decoy", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        // Normal back behavior - minimize the app
        moveTaskToBack(true);
    }
}
