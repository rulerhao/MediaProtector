package com.rulerhao.media_protector.album;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.core.FileConfig;
import com.rulerhao.media_protector.shared.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual album picker: shows a grid of album cards with cover thumbnails.
 * Replaces the old text-only AlertDialog lists for both Move-to-Album and
 * Encrypt-to-Album flows.
 *
 * <h3>Input extras</h3>
 * <ul>
 *   <li>{@link #EXTRA_MODE} — {@link #MODE_MOVE} or {@link #MODE_ENCRYPT}</li>
 *   <li>{@link #EXTRA_FILE_PATHS} — {@code ArrayList<String>} of file paths being operated on</li>
 * </ul>
 *
 * <h3>Output extras (on RESULT_OK)</h3>
 * <ul>
 *   <li>{@link #EXTRA_RESULT_TYPE} — one of the {@code RESULT_TYPE_*} constants</li>
 *   <li>{@link #EXTRA_RESULT_ALBUM_PATH} — absolute path of the chosen album dir
 *       (only present when {@code RESULT_TYPE} is {@link #RESULT_TYPE_ALBUM})</li>
 * </ul>
 */
public class AlbumPickerActivity extends Activity {

    // ─── Intent extras ────────────────────────────────────────────────────

    public static final String EXTRA_MODE       = "mode";
    public static final String EXTRA_FILE_PATHS = "file_paths";

    public static final int MODE_MOVE    = 0;
    public static final int MODE_ENCRYPT = 1;

    // ─── Result extras ────────────────────────────────────────────────────

    public static final String EXTRA_RESULT_TYPE       = "result_type";
    public static final String EXTRA_RESULT_ALBUM_PATH = "result_album_path";

    /** User selected a specific album directory. */
    public static final int RESULT_TYPE_ALBUM            = 0;
    /** User chose "Main Collection" (encrypt to root, MODE_ENCRYPT only). */
    public static final int RESULT_TYPE_MAIN_COLLECTION  = 1;
    /** User chose "Remove from Album" (move to root, MODE_MOVE only). */
    public static final int RESULT_TYPE_REMOVE_FROM_ALBUM = 2;

    // ─── Fields ───────────────────────────────────────────────────────────

    private int mode;
    private List<String> filePaths;
    private AlbumAdapter adapter;
    /** Mirror of the regular album dir list, aligned with adapter positions for quick lookup. */
    private List<File> albumDirs;

    // ─── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_picker);

        mode      = getIntent().getIntExtra(EXTRA_MODE, MODE_MOVE);
        filePaths = getIntent().getStringArrayListExtra(EXTRA_FILE_PATHS);
        if (filePaths == null) filePaths = new ArrayList<>();

        // Toolbar
        findViewById(R.id.btnPickerBack).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tvPickerTitle);
        tvTitle.setText(mode == MODE_ENCRYPT
                ? getString(R.string.encrypt_to_album_title)
                : getString(R.string.album_move_to));

        // Build adapter and grid
        adapter = new AlbumAdapter(this);
        GridView grid = findViewById(R.id.albumPickerGrid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> onItemPicked(pos));

        buildItems();
    }

    // ─── Build ────────────────────────────────────────────────────────────

    private void buildItems() {
        File protectedRoot = FileConfig.getProtectedFolder();
        albumDirs = AlbumManager.getAlbumDirs(protectedRoot);

        List<AlbumAdapter.AlbumItem> items = new ArrayList<>();

        if (mode == MODE_ENCRYPT) {
            // First card: Main Collection
            items.add(new AlbumAdapter.AlbumItem(
                    getString(R.string.encrypt_to_main),
                    R.drawable.ic_empty_folder));
        } else {
            // First card: Remove from Album
            items.add(new AlbumAdapter.AlbumItem(
                    getString(R.string.album_remove_from),
                    R.drawable.ic_empty_folder));
        }

        // One card per album sub-directory
        for (File dir : albumDirs) {
            File cover = AlbumManager.getAlbumCover(dir);
            int  count = AlbumManager.getFileCount(dir);
            items.add(new AlbumAdapter.AlbumItem(dir, dir.getName(), count, cover));
        }

        // "+ New Album" add card
        items.add(new AlbumAdapter.AlbumItem());

        adapter.setItems(items);
    }

    // ─── Interaction ──────────────────────────────────────────────────────

    private void onItemPicked(int position) {
        AlbumAdapter.AlbumItem item = adapter.getItem(position);

        if (item.type == AlbumAdapter.TYPE_ADD) {
            showCreateAlbumDialog();
            return;
        }

        if (item.type == AlbumAdapter.TYPE_ACTION) {
            // position 0 is always the action card
            Intent result = new Intent();
            result.putExtra(EXTRA_RESULT_TYPE,
                    mode == MODE_ENCRYPT
                            ? RESULT_TYPE_MAIN_COLLECTION
                            : RESULT_TYPE_REMOVE_FROM_ALBUM);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        // Regular album card — position 0 is the action card, so album index = position - 1
        File dir = albumDirs.get(position - 1);
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_TYPE, RESULT_TYPE_ALBUM);
        result.putExtra(EXTRA_RESULT_ALBUM_PATH, dir.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showCreateAlbumDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.album_name_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 32, 48, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.album_create_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!AlbumManager.isValidName(name)) return;
                    File protectedRoot = FileConfig.getProtectedFolder();
                    if (AlbumManager.albumExists(protectedRoot, name)) {
                        Toast.makeText(this, R.string.album_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newAlbum = AlbumManager.createAlbum(protectedRoot, name);
                    if (newAlbum == null) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.album_created, Toast.LENGTH_SHORT).show();
                    Intent result = new Intent();
                    result.putExtra(EXTRA_RESULT_TYPE, RESULT_TYPE_ALBUM);
                    result.putExtra(EXTRA_RESULT_ALBUM_PATH, newAlbum.getAbsolutePath());
                    setResult(RESULT_OK, result);
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
