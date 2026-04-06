package com.rulerhao.media_protector.album;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.core.FileConfig;
import com.rulerhao.media_protector.media.MediaAdapter;
import com.rulerhao.media_protector.shared.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Previews the contents of an album before user confirms selection.
 * Shows a grid of existing files in the target album, similar to the Protected tab.
 *
 * <h3>Input extras</h3>
 * <ul>
 *   <li>{@link #EXTRA_ALBUM_PATH} — absolute path of album to preview (null for main collection)</li>
 *   <li>{@link #EXTRA_ALBUM_NAME} — display name for the album</li>
 * </ul>
 *
 * <h3>Output (on RESULT_OK)</h3>
 * Returns the same album path that was passed in, confirming user selection.
 */
public class AlbumPreviewActivity extends Activity {

    public static final String EXTRA_ALBUM_PATH = "album_path";
    public static final String EXTRA_ALBUM_NAME = "album_name";

    private String albumPath;
    private boolean appliedDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_preview);
        appliedDark = ThemeHelper.isDarkMode(this);

        albumPath = getIntent().getStringExtra(EXTRA_ALBUM_PATH);
        String albumName = getIntent().getStringExtra(EXTRA_ALBUM_NAME);

        // Toolbar
        findViewById(R.id.btnPreviewBack).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tvPreviewTitle);
        tvTitle.setText(albumName != null ? albumName : getString(R.string.encrypt_to_main));

        findViewById(R.id.btnPreviewConfirm).setOnClickListener(v -> confirmSelection());

        // Load and display files
        loadAlbumFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeHelper.isDarkMode(this) != appliedDark) {
            recreate();
        }
    }

    private void loadAlbumFiles() {
        File targetDir;
        if (albumPath != null) {
            targetDir = new File(albumPath);
        } else {
            targetDir = FileConfig.getProtectedFolder();
        }

        List<File> files = new ArrayList<>();
        if (targetDir.exists() && targetDir.isDirectory()) {
            File[] children = targetDir.listFiles();
            if (children != null) {
                for (File f : children) {
                    if (f.isFile() && FileConfig.isEncryptedFile(f.getName())) {
                        files.add(f);
                    }
                }
            }
        }

        TextView tvInfo = findViewById(R.id.tvPreviewInfo);
        GridView grid = findViewById(R.id.previewGrid);
        View emptyState = findViewById(R.id.emptyState);

        if (files.isEmpty()) {
            tvInfo.setVisibility(View.GONE);
            grid.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            tvInfo.setVisibility(View.VISIBLE);
            tvInfo.setText(getString(R.string.album_preview_info, files.size()));
            grid.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);

            MediaAdapter adapter = new MediaAdapter(this);
            adapter.setShowEncrypted(true);
            adapter.setFiles(files);
            grid.setAdapter(adapter);
        }
    }

    private void confirmSelection() {
        Intent result = new Intent();
        if (albumPath != null) {
            result.putExtra(EXTRA_ALBUM_PATH, albumPath);
        }
        setResult(RESULT_OK, result);
        finish();
    }
}
