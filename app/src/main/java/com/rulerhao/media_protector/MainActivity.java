package com.rulerhao.media_protector;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.ui.MainContract;
import com.rulerhao.media_protector.ui.MainPresenter;
import com.rulerhao.media_protector.ui.SortOption;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.util.List;

public class MainActivity extends Activity implements MainContract.View {

    private GridView  gridView;
    private MediaAdapter adapter;
    private MainContract.Presenter presenter;

    // Mode tabs
    private boolean showEncrypted     = true;
    private Button  btnModeProtected;
    private Button  btnModeOriginal;
    private View    indicatorProtected;
    private View    indicatorOriginal;

    // Selection bottom bar
    private View     selectionBar;
    private Button   btnSelectAll;
    private Button   btnEncrypt;

    // Empty state
    private TextView tvEmpty;

    /** True when one or more files are selected; taps open the viewer when false. */
    private boolean selectionActive = false;

    private static final int PERMISSION_REQUEST_CODE     = 100;
    private static final int FOLDER_BROWSER_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_main);

        gridView           = findViewById(R.id.gridView);
        btnModeProtected   = findViewById(R.id.btnModeProtected);
        btnModeOriginal    = findViewById(R.id.btnModeOriginal);
        indicatorProtected = findViewById(R.id.indicatorProtected);
        indicatorOriginal  = findViewById(R.id.indicatorOriginal);
        selectionBar       = findViewById(R.id.selectionBar);
        btnSelectAll       = findViewById(R.id.btnSelectAll);
        btnEncrypt         = findViewById(R.id.btnEncrypt);
        tvEmpty            = findViewById(R.id.tvEmpty);
        Button btnSort        = findViewById(R.id.btnSort);
        Button btnBrowseFolder = findViewById(R.id.btnBrowseFolder);
        Button btnThemeToggle  = findViewById(R.id.btnThemeToggle);

        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        presenter = new MainPresenter(this, new MediaRepository());

        // Toolbar actions
        btnThemeToggle.setText(ThemeHelper.isDarkMode(this) ? "\u2600" : "\u263E");
        btnThemeToggle.setOnClickListener(v -> {
            ThemeHelper.toggleTheme(this);
            recreate();
        });
        btnSort.setOnClickListener(this::showSortMenu);
        btnBrowseFolder.setOnClickListener(v -> {
            Intent intent = new Intent(this, FolderBrowserActivity.class);
            intent.putExtra(FolderBrowserActivity.EXTRA_SHOW_ENCRYPTED, showEncrypted);
            startActivityForResult(intent, FOLDER_BROWSER_REQUEST_CODE);
        });

        // Mode tab clicks
        btnModeProtected.setOnClickListener(v -> presenter.switchMode(true));
        btnModeOriginal.setOnClickListener(v -> presenter.switchMode(false));

        // Tap: open viewer when idle, toggle selection when in selection mode.
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (!(item instanceof File)) return;
            if (selectionActive) {
                presenter.toggleSelection((File) item);
            } else {
                openViewer((File) item);
            }
        });

        // Long-press: enter selection mode.
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item instanceof File) {
                presenter.toggleSelection((File) item);
            }
            return true;
        });

        btnSelectAll.setOnClickListener(v -> {
            if (btnSelectAll.getText().toString().equals(getString(R.string.btn_select_all))) {
                presenter.selectAll();
            } else {
                presenter.deselectAll();
            }
        });

        btnEncrypt.setOnClickListener(v -> {
            if (showEncrypted) {
                presenter.decryptSelected();
            } else {
                presenter.encryptSelected();
            }
        });

        updateModeTabUI();
        presenter.onCreate();
    }

    // -------------------------------------------------------------------------
    // MainContract.View
    // -------------------------------------------------------------------------

    @Override
    public void showFiles(List<File> files) {
        adapter.setFiles(files);
        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        if (!files.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_found_files, files.size()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showPermissionError() {
        Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message != null ? message : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
    }

    @Override
    public void showOperationResult(int succeeded, int failed) {
        btnEncrypt.setEnabled(true);
        Toast.makeText(this,
                getString(R.string.toast_operation_result, succeeded, failed),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showProgress(int done, int total, boolean encrypting) {
        btnEncrypt.setEnabled(false);
        btnEncrypt.setText(getString(
                encrypting ? R.string.progress_encrypting : R.string.progress_decrypting,
                done, total));
    }

    @Override
    public void requestStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        } else {
            presenter.onPermissionGranted();
        }
    }

    @Override
    public void requestManageAllFilesPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void updateSelectionMode(boolean enabled, int count) {
        selectionActive = enabled;
        selectionBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnSelectAll.setText(enabled && count > 0 ? R.string.btn_deselect_all : R.string.btn_select_all);
        btnEncrypt.setEnabled(true);
        btnEncrypt.setText(showEncrypted
                ? getString(R.string.btn_decrypt, count)
                : getString(R.string.btn_encrypt, count));

        if (presenter instanceof MainPresenter) {
            adapter.updateSelection(((MainPresenter) presenter).getSelectedFiles());
        }
    }

    @Override
    public void updateMode(boolean isEncryptedMode) {
        showEncrypted = isEncryptedMode;
        selectionActive = false;
        adapter.setShowEncrypted(isEncryptedMode);
        selectionBar.setVisibility(View.GONE);
        updateModeTabUI();
    }

    // -------------------------------------------------------------------------
    // Permission callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                presenter.onPermissionGranted();
            } else {
                presenter.onPermissionDenied();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    presenter.onPermissionGranted();
                } else {
                    presenter.onPermissionDenied();
                }
            }
        } else if (requestCode == FOLDER_BROWSER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String folderPath = data.getStringExtra(FolderBrowserActivity.EXTRA_SELECTED_FOLDER);
            if (folderPath != null) {
                File selectedFolder = new File(folderPath);
                presenter.loadFolder(selectedFolder);
                Toast.makeText(this, getString(R.string.toast_browsing_folder, selectedFolder.getName()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        adapter.destroy();
        presenter.onDestroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Viewer
    // -------------------------------------------------------------------------

    private void openViewer(File file) {
        Intent intent = new Intent(this, MediaViewerActivity.class);
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        intent.putExtra(MediaViewerActivity.EXTRA_ENCRYPTED, showEncrypted);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Mode tab UI
    // -------------------------------------------------------------------------

    private void updateModeTabUI() {
        int active   = getColor(R.color.white);
        int inactive = getColor(R.color.tab_unselected);
        btnModeProtected.setTextColor(showEncrypted ? active : inactive);
        btnModeOriginal.setTextColor(showEncrypted ? inactive : active);
        indicatorProtected.setVisibility(showEncrypted ? View.VISIBLE : View.INVISIBLE);
        indicatorOriginal.setVisibility(showEncrypted ? View.INVISIBLE : View.VISIBLE);
    }

    // -------------------------------------------------------------------------
    // Sort menu
    // -------------------------------------------------------------------------

    private void showSortMenu(View v) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
        popup.getMenu().add(0, 0, 0, R.string.sort_name_az);
        popup.getMenu().add(0, 1, 1, R.string.sort_name_za);
        popup.getMenu().add(0, 2, 2, R.string.sort_date_oldest);
        popup.getMenu().add(0, 3, 3, R.string.sort_date_newest);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            SortOption option = null;
            if (id == 0) option = SortOption.NAME_ASC;
            else if (id == 1) option = SortOption.NAME_DESC;
            else if (id == 2) option = SortOption.DATE_ASC;
            else if (id == 3) option = SortOption.DATE_DESC;

            if (option != null) presenter.sortFiles(option);
            return true;
        });
        popup.show();
    }
}
