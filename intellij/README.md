# DevTour for IntelliJ

## Overview
This module mirrors the VS Code DevTour experience inside JetBrains IDEs: record steps in `.devtour/devtour.json`, add checkpoints from the editor, and play them back with keyboard shortcuts, a dedicated tool window, and gutter icons.

## Features
- Tool window listing every tour and its steps.
- Actions to create/select tours, add/delete steps, refresh data, and open the config file.
- Playback session (start / next / previous / stop) with highlighted lines plus gutter icons.
- Quick access to `.devtour/devtour.json` for manual edits.
- Automatic refresh when the JSON file changes on disk.

## Requirements
- JDK 17 (Gradle 8.10 is bundled via the wrapper).

## Run & build
```bash
cd intellij
./gradlew runIde          # launch IntelliJ with the plugin loaded
./gradlew runIde -q       # same, quieter logs
./gradlew buildPlugin     # produces build/distributions/DevTour-<version>.zip
```

`runIde` launches IntelliJ Community with DevTour preloaded, so you can test without installing anything globally.

## Quick usage
- `DevTour: Add Step` (`Ctrl+D Ctrl+A`): adds the current line, asking which tour to use (or create).
- `DevTour: Start/Next/Previous/Stop`: controls the active tour playback.
- `DevTour: Create/Select Tour`: manages the available tours.
- `DevTour: Open Config`: opens `.devtour/devtour.json`.
- `DevTour: Delete Step`: available from the panel on a selected step.

## Shared tour file
The `.devtour/devtour.json` format matches the VS Code extension, so both editors share the exact same tour data.
