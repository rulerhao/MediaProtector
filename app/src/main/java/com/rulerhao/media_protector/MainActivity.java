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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.ui.MainContract;
import com.rulerhao.media_protector.ui.MainPresenter;
import com.rulerhao.media_protector.util.SecurityHelper;
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

    // ─── Mode state ──────────────────────────────────────────────────────
    private boolean showEncrypted     = true;

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

    // ─── Bottom navigation bar ───────────────────────────────────────────
    private View      navProtected;
    private View      navOriginal;
    private View      navSettings;
    private ImageView navProtectedIcon;
    private ImageView navOriginalIcon;
    private ImageView navSettingsIcon;

    // ─── Settings page ───────────────────────────────────────────────────
    private View   settingsPage;
    private Switch switchDarkMode;
    private Switch switchPinLock;
    private Switch switchFingerprint;
    private View   fingerprintRow;
    private View   fingerprintDivider;
    private View   changePinRow;
    private View   changePinDivider;

    // ─── Current navigation tab ──────────────────────────────────────────
    private enum NavTab { PROTECTED, ORIGINAL, SETTINGS }
    private NavTab currentNavTab = NavTab.PROTECTED;

    // ─── Selection bar (shared across modes) ──────────────────────────────
    private View   selectionBar;
    private Button btnSelectAll;
    private Button btnExport;
    private Button btnEncrypt;

    // ─── Empty / progress ─────────────────────────────────────────────────
    private TextView tvEmpty;

    /** True when one or more files are selected in grid mode. */
    private boolean gridSelectionActive = false;

    /** Theme that was active when this activity was created. */
    private boolean appliedDark;

    private static final int PERMISSION_REQUEST_CODE     = 100;
    private static final int PIN_SETUP_REQUEST_CODE      = 300;
    private static final int PIN_CHANGE_REQUEST_CODE     = 301;
    private static final int LOCK_SCREEN_REQUEST_CODE    = 302;

    /** Track whether app is authenticated (for lock screen). */
    private boolean isAuthenticated = false;

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
        gridView = findViewById(R.id.gridView);

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
        btnExport     = findViewById(R.id.btnExport);
        btnEncrypt    = findViewById(R.id.btnEncrypt);
        tvEmpty       = findViewById(R.id.tvEmpty);

        // Bottom navigation bar
        navProtected     = findViewById(R.id.navProtected);
        navOriginal      = findViewById(R.id.navOriginal);
        navSettings      = findViewById(R.id.navSettings);
        navProtectedIcon = findViewById(R.id.navProtectedIcon);
        navOriginalIcon  = findViewById(R.id.navOriginalIcon);
        navSettingsIcon  = findViewById(R.id.navSettingsIcon);

        // Settings page
        settingsPage      = findViewById(R.id.settingsPage);
        switchDarkMode    = findViewById(R.id.switchDarkMode);
        switchPinLock     = findViewById(R.id.switchPinLock);
        switchFingerprint = findViewById(R.id.switchFingerprint);
        fingerprintRow    = findViewById(R.id.fingerprintRow);
        fingerprintDivider = findViewById(R.id.fingerprintDivider);
        changePinRow      = findViewById(R.id.changePinRow);
        changePinDivider  = findViewById(R.id.changePinDivider);

        // Adapters / presenter
        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        browseAdapter = new FolderAdapter(this, false /* unencrypted */);
        browseListView.setAdapter(browseAdapter);

        presenter = new MainPresenter(this, new MediaRepository());

        // ── Bottom navigation bar ────────────────────────────────────────
        navProtected.setOnClickListener(v -> switchNavTab(NavTab.PROTECTED));
        navOriginal.setOnClickListener(v  -> switchNavTab(NavTab.ORIGINAL));
        navSettings.setOnClickListener(v  -> switchNavTab(NavTab.SETTINGS));

        // ── Settings dark mode switch ────────────────────────────────────
        switchDarkMode.setChecked(ThemeHelper.isDarkMode(this));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != ThemeHelper.isDarkMode(this)) {
                ThemeHelper.toggleTheme(this);
                recreate();
            }
        });

        // ── Security settings ────────────────────────────────────────────
        setupSecuritySettings();

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

        btnExport.setOnClickListener(v -> {
            // Export selected protected files as decrypted copies to Downloads/MediaProtector_Export
            File exportFolder = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "MediaProtector_Export");
            presenter.exportSelected(exportFolder);
        });

        updateNavBarUI();

        // Check if lock screen is needed
        if (SecurityHelper.isLockEnabled(this) && !isAuthenticated) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_UNLOCK);
            startActivityForResult(lockIntent, LOCK_SCREEN_REQUEST_CODE);
        } else {
            isAuthenticated = true;
        }

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
    public void showExportResult(int succeeded, int failed, String folderName) {
        btnExport.setEnabled(true);
        if (failed == 0) {
            Toast.makeText(this,
                    getString(R.string.toast_export_complete, succeeded, folderName),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                    getString(R.string.toast_export_failed, succeeded, failed),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showExportProgress(int done, int total) {
        btnExport.setEnabled(false);
        btnExport.setText(getString(R.string.progress_exporting, done, total));
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

        // Show export button only in Protected mode
        btnExport.setVisibility(showEncrypted ? View.VISIBLE : View.GONE);

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
        // Navigation bar UI is already updated by switchNavTab
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
        } else if (requestCode == PIN_SETUP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, R.string.toast_pin_enabled, Toast.LENGTH_SHORT).show();
                refreshSecuritySettingsUI();
            } else {
                // User cancelled - reset switch
                switchPinLock.setChecked(false);
            }
        } else if (requestCode == PIN_CHANGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, R.string.toast_pin_enabled, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == LOCK_SCREEN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                isAuthenticated = true;
            } else {
                // User didn't authenticate - exit app
                finishAffinity();
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
    // Navigation tab switching
    // ─────────────────────────────────────────────────────────────────────

    private void switchNavTab(NavTab tab) {
        if (currentNavTab == tab) return;
        currentNavTab = tab;
        updateNavBarUI();

        // Hide all content first
        gridView.setVisibility(View.GONE);
        browseListView.setVisibility(View.GONE);
        browseModeBar.setVisibility(View.GONE);
        browseProgressBar.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        selectionBar.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        switch (tab) {
            case PROTECTED:
                showEncrypted = true;
                adapter.setShowEncrypted(true);
                gridView.setVisibility(View.VISIBLE);
                browseAdapter.clearSelection();
                presenter.switchMode(true);
                break;

            case ORIGINAL:
                showEncrypted = false;
                adapter.setShowEncrypted(false);
                browseListView.setVisibility(View.VISIBLE);
                browseModeBar.setVisibility(View.VISIBLE);
                browseProgressBar.setVisibility(View.VISIBLE);
                browseAdapter.setItems(new ArrayList<>());
                presenter.switchMode(false);
                break;

            case SETTINGS:
                settingsPage.setVisibility(View.VISIBLE);
                // Refresh settings state
                switchDarkMode.setChecked(ThemeHelper.isDarkMode(this));
                refreshSecuritySettingsUI();
                break;
        }
    }

    private void updateNavBarUI() {
        int active   = getColor(R.color.tab_indicator);
        int inactive = getColor(R.color.tab_unselected);

        navProtectedIcon.setColorFilter(currentNavTab == NavTab.PROTECTED ? active : inactive);
        navOriginalIcon.setColorFilter(currentNavTab == NavTab.ORIGINAL ? active : inactive);
        navSettingsIcon.setColorFilter(currentNavTab == NavTab.SETTINGS ? active : inactive);
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
    // Security settings
    // ─────────────────────────────────────────────────────────────────────

    private void setupSecuritySettings() {
        // PIN lock switch
        switchPinLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isPinEnabled(this)) {
                // Enable PIN - launch setup
                Intent intent = new Intent(this, LockScreenActivity.class);
                intent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_SETUP);
                startActivityForResult(intent, PIN_SETUP_REQUEST_CODE);
            } else if (!isChecked && SecurityHelper.isPinEnabled(this)) {
                // Disable PIN
                SecurityHelper.clearPin(this);
                Toast.makeText(this, R.string.toast_pin_disabled, Toast.LENGTH_SHORT).show();
                refreshSecuritySettingsUI();
            }
        });

        // Fingerprint switch
        switchFingerprint.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isFingerprintAvailable(this)) {
                Toast.makeText(this, R.string.toast_fingerprint_not_available, Toast.LENGTH_SHORT).show();
                switchFingerprint.setChecked(false);
                return;
            }
            SecurityHelper.setFingerprintEnabled(this, isChecked);
            Toast.makeText(this,
                    isChecked ? R.string.toast_fingerprint_enabled : R.string.toast_fingerprint_disabled,
                    Toast.LENGTH_SHORT).show();
        });

        // Change PIN row click
        changePinRow.setOnClickListener(v -> {
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_CHANGE);
            startActivityForResult(intent, PIN_CHANGE_REQUEST_CODE);
        });

        // Initial state
        refreshSecuritySettingsUI();
    }

    private void refreshSecuritySettingsUI() {
        boolean pinEnabled = SecurityHelper.isPinEnabled(this);
        boolean fingerprintAvailable = SecurityHelper.isFingerprintAvailable(this);

        // Update PIN switch without triggering listener
        switchPinLock.setOnCheckedChangeListener(null);
        switchPinLock.setChecked(pinEnabled);
        switchPinLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SecurityHelper.isPinEnabled(this)) {
                Intent intent = new Intent(this, LockScreenActivity.class);
                intent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_SETUP);
                startActivityForResult(intent, PIN_SETUP_REQUEST_CODE);
            } else if (!isChecked && SecurityHelper.isPinEnabled(this)) {
                SecurityHelper.clearPin(this);
                Toast.makeText(this, R.string.toast_pin_disabled, Toast.LENGTH_SHORT).show();
                refreshSecuritySettingsUI();
            }
        });

        // Show/hide fingerprint option (only when PIN is enabled and fingerprint is available)
        if (pinEnabled && fingerprintAvailable) {
            fingerprintRow.setVisibility(View.VISIBLE);
            fingerprintDivider.setVisibility(View.VISIBLE);
            switchFingerprint.setChecked(SecurityHelper.isFingerprintEnabled(this));
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

    // ─────────────────────────────────────────────────────────────────────
    // Lock screen check
    // ─────────────────────────────────────────────────────────────────────

    private void checkLockScreen() {
        if (SecurityHelper.isLockEnabled(this) && !isAuthenticated) {
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_UNLOCK);
            startActivityForResult(intent, LOCK_SCREEN_REQUEST_CODE);
        }
    }
}
