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
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.ui.MainContract;
import com.rulerhao.media_protector.ui.MainPresenter;
import com.rulerhao.media_protector.ui.SortOption;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements MainContract.View {

    // ─── Protected-mode grid ──────────────────────────────────────────────
    private GridView     gridView;
    private MediaAdapter adapter;
    private MainContract.Presenter presenter;

    // ─── Mode tabs ────────────────────────────────────────────────────────
    private boolean showEncrypted     = true;
    private Button  btnModeProtected;
    private Button  btnModeOriginal;
    private View    indicatorProtected;
    private View    indicatorOriginal;

    // ─── Browse (Original) mode ───────────────────────────────────────────
    private enum BrowseMode { DATE, FOLDER }
    private BrowseMode   browseMode = BrowseMode.DATE;

    private ListView      browseListView;
    private FolderAdapter browseAdapter;
    private ProgressBar   browseProgressBar;

    private View   browseModeBar;
    private Button btnBrowseModeDate;
    private Button btnBrowseModeFolder;
    private View   indicatorBrowseDate;
    private View   indicatorBrowseFolder;

    // ─── Shared toolbar buttons ───────────────────────────────────────────
    private Button btnSort;
    private Button btnBrowseFolder;

    // ─── Selection bar (shared across modes) ──────────────────────────────
    private View   selectionBar;
    private Button btnSelectAll;
    private Button btnEncrypt;

    // ─── Empty / progress ─────────────────────────────────────────────────
    private TextView tvEmpty;

    /** True when one or more files are selected in grid mode. */
    private boolean gridSelectionActive = false;

    /** Theme that was active when this activity was created. */
    private boolean appliedDark;

    private static final int PERMISSION_REQUEST_CODE     = 100;
    private static final int FOLDER_BROWSER_REQUEST_CODE = 200;

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedDark = ThemeHelper.isDarkMode(this);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_main);

        // Protected-mode views
        gridView           = findViewById(R.id.gridView);
        btnModeProtected   = findViewById(R.id.btnModeProtected);
        btnModeOriginal    = findViewById(R.id.btnModeOriginal);
        indicatorProtected = findViewById(R.id.indicatorProtected);
        indicatorOriginal  = findViewById(R.id.indicatorOriginal);

        // Browse-mode views
        browseListView        = findViewById(R.id.browseListView);
        browseProgressBar     = findViewById(R.id.browseProgressBar);
        browseModeBar         = findViewById(R.id.browseModeBar);
        btnBrowseModeDate     = findViewById(R.id.btnBrowseModeDate);
        btnBrowseModeFolder   = findViewById(R.id.btnBrowseModeFolder);
        indicatorBrowseDate   = findViewById(R.id.indicatorBrowseDate);
        indicatorBrowseFolder = findViewById(R.id.indicatorBrowseFolder);

        // Shared
        selectionBar  = findViewById(R.id.selectionBar);
        btnSelectAll  = findViewById(R.id.btnSelectAll);
        btnEncrypt    = findViewById(R.id.btnEncrypt);
        tvEmpty       = findViewById(R.id.tvEmpty);
        btnSort       = findViewById(R.id.btnSort);
        btnBrowseFolder = findViewById(R.id.btnBrowseFolder);

        // Adapters / presenter
        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        browseAdapter = new FolderAdapter(this, false /* unencrypted */);
        browseListView.setAdapter(browseAdapter);

        presenter = new MainPresenter(this, new MediaRepository());

        // ── Toolbar ──────────────────────────────────────────────────────
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        btnSort.setOnClickListener(this::showSortMenu);
        btnBrowseFolder.setOnClickListener(v -> {
            Intent intent = new Intent(this, FolderBrowserActivity.class);
            intent.putExtra(FolderBrowserActivity.EXTRA_SHOW_ENCRYPTED, showEncrypted);
            startActivityForResult(intent, FOLDER_BROWSER_REQUEST_CODE);
        });

        // ── Mode tabs ────────────────────────────────────────────────────
        btnModeProtected.setOnClickListener(v -> presenter.switchMode(true));
        btnModeOriginal.setOnClickListener(v  -> presenter.switchMode(false));

        // ── Browse sub-tabs ──────────────────────────────────────────────
        btnBrowseModeDate.setOnClickListener(v   -> switchBrowseMode(BrowseMode.DATE));
        btnBrowseModeFolder.setOnClickListener(v -> switchBrowseMode(BrowseMode.FOLDER));

        // ── Grid interactions ────────────────────────────────────────────
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (!(item instanceof File)) return;
            if (gridSelectionActive) {
                presenter.toggleSelection((File) item);
            } else {
                openViewer((File) item);
            }
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item instanceof File) presenter.toggleSelection((File) item);
            return true;
        });

        // ── Browse folder-header click: return folder path to caller ──────
        browseListView.setOnItemClickListener((parent, view, position, id) -> {
            FolderAdapter.BrowseItem item =
                    (FolderAdapter.BrowseItem) browseAdapter.getItem(position);
            if (item != null
                    && item.type == FolderAdapter.TYPE_FOLDER_HEADER
                    && item.folder != null) {
                // In browse-original mode, folder headers are just labels (not selectable).
                // No action needed — files are opened directly from the strip.
            }
        });

        // Thumbnail tap inside strips → open viewer
        browseAdapter.setOnFileClickListener((paths, index) -> {
            if (browseAdapter.getSelectedFiles().isEmpty()) {
                // Not in selection mode — open viewer.
                Intent intent = new Intent(this, MediaViewerActivity.class);
                intent.putExtra(MediaViewerActivity.EXTRA_FILE_LIST, paths);
                intent.putExtra(MediaViewerActivity.EXTRA_FILE_INDEX, index);
                intent.putExtra(MediaViewerActivity.EXTRA_ENCRYPTED, false);
                startActivity(intent);
            }
        });

        // Browse selection changes → update bottom bar
        browseAdapter.setOnSelectionChangedListener(count -> {
            if (count == 0) {
                selectionBar.setVisibility(View.GONE);
            } else {
                selectionBar.setVisibility(View.VISIBLE);
                btnSelectAll.setText(R.string.btn_select_all);
                btnEncrypt.setEnabled(true);
                btnEncrypt.setText(getString(R.string.btn_protect, count));
            }
        });

        // ── Selection bar ────────────────────────────────────────────────
        btnSelectAll.setOnClickListener(v -> {
            if (showEncrypted) {
                // Grid mode
                if (btnSelectAll.getText().toString().equals(getString(R.string.btn_select_all))) {
                    presenter.selectAll();
                } else {
                    presenter.deselectAll();
                }
            } else {
                // Browse mode
                if (btnSelectAll.getText().toString().equals(getString(R.string.btn_select_all))) {
                    browseAdapter.selectAll();
                } else {
                    browseAdapter.clearSelection();
                    selectionBar.setVisibility(View.GONE);
                }
            }
        });

        btnEncrypt.setOnClickListener(v -> {
            if (showEncrypted) {
                presenter.decryptSelected();
            } else {
                // Protect (encrypt) files selected in browse
                Set<File> sel = browseAdapter.getSelectedFiles();
                if (!sel.isEmpty()) {
                    presenter.encryptFiles(new ArrayList<>(sel));
                }
            }
        });

        updateModeTabUI();
        presenter.onCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeHelper.isDarkMode(this) != appliedDark) recreate();
    }

    @Override
    protected void onDestroy() {
        browseAdapter.destroy();
        adapter.destroy();
        presenter.onDestroy();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────
    // MainContract.View
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void showFiles(List<File> files) {
        if (showEncrypted) {
            // Protected mode: populate grid
            adapter.setFiles(files);
            tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
            if (!files.isEmpty()) {
                Toast.makeText(this,
                        getString(R.string.toast_found_files, files.size()),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            // Original (browse) mode: build grouped browse list
            browseProgressBar.setVisibility(View.GONE);
            List<FolderAdapter.BrowseItem> items =
                    (browseMode == BrowseMode.DATE)
                            ? BrowseListBuilder.buildDateItems(files)
                            : BrowseListBuilder.buildFolderItems(files);
            browseAdapter.setItems(items);
            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void showPermissionError() {
        Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this,
                message != null ? message : getString(R.string.error_generic),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showOperationResult(int succeeded, int failed) {
        btnEncrypt.setEnabled(true);
        Toast.makeText(this,
                getString(R.string.toast_operation_result, succeeded, failed),
                Toast.LENGTH_SHORT).show();
        if (!showEncrypted) {
            // Browse encrypt finished: clear selection state
            browseAdapter.clearSelection();
            selectionBar.setVisibility(View.GONE);
        }
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
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
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
        gridSelectionActive = enabled;
        selectionBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnSelectAll.setText(enabled && count > 0
                ? R.string.btn_deselect_all : R.string.btn_select_all);
        btnEncrypt.setEnabled(true);
        btnEncrypt.setText(showEncrypted
                ? getString(R.string.btn_decrypt, count)
                : getString(R.string.btn_protect, count));

        if (presenter instanceof MainPresenter) {
            adapter.updateSelection(((MainPresenter) presenter).getSelectedFiles());
        }
    }

    @Override
    public void updateMode(boolean isEncryptedMode) {
        showEncrypted = isEncryptedMode;
        gridSelectionActive = false;
        adapter.setShowEncrypted(isEncryptedMode);
        selectionBar.setVisibility(View.GONE);
        updateModeTabUI();

        if (isEncryptedMode) {
            // Switch to grid view
            gridView.setVisibility(View.VISIBLE);
            browseListView.setVisibility(View.GONE);
            browseModeBar.setVisibility(View.GONE);
            browseProgressBar.setVisibility(View.GONE);
            btnSort.setVisibility(View.VISIBLE);
            btnBrowseFolder.setVisibility(View.VISIBLE);
            browseAdapter.clearSelection();
        } else {
            // Switch to browse view
            gridView.setVisibility(View.GONE);
            browseListView.setVisibility(View.VISIBLE);
            browseModeBar.setVisibility(View.VISIBLE);
            browseProgressBar.setVisibility(View.VISIBLE);
            btnSort.setVisibility(View.GONE);
            btnBrowseFolder.setVisibility(View.GONE);
            browseAdapter.setItems(new ArrayList<>());
            // presenter.switchMode(false) will call loadMedia() → showFiles() populates the list
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Permission callbacks
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
        } else if (requestCode == FOLDER_BROWSER_REQUEST_CODE
                && resultCode == RESULT_OK && data != null) {
            String folderPath = data.getStringExtra(FolderBrowserActivity.EXTRA_SELECTED_FOLDER);
            if (folderPath != null) {
                File selectedFolder = new File(folderPath);
                presenter.loadFolder(selectedFolder);
                Toast.makeText(this,
                        getString(R.string.toast_browsing_folder, selectedFolder.getName()),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Browse mode switching
    // ─────────────────────────────────────────────────────────────────────

    private void switchBrowseMode(BrowseMode newMode) {
        if (browseMode == newMode) return;
        browseMode = newMode;
        updateBrowseModeTabUI();
        // Rebuild from the adapter's current items (no re-scan needed)
        // We re-trigger the presenter scan to get fresh data for the new grouping.
        browseProgressBar.setVisibility(View.VISIBLE);
        browseAdapter.setItems(new ArrayList<>());
        presenter.switchMode(false); // same mode, triggers loadMedia() → showFiles()
    }

    private void updateBrowseModeTabUI() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        int active   = tv.data;
        int inactive = getColor(R.color.tab_unselected);

        btnBrowseModeDate.setTextColor(browseMode == BrowseMode.DATE ? active : inactive);
        btnBrowseModeFolder.setTextColor(browseMode == BrowseMode.FOLDER ? active : inactive);
        indicatorBrowseDate.setVisibility(
                browseMode == BrowseMode.DATE ? View.VISIBLE : View.INVISIBLE);
        indicatorBrowseFolder.setVisibility(
                browseMode == BrowseMode.FOLDER ? View.VISIBLE : View.INVISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode tab UI
    // ─────────────────────────────────────────────────────────────────────

    private void updateModeTabUI() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorToolbarText, tv, true);
        int active   = tv.data;
        int inactive = getColor(R.color.tab_unselected);
        btnModeProtected.setTextColor(showEncrypted ? active : inactive);
        btnModeOriginal.setTextColor(showEncrypted ? inactive : active);
        indicatorProtected.setVisibility(showEncrypted ? View.VISIBLE : View.INVISIBLE);
        indicatorOriginal.setVisibility(showEncrypted ? View.INVISIBLE : View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Viewer
    // ─────────────────────────────────────────────────────────────────────

    private void openViewer(File file) {
        int count = adapter.getCount();
        String[] paths = new String[count];
        int startIndex = 0;
        for (int i = 0; i < count; i++) {
            File f = (File) adapter.getItem(i);
            paths[i] = f.getAbsolutePath();
            if (f.getAbsolutePath().equals(file.getAbsolutePath())) startIndex = i;
        }
        Intent intent = new Intent(this, MediaViewerActivity.class);
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_LIST, paths);
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_INDEX, startIndex);
        intent.putExtra(MediaViewerActivity.EXTRA_ENCRYPTED, showEncrypted);
        startActivity(intent);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sort menu (Protected mode only)
    // ─────────────────────────────────────────────────────────────────────

    private void showSortMenu(View v) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
        popup.getMenu().add(0, 0, 0, R.string.sort_name_az);
        popup.getMenu().add(0, 1, 1, R.string.sort_name_za);
        popup.getMenu().add(0, 2, 2, R.string.sort_date_oldest);
        popup.getMenu().add(0, 3, 3, R.string.sort_date_newest);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            SortOption option = null;
            if      (id == 0) option = SortOption.NAME_ASC;
            else if (id == 1) option = SortOption.NAME_DESC;
            else if (id == 2) option = SortOption.DATE_ASC;
            else if (id == 3) option = SortOption.DATE_DESC;
            if (option != null) presenter.sortFiles(option);
            return true;
        });
        popup.show();
    }
}
