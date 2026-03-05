---
name: release
description: Build release APK, analyze size, and prepare for distribution
allowed-tools:
  - Bash
  - Read
  - Glob
---

Build and analyze a release-ready APK for MediaProtector.

## Instructions

1. Clean and build release APK:
   ```bash
   ./gradlew clean assembleRelease
   ```

2. Analyze APK contents:
   ```bash
   unzip -l app/build/outputs/apk/release/app-release-unsigned.apk
   ```

3. Report:
   - Total APK size
   - DEX size (classes.dex)
   - Resources size (resources.arsc)
   - Number of files
   - Breakdown by component

4. Suggest optimizations if APK > 100KB

## Size Targets

- Target: < 100KB
- Warning: > 150KB
- Critical: > 200KB

This project uses:
- ProGuard/R8 with aggressive optimization
- Vector drawables only (no bitmaps)
- Zero external dependencies
