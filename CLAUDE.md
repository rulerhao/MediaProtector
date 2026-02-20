# CLAUDE.md

## Build

```bash
./gradlew :app:assembleDebug           # usual dev build
./gradlew assembleRelease              # ProGuard-minified release APK
./gradlew clean                        # wipe outputs
./gradlew :media-crypto:assembleRelease
```
No test suite.

---

## Modules & SDK

| Module | minSdk | targetSdk | notes |
|---|---|---|---|
| `app/` | 26 | 34 | main app |
| `media-crypto/` | 24 | 34 | crypto library, no external deps |

Package: `com.rulerhao.media_protector`
Java: 1.8 source/target. Zero external library dependencies (Android SDK + Java 8 stdlib only).

---

## File Map

### app/ — activities & adapters

| File | Purpose |
|---|---|
| `MainActivity.java` | Launcher; grid of media, mode tabs (protected/original), selection bar, sort/browse/settings toolbar |
| `MediaViewerActivity.java` | Full-screen viewer; swipe navigation, overlay auto-hide (3 s), video playback, immersive system bars |
| `FolderBrowserActivity.java` | Folder picker; returns path via `RESULT_OK` Intent; optional "non-empty only" filter |
| `SettingsActivity.java` | Dark/light mode toggle switch; calls `ThemeHelper.toggleTheme()` + `recreate()` |
| `MediaAdapter.java` | GridView adapter; thumbnails via `ThumbnailLoader`, video badge, selection overlay, ViewHolder |
| `FolderAdapter.java` | ListView adapter for folder browser; ".." parent marker |

### app/ — ui (MVP)

| File | Purpose |
|---|---|
| `ui/MainContract.java` | View & Presenter interfaces; callbacks: `showFiles`, `showError`, `showProgress`, `showOperationResult`, `updateSelectionMode`, `updateMode`, `requestStoragePermission`, `requestManageAllFilesPermission` |
| `ui/MainPresenter.java` | Presenter; `WeakReference<View>`; selection set; `volatile boolean destroyed`; `Handler(mainLooper)` for main-thread delivery |
| `ui/SortOption.java` | Enum: `NAME_ASC`, `NAME_DESC`, `DATE_ASC`, `DATE_DESC` |

### app/ — data

| File | Purpose |
|---|---|
| `data/MediaRepository.java` | Single-thread `scanExecutor`; raw `Thread` for encrypt/decrypt; `traverse()` depth-limited to 3; `destroy()` shuts executor |
| `data/FileConfig.java` | **Source of truth** for extensions (`.jpg .jpeg .png .mp4`) and `.mprot` marker |

### app/ — util

| File | Purpose |
|---|---|
| `util/ThemeHelper.java` | SharedPreferences (`theme_prefs` / `dark_mode`); default = dark; `applyTheme(Activity)`, `isDarkMode(Context)`, `toggleTheme(Context)` |
| `util/ThumbnailLoader.java` | 4-thread pool; LRU cache 30 bitmaps; `ImageView.setTag` stale-guard; `inSampleSize=4`; catches `Exception` (not just `IOException`) for MIUI `RuntimeException` from `MediaMetadataRetriever` |
| `util/EncryptedMediaDataSource.java` | `MediaDataSource` for encrypted video; decrypted 1 KB header in memory; remaining bytes via `RandomAccessFile`; skips 16-byte nonce |

### media-crypto/

| File | Purpose |
|---|---|
| `crypto/HeaderObfuscator.java` | AES-128-CTR on first 1 KB; **hardcoded key** (obfuscation only); public API: `encrypt(File,File)`, `decrypt(File,File)`, `getDecryptedStream(File)` → `DecryptingInputStream`; statics: `getObfuscatedFile()`, `getOriginalName()`, `isObfuscated()` |

### Layouts

| File | Key views |
|---|---|
| `activity_main.xml` | `gridView`, `btnModeProtected/Original`, `indicatorProtected/Original`, `selectionBar`, `btnSelectAll`, `btnEncrypt`, `tvEmpty`, `btnSort`, `btnBrowseFolder`, `btnSettings` |
| `activity_media_viewer.xml` | `imageView`, `surfaceView`, `viewerTopBar`, `tvFilename`, `btnBack`, `videoControls`, `btnPlayPause`, `seekBar`, `tvDuration`, `progressBar`, `tvError` |
| `activity_folder_browser.xml` | Path display, `ListView`, media-filter checkbox, select-folder button |
| `activity_settings.xml` | Back button, `switchDarkMode` |
| `item_media.xml` | Thumbnail `ImageView`, filename `TextView`, video badge, selection overlay |
| `item_folder.xml` | Folder icon, name `TextView` |

### Resources

| File | Content |
|---|---|
| `values/attrs.xml` | Custom color attrs: `colorToolbar`, `colorToolbarText`, `colorToolbarIcon`, `colorSurface`, `colorDivider`, `colorThumbPlaceholder` |
| `values/themes.xml` | `Theme.Minimal.Dark` (default) + `Theme.Minimal.Light`; `Theme.Minimal` aliases Dark |
| `values/colors.xml` | Static/shared colors only: `tab_indicator`, `tab_unselected`, `white`, `black`, `action_encrypt`, `selection_overlay`, `badge_bg`, `primary_red`, `selection_red` |
| `values/strings.xml` | All UI strings |

---

## Cryptography

File format: `[16-byte random nonce][AES-128-CTR ciphertext of first 1 KB][rest of file verbatim]`
Encrypted file is original name + `.mprot` extension. File grows by 16 bytes.

---

## Theming Pattern

Every activity must call in `onCreate()` **before** `setContentView()`:
```java
ThemeHelper.applyTheme(this);   // sets theme from SharedPreferences
setContentView(R.layout.…);
```
Store `appliedDark = ThemeHelper.isDarkMode(this)` in `onCreate()`, then in `onResume()` call `recreate()` if it changed. Use `?attr/colorToolbar`, `?attr/colorToolbarText`, etc. in layouts — never hardcode toolbar colors. Resolve attr at runtime with `TypedValue` when needed in code.

---

## Async / Threading

- **Scan**: `MediaRepository.scanExecutor` (single thread) → `ScanCallback.onScanComplete / onScanError` → main thread via `Handler` in `MainPresenter`
- **Crypto**: raw `Thread` → `OperationCallback.onProgress / onComplete` → same `Handler`, gated by `destroyed`
- **Thumbnails**: `ThumbnailLoader` 4-thread pool; stale-guard via `ImageView.setTag(file)`
- **Image decode in viewer**: `ioExecutor` (single thread); stale-guard via `final File target` captured before submit

---

## Known Pitfalls & Fixes

### MediaPlayer surface lifecycle (video→video navigation)
`MediaPlayer.release()` without `setDisplay(null)` leaves the codec connected to `BufferQueueProducer`; the next player's `prepareAsync()` throws `IllegalStateException` ("already connected").

**Required pattern** in `releasePlayer()`:
```java
try { mediaPlayer.setDisplay(null); } catch (IllegalStateException ignored) {}
try { mediaPlayer.stop(); }          catch (IllegalStateException ignored) {}
mediaPlayer.release();
mediaPlayer = null;
```
`setupVideo()` always forces `SurfaceView` GONE→VISIBLE to guarantee `surfaceCreated()` fires on a clean surface. `surfaceDestroyed()` posts `setVisibility(VISIBLE)` when `isVideoMode && mediaPlayer == null`.

### MIUI MediaMetadataRetriever crash
`setDataSource(MediaDataSource)` throws `RuntimeException` (status `0x80000000`) on MIUI — not `IOException` or `IllegalArgumentException`. Catch `Exception` in `ThumbnailLoader.decodeVideoFrame()`.

### Theme-change detection
`MainActivity.onResume()` calls `recreate()` when `ThemeHelper.isDarkMode()` differs from `appliedDark`. Same pattern needed in any activity that can return from `SettingsActivity`.
