---
name: add-feature
description: Add a new feature to MediaProtector following project patterns
allowed-tools:
  - Bash
  - Read
  - Write
  - Edit
  - Glob
  - Grep
---

Add a new feature to MediaProtector following established patterns.

## Project Architecture

- **MVP Pattern**: MainContract (View/Presenter interfaces), MainPresenter, MainActivity
- **Data Layer**: MediaRepository (single-thread executor for scanning, raw Thread for crypto)
- **Utilities**: ThemeHelper, ThumbnailLoader (singleton), SecurityHelper, OriginalPathStore

## Checklist for New Features

### 1. Activity
- [ ] Call `ThemeHelper.applyTheme(this)` before `setContentView()`
- [ ] Store `appliedDark = ThemeHelper.isDarkMode(this)` in `onCreate()`
- [ ] Check theme change in `onResume()` and `recreate()` if changed
- [ ] Register in AndroidManifest.xml

### 2. Strings
- [ ] Add to `res/values/strings.xml`
- [ ] Use `getString(R.string.xxx)` - never hardcode

### 3. Layouts
- [ ] Use `?attr/colorToolbar`, `?attr/colorToolbarText` for theme colors
- [ ] Never hardcode colors

### 4. Background Work
- [ ] Use ExecutorService or raw Thread
- [ ] Post results to main thread via Handler
- [ ] Guard callbacks with `volatile boolean destroyed`

### 5. Crypto Operations
- [ ] Use MediaRepository methods (encryptFiles, decryptFiles)
- [ ] Store original path via OriginalPathStore if encrypting

## File Locations

| Type | Location |
|------|----------|
| Activities | `app/src/main/java/com/rulerhao/media_protector/` |
| Data | `app/src/main/java/com/rulerhao/media_protector/data/` |
| UI contracts | `app/src/main/java/com/rulerhao/media_protector/ui/` |
| Utilities | `app/src/main/java/com/rulerhao/media_protector/util/` |
| Layouts | `app/src/main/res/layout/` |
| Drawables | `app/src/main/res/drawable/` |
| Strings | `app/src/main/res/values/strings.xml` |

## Supported Extensions

From FileConfig: `.jpg`, `.jpeg`, `.png`, `.mp4`
Encrypted extension: `.mprot`
