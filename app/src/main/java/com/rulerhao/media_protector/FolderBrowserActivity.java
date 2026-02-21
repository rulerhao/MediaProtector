package com.rulerhao.media_protector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.rulerhao.media_protector.data.MediaRepository;
import com.rulerhao.media_protector.util.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Media browser with two display modes:
 *
 * <ul>
 *   <li><b>Date mode</b> — all media grouped by day ("FEBRUARY 19, 2026"),
 *       newest first. Tapping a media row opens {@link MediaViewerActivity}
 *       with all files from that day as the navigation list.</li>
 *   <li><b>Folder mode</b> — folders that have media as direct children,
 *       each followed by its media files. Tapping a folder header returns
 *       that folder path to the calling activity via {@code RESULT_OK}.</li>
 * </ul>
 *
 * <p>Both modes scan from external storage root. The scan runs once;
 * switching modes just rebuilds the grouped list from the same result.
 */
public class FolderBrowserActivity extends Activity {

    public static final String EXTRA_SELECTED_FOLDER = "selected_folder";
    public static final String EXTRA_SHOW_ENCRYPTED  = "show_encrypted";

    private enum BrowseMode { DATE, FOLDER }

    private BrowseMode    mode      = BrowseMode.FOLDER;
    private boolean       encrypted = true;

    private FolderAdapter adapter;
    private ProgressBar   progressBar;
    private TextView      tvEmpty;

    // Scan results — shared between modes; rebuilt into grouped lists without re-scanning.
    private List<File> allFiles = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;

    private MediaRepository repository;

    // Tab indicator views
    private View   indicatorDate;
    private View   indicatorFolder;
    private Button btnModeDate;
    private Button btnModeFolder;

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_folder_browser);

        ListView browseList = findViewById(R.id.browseList);
        progressBar         = findViewById(R.id.progressBar);
        tvEmpty             = findViewById(R.id.tvEmpty);
        btnModeDate         = findViewById(R.id.btnModeDate);
        btnModeFolder       = findViewById(R.id.btnModeFolder);
        indicatorDate       = findViewById(R.id.indicatorDate);
        indicatorFolder     = findViewById(R.id.indicatorFolder);

        encrypted  = getIntent().getBooleanExtra(EXTRA_SHOW_ENCRYPTED, true);
        repository = new MediaRepository();
        adapter    = new FolderAdapter(this, encrypted);
        browseList.setAdapter(adapter);

        // Tab clicks
        btnModeDate.setOnClickListener(v -> switchMode(BrowseMode.DATE));
        btnModeFolder.setOnClickListener(v -> switchMode(BrowseMode.FOLDER));

        // Item clicks (only folder headers are clickable at the list level)
        browseList.setOnItemClickListener((parent, view, position, id) -> {
            FolderAdapter.BrowseItem item =
                    (FolderAdapter.BrowseItem) adapter.getItem(position);
            if (item == null) return;

            if (item.type == FolderAdapter.TYPE_FOLDER_HEADER && item.folder != null) {
                // Folder mode: return the selected folder to the main activity.
                Intent result = new Intent();
                result.putExtra(EXTRA_SELECTED_FOLDER, item.folder.getAbsolutePath());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        // Thumbnail clicks inside strips open the viewer.
        adapter.setOnFileClickListener((paths, index) -> {
            Intent intent = new Intent(this, MediaViewerActivity.class);
            intent.putExtra(MediaViewerActivity.EXTRA_FILE_LIST, paths);
            intent.putExtra(MediaViewerActivity.EXTRA_FILE_INDEX, index);
            intent.putExtra(MediaViewerActivity.EXTRA_ENCRYPTED, encrypted);
            startActivity(intent);
        });

        updateTabUI();
        startScan();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        adapter.destroy();
        repository.destroy();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode switching
    // ─────────────────────────────────────────────────────────────────────

    private void switchMode(BrowseMode newMode) {
        if (mode == newMode) return;
        mode = newMode;
        updateTabUI();
        rebuildList();
    }

    private void updateTabUI() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        int active   = tv.data;
        int inactive = getColor(R.color.tab_unselected);

        btnModeDate.setTextColor(mode == BrowseMode.DATE ? active : inactive);
        btnModeFolder.setTextColor(mode == BrowseMode.FOLDER ? active : inactive);
        indicatorDate.setVisibility(mode == BrowseMode.DATE
                ? View.VISIBLE : View.INVISIBLE);
        indicatorFolder.setVisibility(mode == BrowseMode.FOLDER
                ? View.VISIBLE : View.INVISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scan
    // ─────────────────────────────────────────────────────────────────────

    private void startScan() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        adapter.setItems(new ArrayList<>());

        File root = Environment.getExternalStorageDirectory();
        MediaRepository.ScanCallback cb = new MediaRepository.ScanCallback() {
            @Override
            public void onScanComplete(List<File> files) {
                mainHandler.post(() -> {
                    if (destroyed) return;
                    progressBar.setVisibility(View.GONE);
                    allFiles = files;
                    rebuildList();
                });
            }
            @Override
            public void onScanError(Exception e) {
                mainHandler.post(() -> {
                    if (destroyed) return;
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        };

        if (encrypted) repository.scanFiles(root, cb);
        else           repository.scanUnencryptedFiles(root, cb);
    }

    // ─────────────────────────────────────────────────────────────────────
    // List building (runs on main thread; grouping is cheap CPU work)
    // ─────────────────────────────────────────────────────────────────────

    private void rebuildList() {
        if (allFiles == null) return;
        List<FolderAdapter.BrowseItem> items =
                (mode == BrowseMode.DATE) ? buildDateItems() : buildFolderItems();
        adapter.setItems(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<FolderAdapter.BrowseItem> buildDateItems() {
        return BrowseListBuilder.buildDateItems(allFiles);
    }

    private List<FolderAdapter.BrowseItem> buildFolderItems() {
        return BrowseListBuilder.buildFolderItems(allFiles);
    }
}
