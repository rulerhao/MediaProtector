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

import com.rulerhao.media_protector.core.MediaRepository;
import com.rulerhao.media_protector.album.AlbumController;
import com.rulerhao.media_protector.core.MainContract;
import com.rulerhao.media_protector.core.MainPresenter;
import com.rulerhao.media_protector.security.OriginalPathStore;
import com.rulerhao.media_protector.media.PreviewPopup;
import com.rulerhao.media_protector.widget.PullToRefreshLayout;
import com.rulerhao.media_protector.security.SecurityHelper;
import com.rulerhao.media_protector.widget.SkeletonView;
import com.rulerhao.media_protector.widget.SwipeableTabLayout;
import com.rulerhao.media_protector.shared.ThemeHelper;
import com.rulerhao.media_protector.media.ThumbnailLoader;
import com.rulerhao.media_protector.disguise.DisguiseHelper;

import android.app.AlertDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ImageButton;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.album.MediaFilter;
import com.rulerhao.media_protector.album.SortOption;
import com.rulerhao.media_protector.album.AlbumAdapter;
import com.rulerhao.media_protector.browse.FolderAdapter;
import com.rulerhao.media_protector.browse.BrowseListBuilder;
import com.rulerhao.media_protector.media.MediaAdapter;
import com.rulerhao.media_protector.media.MediaViewerActivity;
import com.rulerhao.media_protector.security.LockScreenActivity;

public class MainActivity extends Activity implements MainContract.View {

    // ─── Protected-mode grid ──────────────────────────────────────────────
    private GridView     gridView;
    private MediaAdapter adapter;
    private MainContract.Presenter presenter;

    // ─── Search ──────────────────────────────────────────────────────────
    private View        searchBar;
    private EditText    etSearch;
    private ImageButton btnClearSearch;
    private TextView    tvSelectionCount;
    private List<File>  allProtectedFiles = new ArrayList<>();
    private String      currentSearchQuery = "";

    // ─── Mode state ──────────────────────────────────────────────────────
    private boolean showEncrypted     = true;

    // ─── Browse (Original) mode ───────────────────────────────────────────
    private enum BrowseMode { DATE, FOLDER }
    private BrowseMode   browseMode = BrowseMode.DATE;

    private ListView           browseListView;
    private FolderAdapter      browseAdapter;
    private ProgressBar        browseProgressBar;
    private PullToRefreshLayout pullToRefreshProtected;
    private PullToRefreshLayout pullToRefreshBrowse;

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
    private TextView  navProtectedLabel;
    private TextView  navOriginalLabel;
    private TextView  navSettingsLabel;

    // ─── Settings page ───────────────────────────────────────────────────
    private View   settingsPage;
    private Switch switchDarkMode;
    private Switch switchPinLock;
    private Switch switchFingerprint;
    private View   fingerprintRow;
    private View   fingerprintDivider;
    private View   changePinRow;
    private View   changePinDivider;
    private View   autoLockRow;
    private View   autoLockDivider;
    private TextView tvAutoLockValue;
    private Switch switchRestoreLocation;
    // Disguise settings
    private Switch switchDisguiseMode;
    private boolean isUpdatingDisguiseSwitch = false;

    // ─── Current navigation tab ──────────────────────────────────────────
    private enum NavTab { PROTECTED, ORIGINAL, SETTINGS }
    private NavTab currentNavTab = NavTab.PROTECTED;
    private SwipeableTabLayout swipeableContent;

    // ─── Selection bar (shared across modes) ──────────────────────────────
    private View   selectionBar;
    private Button btnSelectAll;
    private Button btnExport;
    private Button btnEncrypt;
    private View   dividerExport;

    // ─── Empty / progress ─────────────────────────────────────────────────
    private View         emptyStateContainer;
    private TextView     tvEmpty;
    private SkeletonView skeletonView;

    /** True when one or more files are selected in grid mode. */
    private boolean gridSelectionActive = false;

    /** Theme that was active when this activity was created. */
    private boolean appliedDark;

    private static final int PERMISSION_REQUEST_CODE     = 100;
    private static final int PIN_SETUP_REQUEST_CODE      = 300;
    private static final int PIN_CHANGE_REQUEST_CODE     = 301;
    private static final int LOCK_SCREEN_REQUEST_CODE    = 302;
    private static final int SECTION_VIEW_REQUEST_CODE   = 303;
    private static final int VIEWER_REQUEST_CODE         = 304;

    /** Track whether app is authenticated (for lock screen). */
    private boolean isAuthenticated = false;

    // ─── Scroll position preservation ────────────────────────────────────
    private int protectedScrollPosition = 0;
    private int protectedScrollOffset = 0;
    private int browseDateScrollPosition = 0;
    private int browseDateScrollOffset = 0;
    private int browseFolderScrollPosition = 0;
    private int browseFolderScrollOffset = 0;

    // ─── Multi-select drag ────────────────────────────────────────────────
    private boolean isDraggingToSelect = false;
    private int lastDragPosition = -1;
    private boolean dragSelectMode = true; // true = selecting, false = deselecting

    // ─── Long-press preview ──────────────────────────────────────────────
    private PreviewPopup previewPopup;

    // ─── Album view ───────────────────────────────────────────────────────────
    private AlbumController albumController;
    private GridView    albumGridView;
    private AlbumAdapter albumAdapter;
    private View        albumBar;
    private View        albumBreadcrumbBar;
    private TextView    tvAlbumBreadcrumb;
    private ImageButton btnBackToAlbums;
    private Button      btnMoveToAlbum;
    private ImageButton btnAddAlbum;

    // ─── Sort options ────────────────────────────────────────────────────
    private SortOption currentSortOption = SortOption.DATE_DESC;
    private ImageButton btnSort;

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedDark = ThemeHelper.isDarkMode(this);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_main);

        // Initialize thumbnail loader with screen-adaptive sizing
        ThumbnailLoader.init(this);

        // Initialize preview popup
        previewPopup = new PreviewPopup(this);

        // Protected-mode views
        gridView = findViewById(R.id.gridView);

        // Search bar
        searchBar        = findViewById(R.id.searchBar);
        etSearch         = findViewById(R.id.etSearch);
        btnClearSearch   = findViewById(R.id.btnClearSearch);
        btnSort          = findViewById(R.id.btnSort);
        tvSelectionCount = findViewById(R.id.tvSelectionCount);

        // Browse-mode views
        browseListView         = findViewById(R.id.browseListView);
        browseProgressBar      = findViewById(R.id.browseProgressBar);
        browseModeBar          = findViewById(R.id.browseModeBar);
        pullToRefreshProtected = findViewById(R.id.pullToRefreshProtected);
        pullToRefreshBrowse    = findViewById(R.id.pullToRefreshBrowse);
        btnBrowseModeDate     = findViewById(R.id.btnBrowseModeDate);
        btnBrowseModeFolder   = findViewById(R.id.btnBrowseModeFolder);
        indicatorBrowseDate   = findViewById(R.id.indicatorBrowseDate);
        indicatorBrowseFolder = findViewById(R.id.indicatorBrowseFolder);

        // Shared
        selectionBar        = findViewById(R.id.selectionBar);
        btnSelectAll        = findViewById(R.id.btnSelectAll);
        btnExport           = findViewById(R.id.btnExport);
        btnEncrypt          = findViewById(R.id.btnEncrypt);
        dividerExport       = findViewById(R.id.dividerExport);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmpty             = findViewById(R.id.tvEmpty);
        skeletonView        = findViewById(R.id.skeletonView);

        // Bottom navigation bar
        navProtected      = findViewById(R.id.navProtected);
        navOriginal       = findViewById(R.id.navOriginal);
        navSettings       = findViewById(R.id.navSettings);
        navProtectedIcon  = findViewById(R.id.navProtectedIcon);
        navOriginalIcon   = findViewById(R.id.navOriginalIcon);
        navSettingsIcon   = findViewById(R.id.navSettingsIcon);
        navProtectedLabel = findViewById(R.id.navProtectedLabel);
        navOriginalLabel  = findViewById(R.id.navOriginalLabel);
        navSettingsLabel  = findViewById(R.id.navSettingsLabel);

        // Settings page
        settingsPage      = findViewById(R.id.settingsPage);
        swipeableContent  = findViewById(R.id.swipeableContent);
        switchDarkMode    = findViewById(R.id.switchDarkMode);
        switchPinLock     = findViewById(R.id.switchPinLock);
        switchFingerprint = findViewById(R.id.switchFingerprint);
        fingerprintRow    = findViewById(R.id.fingerprintRow);
        fingerprintDivider = findViewById(R.id.fingerprintDivider);
        changePinRow      = findViewById(R.id.changePinRow);
        changePinDivider  = findViewById(R.id.changePinDivider);
        autoLockRow       = findViewById(R.id.autoLockRow);
        autoLockDivider   = findViewById(R.id.autoLockDivider);
        tvAutoLockValue   = findViewById(R.id.tvAutoLockValue);
        switchRestoreLocation = findViewById(R.id.switchRestoreLocation);
        // Disguise settings
        switchDisguiseMode = findViewById(R.id.switchDisguiseMode);

        // Album views
        albumGridView       = findViewById(R.id.albumGridView);
        albumBar            = findViewById(R.id.albumBar);
        albumBreadcrumbBar  = findViewById(R.id.albumBreadcrumbBar);
        tvAlbumBreadcrumb   = findViewById(R.id.tvAlbumBreadcrumb);
        btnBackToAlbums     = findViewById(R.id.btnBackToAlbums);
        btnMoveToAlbum      = findViewById(R.id.btnMoveToAlbum);
        btnAddAlbum         = findViewById(R.id.btnAddAlbum);

        // Adapters / presenter
        adapter = new MediaAdapter(this);
        gridView.setAdapter(adapter);

        browseAdapter = new FolderAdapter(this, false /* unencrypted */);
        browseListView.setAdapter(browseAdapter);

        albumAdapter = new AlbumAdapter(this);
        albumGridView.setAdapter(albumAdapter);

        albumController = new AlbumController(
                this, buildAlbumControllerCallback(),
                albumBar, albumGridView, albumBreadcrumbBar, tvAlbumBreadcrumb,
                searchBar, pullToRefreshProtected, selectionBar, albumAdapter);

        presenter = new MainPresenter(this, new MediaRepository(this));

        // ── Pull-to-refresh ──────────────────────────────────────────────
        pullToRefreshProtected.setOnRefreshListener(() -> {
            presenter.switchMode(true); // Refresh protected files
        });
        pullToRefreshBrowse.setOnRefreshListener(() -> {
            presenter.switchMode(false); // Refresh browse files
        });

        // ── Swipe between tabs ───────────────────────────────────────────
        swipeableContent.setOnSwipeListener(new SwipeableTabLayout.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                // Swipe left = go to next tab
                if (currentNavTab == NavTab.PROTECTED) {
                    switchNavTab(NavTab.ORIGINAL);
                } else if (currentNavTab == NavTab.ORIGINAL) {
                    switchNavTab(NavTab.SETTINGS);
                }
            }

            @Override
            public void onSwipeRight() {
                // Swipe right = go to previous tab
                if (currentNavTab == NavTab.SETTINGS) {
                    switchNavTab(NavTab.ORIGINAL);
                } else if (currentNavTab == NavTab.ORIGINAL) {
                    switchNavTab(NavTab.PROTECTED);
                }
            }
        });

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

        // ── Storage settings ────────────────────────────────────────────
        switchRestoreLocation.setChecked(OriginalPathStore.isRestoreToOriginalEnabled(this));
        switchRestoreLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            OriginalPathStore.setRestoreToOriginalEnabled(this, isChecked);
        });

        // ── Search bar ──────────────────────────────────────────────────────
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                filterProtectedFiles();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            currentSearchQuery = "";
            filterProtectedFiles();
        });

        // ── Sort button ──────────────────────────────────────────────────
        btnSort.setOnClickListener(v -> showSortDialog());

        // ── Album grid interactions ──────────────────────────────────────
        albumGridView.setOnItemClickListener((parent, view, pos, id) -> {
            AlbumAdapter.AlbumItem item = albumAdapter.getItem(pos);
            if (item.type == AlbumAdapter.TYPE_ADD) {
                albumController.showCreateAlbumDialog(null);
            } else {
                albumController.openAlbum(item.dir);
            }
        });

        albumGridView.setOnItemLongClickListener((parent, view, pos, id) -> {
            AlbumAdapter.AlbumItem item = albumAdapter.getItem(pos);
            if (item.type == AlbumAdapter.TYPE_ALBUM && item.dir != null) {
                albumController.showAlbumOptionsDialog(item);
            }
            return true;
        });

        btnBackToAlbums.setOnClickListener(v -> albumController.showAlbumView(allProtectedFiles));

        btnMoveToAlbum.setOnClickListener(v ->
                albumController.showMoveToAlbumDialog(((MainPresenter) presenter).getSelectedFiles()));

        btnAddAlbum.setOnClickListener(v -> albumController.showCreateAlbumDialog(null));

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
            if (item instanceof File) {
                File file = (File) item;
                if (gridSelectionActive) {
                    // Already in selection mode - start drag-to-select
                    Set<File> currentSelection = ((MainPresenter) presenter).getSelectedFiles();
                    dragSelectMode = !currentSelection.contains(file);
                    isDraggingToSelect = true;
                    lastDragPosition = position;
                    presenter.toggleSelection(file);
                } else {
                    // Not in selection mode - show preview popup
                    previewPopup.show(view, file, showEncrypted);
                }
            }
            return true;
        });

        // Multi-select drag touch listener
        gridView.setOnTouchListener((v, event) -> {
            if (!gridSelectionActive && !isDraggingToSelect) {
                return false; // Let normal touch handling occur
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (isDraggingToSelect) {
                        int position = gridView.pointToPosition((int) event.getX(), (int) event.getY());
                        if (position != GridView.INVALID_POSITION && position != lastDragPosition) {
                            Object item = adapter.getItem(position);
                            if (item instanceof File) {
                                File file = (File) item;
                                Set<File> currentSelection = ((MainPresenter) presenter).getSelectedFiles();
                                boolean isSelected = currentSelection.contains(file);
                                // Select or deselect based on initial drag mode
                                if (dragSelectMode && !isSelected) {
                                    presenter.toggleSelection(file);
                                } else if (!dragSelectMode && isSelected) {
                                    presenter.toggleSelection(file);
                                }
                            }
                            lastDragPosition = position;
                        }
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDraggingToSelect = false;
                    lastDragPosition = -1;
                    // Dismiss preview popup if showing
                    if (previewPopup.isShowing()) {
                        previewPopup.dismiss();
                    }
                    break;
            }
            return false;
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
                startActivityForResult(intent, VIEWER_REQUEST_CODE);
            }
        });

        // Header (date/folder) tap → open section view with all files
        browseAdapter.setOnHeaderClickListener((title, paths) -> {
            Intent intent = new Intent(this, SectionViewActivity.class);
            intent.putExtra(SectionViewActivity.EXTRA_TITLE, title);
            intent.putExtra(SectionViewActivity.EXTRA_FILE_PATHS, paths);
            startActivityForResult(intent, SECTION_VIEW_REQUEST_CODE);
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
                // Protect (encrypt) files selected in browse - show album selection dialog
                Set<File> sel = browseAdapter.getSelectedFiles();
                if (!sel.isEmpty()) {
                    albumController.showEncryptToAlbumDialog(new ArrayList<>(sel));
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

        // Handle app shortcuts
        handleShortcutIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("shortcut_action");
        if ("protected".equals(action)) {
            switchNavTab(NavTab.PROTECTED);
        } else if ("browse".equals(action)) {
            switchNavTab(NavTab.ORIGINAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeHelper.isDarkMode(this) != appliedDark) recreate();

        // Check if we should lock due to timeout
        if (isAuthenticated && SecurityHelper.shouldLockDueToTimeout(this)) {
            isAuthenticated = false;
            checkLockScreen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Update last activity time for auto-lock
        if (isAuthenticated) {
            SecurityHelper.updateLastActivityTime(this);
        }
    }

    @Override
    protected void onDestroy() {
        // Note: ThumbnailLoader is a singleton, no need to clear cache on destroy.
        // The cache persists across activity recreations (e.g., theme change).
        if (previewPopup != null) {
            previewPopup.destroy();
        }
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // If viewing files in Protected tab (not album grid), go back to album view
        if (showEncrypted && !albumController.isInAlbumView()) {
            if (gridSelectionActive) {
                int count = ((MainPresenter) presenter).getSelectedFiles().size();
                if (count > 0) {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle(R.string.confirm_discard_selection_title)
                            .setMessage(getString(R.string.confirm_discard_selection_message, count))
                            .setPositiveButton(R.string.btn_discard, (dialog, which) -> {
                                presenter.deselectAll();
                                albumController.showAlbumView(allProtectedFiles);
                            })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .show();
                    return;
                }
            }
            albumController.showAlbumView(allProtectedFiles);
            return;
        }
        // If in selection mode, confirm before discarding selection
        if (gridSelectionActive && showEncrypted) {
            int count = ((MainPresenter) presenter).getSelectedFiles().size();
            if (count > 0) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.confirm_discard_selection_title)
                        .setMessage(getString(R.string.confirm_discard_selection_message, count))
                        .setPositiveButton(R.string.btn_discard, (dialog, which) -> {
                            presenter.deselectAll();
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show();
                return;
            }
        }
        // If in browse selection mode
        if (!showEncrypted && !browseAdapter.getSelectedFiles().isEmpty()) {
            int count = browseAdapter.getSelectedFiles().size();
            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_discard_selection_title)
                    .setMessage(getString(R.string.confirm_discard_selection_message, count))
                    .setPositiveButton(R.string.btn_discard, (dialog, which) -> {
                        browseAdapter.clearSelection();
                        selectionBar.setVisibility(View.GONE);
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    // ─────────────────────────────────────────────────────────────────────
    // MainContract.View
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void showFiles(List<File> files) {
        // Stop refresh indicators and skeleton
        pullToRefreshProtected.setRefreshing(false);
        pullToRefreshBrowse.setRefreshing(false);
        skeletonView.setVisibility(View.GONE);
        skeletonView.stopShimmer();

        if (showEncrypted) {
            // Protected mode: store all files
            allProtectedFiles = new ArrayList<>(files);
            if (albumController.isInAlbumView()) {
                albumController.buildAndShowAlbumGrid(allProtectedFiles);
            } else {
                filterProtectedFiles();
            }
            if (!files.isEmpty() && !albumController.isInAlbumView()) {
                Toast.makeText(this,
                        getString(R.string.toast_found_files, files.size()),
                        Toast.LENGTH_SHORT).show();
            }
            // Restore scroll position
            gridView.post(() -> {
                gridView.setSelectionFromTop(protectedScrollPosition, protectedScrollOffset);
            });
        } else {
            // Original (browse) mode: build grouped browse list
            browseProgressBar.setVisibility(View.GONE);
            List<FolderAdapter.BrowseItem> items =
                    (browseMode == BrowseMode.DATE)
                            ? BrowseListBuilder.buildDateItems(files)
                            : BrowseListBuilder.buildFolderItems(files);
            browseAdapter.setItems(items);
            showEmptyState(items.isEmpty(), R.string.label_no_files);
            // Restore scroll position
            browseListView.post(() -> {
                if (browseMode == BrowseMode.DATE) {
                    browseListView.setSelectionFromTop(browseDateScrollPosition, browseDateScrollOffset);
                } else {
                    browseListView.setSelectionFromTop(browseFolderScrollPosition, browseFolderScrollOffset);
                }
            });
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
        // Clear the filename display
        showEmptyState(false, "");
        Toast.makeText(this,
                getString(R.string.toast_operation_result, succeeded, failed),
                Toast.LENGTH_SHORT).show();
        if (showEncrypted) {
            // Protected mode: clear selection state and hide bar
            gridSelectionActive = false;
            selectionBar.setVisibility(View.GONE);
        } else {
            // Browse encrypt finished: clear selection state
            browseAdapter.clearSelection();
            selectionBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void showProgress(int done, int total, boolean encrypting, String currentFileName,
                             long bytesProcessed, long bytesTotal) {
        btnEncrypt.setEnabled(false);
        btnEncrypt.setText(getString(
                encrypting ? R.string.progress_encrypting : R.string.progress_decrypting,
                done, total));
        // Show current file being processed with byte progress
        if (currentFileName != null && total > 1) {
            // Truncate long filenames
            String displayName = currentFileName.length() > 25
                    ? currentFileName.substring(0, 22) + "..."
                    : currentFileName;
            // Format bytes progress
            String byteProgress = formatBytes(bytesProcessed) + " / " + formatBytes(bytesTotal);
            showEmptyState(true, displayName + "\n" + byteProgress);
        }
    }

    /**
     * Formats bytes to human-readable string (KB, MB, GB).
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void showExportResult(int succeeded, int failed, String folderName) {
        btnExport.setEnabled(true);
        // Clear the filename display
        showEmptyState(false, "");
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
    public void showExportProgress(int done, int total, String currentFileName,
                                   long bytesProcessed, long bytesTotal) {
        btnExport.setEnabled(false);
        btnExport.setText(getString(R.string.progress_exporting, done, total));
        // Show current file being exported with byte progress
        if (currentFileName != null && total > 1) {
            String displayName = currentFileName.length() > 25
                    ? currentFileName.substring(0, 22) + "..."
                    : currentFileName;
            // Format bytes progress
            String byteProgress = formatBytes(bytesProcessed) + " / " + formatBytes(bytesTotal);
            showEmptyState(true, displayName + "\n" + byteProgress);
        }
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

        // Show export and move buttons only in Protected mode
        btnExport.setVisibility(showEncrypted ? View.VISIBLE : View.GONE);
        dividerExport.setVisibility(showEncrypted ? View.VISIBLE : View.GONE);
        btnMoveToAlbum.setVisibility(showEncrypted ? View.VISIBLE : View.GONE);

        // Show selection count in toolbar
        if (enabled && count > 0) {
            tvSelectionCount.setVisibility(View.VISIBLE);
            tvSelectionCount.setText(getString(R.string.selection_count, count));
        } else {
            tvSelectionCount.setVisibility(View.GONE);
        }

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
                // Refresh UI and timestamp after successful authentication
                SecurityHelper.updateLastActivityTime(this);
                // Ensure the current tab is properly displayed
                switchNavTab(currentNavTab);
            } else {
                // User didn't authenticate - exit app
                finishAffinity();
            }
        } else if (requestCode == SECTION_VIEW_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // User selected files to protect from section view
                ArrayList<String> selectedPaths = data.getStringArrayListExtra("selected_files");
                if (selectedPaths != null && !selectedPaths.isEmpty()) {
                    List<File> filesToEncrypt = new ArrayList<>();
                    for (String path : selectedPaths) {
                        filesToEncrypt.add(new File(path));
                    }
                    presenter.encryptFiles(filesToEncrypt);
                }
            }
        } else if (requestCode == VIEWER_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Files were encrypted/decrypted in viewer
                ArrayList<String> processedFiles = data.getStringArrayListExtra(
                        MediaViewerActivity.EXTRA_PROCESSED_FILES);
                if (processedFiles != null && !processedFiles.isEmpty()) {
                    if (showEncrypted) {
                        // Protected mode: refresh to remove decrypted files
                        presenter.switchMode(true);
                    } else {
                        // Browse mode: remove processed files from current items (preserves scroll position)
                        removeProcessedFilesFromBrowse(new HashSet<>(processedFiles));
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Browse mode switching
    // ─────────────────────────────────────────────────────────────────────

    private void switchBrowseMode(BrowseMode newMode) {
        if (browseMode == newMode) return;

        // Save scroll position of current browse mode
        saveBrowseScrollPosition();

        browseMode = newMode;
        updateBrowseModeTabUI();
        // Rebuild from the adapter's current items (no re-scan needed)
        // We re-trigger the presenter scan to get fresh data for the new grouping.
        browseProgressBar.setVisibility(View.VISIBLE);
        browseAdapter.setItems(new ArrayList<>());
        presenter.switchMode(false); // same mode, triggers loadMedia() → showFiles()
        // Scroll position restored in showFiles after data loads
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

        // Save scroll position of current tab before switching
        saveCurrentScrollPosition();

        currentNavTab = tab;
        updateNavBarUI();

        // Hide all content first
        pullToRefreshProtected.setVisibility(View.GONE);
        pullToRefreshBrowse.setVisibility(View.GONE);
        browseModeBar.setVisibility(View.GONE);
        browseProgressBar.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        selectionBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        skeletonView.setVisibility(View.GONE);
        albumBar.setVisibility(View.GONE);
        albumBreadcrumbBar.setVisibility(View.GONE);
        albumGridView.setVisibility(View.GONE);
        showEmptyState(false, "");

        switch (tab) {
            case PROTECTED:
                showEncrypted = true;
                adapter.setShowEncrypted(true);
                browseAdapter.clearSelection();
                presenter.switchMode(true);
                albumController.showAlbumView(allProtectedFiles);
                break;

            case ORIGINAL:
                showEncrypted = false;
                adapter.setShowEncrypted(false);
                pullToRefreshBrowse.setVisibility(View.VISIBLE);
                browseModeBar.setVisibility(View.VISIBLE);
                browseProgressBar.setVisibility(View.VISIBLE);
                browseAdapter.setItems(new ArrayList<>());
                presenter.switchMode(false);
                // Restore scroll position with offset after data loads
                browseListView.post(() -> {
                    if (browseMode == BrowseMode.DATE) {
                        browseListView.setSelectionFromTop(
                                browseDateScrollPosition, browseDateScrollOffset);
                    } else {
                        browseListView.setSelectionFromTop(
                                browseFolderScrollPosition, browseFolderScrollOffset);
                    }
                });
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

        navProtectedLabel.setTextColor(currentNavTab == NavTab.PROTECTED ? active : inactive);
        navOriginalLabel.setTextColor(currentNavTab == NavTab.ORIGINAL ? active : inactive);
        navSettingsLabel.setTextColor(currentNavTab == NavTab.SETTINGS ? active : inactive);
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
        startActivityForResult(intent, VIEWER_REQUEST_CODE);
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

        // Auto-lock timeout row click
        autoLockRow.setOnClickListener(v -> showAutoLockTimeoutDialog());

        // Disguise mode switch — show guide first when enabling
        switchDisguiseMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingDisguiseSwitch) return;
            if (isChecked) {
                isUpdatingDisguiseSwitch = true;
                switchDisguiseMode.setChecked(false);
                isUpdatingDisguiseSwitch = false;
                showDisguiseGuideDialog();
            } else {
                DisguiseHelper.setDisguiseEnabled(this, false);
                DisguiseHelper.applyDisguise(this, DisguiseHelper.DisguiseType.NONE);
                Toast.makeText(this, R.string.toast_disguise_disabled, Toast.LENGTH_SHORT).show();
                refreshDisguiseSettingsUI();
            }
        });

        // Initial state
        refreshSecuritySettingsUI();
        refreshDisguiseSettingsUI();
    }

    private void refreshDisguiseSettingsUI() {
        isUpdatingDisguiseSwitch = true;
        switchDisguiseMode.setChecked(DisguiseHelper.isDisguiseEnabled(this));
        isUpdatingDisguiseSwitch = false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // AlbumController callback
    // ─────────────────────────────────────────────────────────────────────

    private AlbumController.Callback buildAlbumControllerCallback() {
        return new AlbumController.Callback() {
            @Override public void onOpenAlbum(File albumDir) { filterProtectedFiles(); }
            @Override public void onShowEmptyState(boolean empty, String msg) { showEmptyState(empty, msg); }
            @Override public void deselectAll() { presenter.deselectAll(); }
            @Override public void moveFilesToAlbum(List<File> files, File targetDir) {
                presenter.moveToAlbum(files, targetDir);
            }
            @Override public void encryptFiles(List<File> files) { presenter.encryptFiles(files); }
            @Override public void encryptFilesToAlbum(List<File> files, File albumDir) {
                presenter.encryptFilesToAlbum(files, albumDir);
            }
            @Override public void onAlbumDeleted() {
                presenter.switchMode(true);
                albumController.buildAndShowAlbumGrid(allProtectedFiles);
            }
            @Override public void clearBrowseSelection() { browseAdapter.clearSelection(); }
            @Override public void onRefreshAlbumGrid() {
                albumController.buildAndShowAlbumGrid(allProtectedFiles);
            }
        };
    }

    private void showDisguiseGuideDialog() {
        String message =
            "Your app icon and name will appear as \"Calculator\" in the launcher.\n\n" +
            "To open the real app:\n" +
            "Type any number whose ending matches your PIN, then press  =\n\n" +
            "Example: PIN is 1234\n" +
            "Type 1234 =  or  991234 =  to unlock";

        new AlertDialog.Builder(this)
                .setTitle("Calculator disguise")
                .setMessage(message)
                .setPositiveButton("Enable", (d, which) -> {
                    DisguiseHelper.setDisguiseEnabled(this, true);
                    DisguiseHelper.applyDisguise(this, DisguiseHelper.DisguiseType.CALCULATOR);
                    isUpdatingDisguiseSwitch = true;
                    switchDisguiseMode.setChecked(true);
                    isUpdatingDisguiseSwitch = false;
                    Toast.makeText(this,
                            getString(R.string.toast_disguise_enabled, "Calculator"),
                            Toast.LENGTH_SHORT).show();
                    refreshDisguiseSettingsUI();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showAutoLockTimeoutDialog() {
        String[] labels = {
            getString(R.string.auto_lock_never),
            getString(R.string.auto_lock_1_min),
            getString(R.string.auto_lock_5_min),
            getString(R.string.auto_lock_15_min),
            getString(R.string.auto_lock_30_min)
        };

        int currentTimeout = SecurityHelper.getAutoLockTimeout(this);
        int selectedIndex = 0;
        for (int i = 0; i < SecurityHelper.TIMEOUT_OPTIONS.length; i++) {
            if (SecurityHelper.TIMEOUT_OPTIONS[i] == currentTimeout) {
                selectedIndex = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_auto_lock)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    SecurityHelper.setAutoLockTimeout(this, SecurityHelper.TIMEOUT_OPTIONS[which]);
                    tvAutoLockValue.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sort dialog
    // ─────────────────────────────────────────────────────────────────────

    private void showSortDialog() {
        String[] labels = {
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_date_desc),
            getString(R.string.sort_date_asc),
            getString(R.string.sort_size_asc),
            getString(R.string.sort_size_desc)
        };

        SortOption[] options = {
            SortOption.NAME_ASC,
            SortOption.NAME_DESC,
            SortOption.DATE_DESC,
            SortOption.DATE_ASC,
            SortOption.SIZE_ASC,
            SortOption.SIZE_DESC
        };

        int selectedIndex = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == currentSortOption) {
                selectedIndex = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.btn_sort)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    currentSortOption = options[which];
                    sortAndDisplayFiles();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void sortAndDisplayFiles() {
        filterProtectedFiles();
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

        // Show/hide auto-lock timeout option
        if (pinEnabled) {
            autoLockRow.setVisibility(View.VISIBLE);
            autoLockDivider.setVisibility(View.VISIBLE);
            tvAutoLockValue.setText(SecurityHelper.getTimeoutLabel(
                    SecurityHelper.getAutoLockTimeout(this)));
        } else {
            autoLockRow.setVisibility(View.GONE);
            autoLockDivider.setVisibility(View.GONE);
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

    // ─────────────────────────────────────────────────────────────────────
    // Scroll position preservation
    // ─────────────────────────────────────────────────────────────────────

    private void saveCurrentScrollPosition() {
        switch (currentNavTab) {
            case PROTECTED:
                protectedScrollPosition = gridView.getFirstVisiblePosition();
                View protectedChild = gridView.getChildAt(0);
                protectedScrollOffset = (protectedChild == null) ? 0 : protectedChild.getTop();
                break;
            case ORIGINAL:
                saveBrowseScrollPosition();
                break;
            case SETTINGS:
                // No scroll to save for settings
                break;
        }
    }

    /** Applies album, search, and sort filters then updates the grid adapter. */
    private void filterProtectedFiles() {
        List<File> result = MediaFilter.apply(
                allProtectedFiles, albumController.getCurrentAlbumDir(), currentSearchQuery, currentSortOption);
        adapter.setFiles(result);
        boolean empty = result.isEmpty();
        showEmptyState(empty, currentSearchQuery.isEmpty()
                ? R.string.label_no_files : R.string.search_no_results);
    }

    private void saveBrowseScrollPosition() {
        int position = browseListView.getFirstVisiblePosition();
        View child = browseListView.getChildAt(0);
        int offset = (child == null) ? 0 : child.getTop();

        if (browseMode == BrowseMode.DATE) {
            browseDateScrollPosition = position;
            browseDateScrollOffset = offset;
        } else {
            browseFolderScrollPosition = position;
            browseFolderScrollOffset = offset;
        }
    }

    /**
     * Shows or hides the empty state with proper icon and message.
     */
    private void showEmptyState(boolean show, int messageResId) {
        if (show) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            tvEmpty.setText(messageResId);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(boolean show, String message) {
        if (show) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Browse list helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Removes processed files from the browse adapter items without resetting scroll position.
     */
    private void removeProcessedFilesFromBrowse(Set<String> processedPaths) {
        int count = browseAdapter.getCount();
        List<FolderAdapter.BrowseItem> updatedItems = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            FolderAdapter.BrowseItem item = (FolderAdapter.BrowseItem) browseAdapter.getItem(i);
            if (item.type == FolderAdapter.TYPE_MEDIA_STRIP && item.files != null) {
                // Filter out processed files from this strip
                List<File> remainingFiles = new ArrayList<>();
                List<String> remainingPaths = new ArrayList<>();
                for (int j = 0; j < item.files.length; j++) {
                    String path = item.files[j].getAbsolutePath();
                    if (!processedPaths.contains(path)) {
                        remainingFiles.add(item.files[j]);
                        remainingPaths.add(path);
                    }
                }
                // Only add strip if it still has files
                if (!remainingFiles.isEmpty()) {
                    item.files = remainingFiles.toArray(new File[0]);
                    item.paths = remainingPaths.toArray(new String[0]);
                    updatedItems.add(item);
                } else {
                    // Remove the preceding header too (date or folder)
                    if (!updatedItems.isEmpty()) {
                        FolderAdapter.BrowseItem lastItem = updatedItems.get(updatedItems.size() - 1);
                        if (lastItem.type == FolderAdapter.TYPE_DATE_HEADER ||
                                lastItem.type == FolderAdapter.TYPE_FOLDER_HEADER) {
                            updatedItems.remove(updatedItems.size() - 1);
                        }
                    }
                }
            } else {
                // Header item - add it (will be removed if its strip becomes empty)
                updatedItems.add(item);
            }
        }

        // Update header subtitles with new counts
        for (int i = 0; i < updatedItems.size() - 1; i++) {
            FolderAdapter.BrowseItem item = updatedItems.get(i);
            if ((item.type == FolderAdapter.TYPE_DATE_HEADER ||
                    item.type == FolderAdapter.TYPE_FOLDER_HEADER) &&
                    i + 1 < updatedItems.size()) {
                FolderAdapter.BrowseItem nextItem = updatedItems.get(i + 1);
                if (nextItem.type == FolderAdapter.TYPE_MEDIA_STRIP && nextItem.files != null) {
                    int fileCount = nextItem.files.length;
                    item.subtitle = fileCount + " " + (fileCount == 1 ? "item" : "items");
                    item.paths = nextItem.paths;
                }
            }
        }

        browseAdapter.setItems(updatedItems);
        showEmptyState(updatedItems.isEmpty(), R.string.label_no_files);
    }
}
