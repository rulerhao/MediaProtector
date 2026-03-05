package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;
import com.rulerhao.media_protector.util.OriginalPathStore;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receives files shared from other apps and protects (encrypts) them.
 */
public class ProtectFileActivity extends Activity {

    private final HeaderObfuscator obfuscator = new HeaderObfuscator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            // Single file shared
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                processUris(List.of(uri));
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            // Multiple files shared
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null && !uris.isEmpty()) {
                processUris(uris);
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            finish();
        }
    }

    private void processUris(List<Uri> uris) {
        int count = uris.size();
        Toast.makeText(this, getString(R.string.toast_protecting_files, count), Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            int success = 0;
            for (Uri uri : uris) {
                if (protectUri(uri)) {
                    success++;
                }
            }
            final int finalSuccess = success;
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.toast_files_protected, finalSuccess), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private boolean protectUri(Uri uri) {
        try {
            // Get the file name from the URI
            String fileName = getFileName(uri);
            if (fileName == null || !FileConfig.isRegularMediaFile(fileName)) {
                return false;
            }

            // Copy content to a temp file
            File tempFile = new File(getCacheDir(), fileName);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) return false;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            // Get protected folder
            File protectedFolder = FileConfig.getProtectedFolder();
            if (!protectedFolder.exists()) {
                protectedFolder.mkdirs();
            }

            // Encrypt to protected folder
            File outFile = new File(protectedFolder, fileName + FileConfig.PROTECTED_EXTENSION);

            // Store original path (if we can determine it)
            String originalPath = uri.getPath();
            if (originalPath != null) {
                OriginalPathStore.storePath(this, outFile.getName(), originalPath);
            }

            obfuscator.encrypt(tempFile, outFile);

            // Clean up temp file
            tempFile.delete();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }

        if (result == null) {
            result = uri.getLastPathSegment();
        }

        return result;
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
