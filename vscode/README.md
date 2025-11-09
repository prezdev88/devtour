# DevTour for VS Code

## Overview
DevTour is a Visual Studio Code extension that keeps a project’s logical flow documented inside the editor. Each checkpoint (file, line, optional description) lives in `.devtour/devtour.json`, which is shared with the IntelliJ plugin.

## Features
- DevTour Explorer panel listing tours and ordered steps.
- DevTour Controls webview (play, next, previous, refresh, stop).
- Line decorations in the gutter for every tracked step.
- Context-menu/shortcut capture (`Ctrl+D Ctrl+A`) to add steps quickly.
- Quick access to `.devtour/devtour.json` for manual editing.
- Multiple named tours per workspace with instant switching.

## Requirements
- VS Code `^1.101.0`.

## Run & build
```bash
cd vscode
npm install
npm run lint
npx @vscode/vsce package --pre-release   # produce devtour-<version>.vsix
```

## Quick usage
1. Create or select a tour (`DevTour: Create Tour` / `DevTour: Select Tour`).
2. Position the cursor and run `DevTour: Add DevTour Step` (`Ctrl+D Ctrl+A`) to save the current file/line (and optional description).
3. Open the **DevTour** view in the Activity Bar to inspect tours; click a step to jump to it or use the context menu to switch tours/delete steps.
4. Start guided playback with `DevTour: Start DevTour Tour`, then use the controls (`Shift+Alt+Down` / `Shift+Alt+Up`) to move forward/backward. Stop anytime with `Shift+Alt+Backspace`.

## DevTour JSON
```json
{
  "activeTourId": "tour-123",
  "tours": [
    {
      "id": "tour-123",
      "name": "Checkout Flow",
      "steps": [
        {
          "id": "step-1",
          "file": "src/api/checkout.ts",
          "line": 42,
          "description": "Explains how the checkout is initialized",
          "order": 1
        }
      ]
    }
  ]
}
```

The tree refreshes automatically whenever `.devtour/devtour.json` changes, so manual edits are safe.

## Notes
- All reads/writes happen inside the current workspace.
- If `.devtour/devtour.json` is missing, the view prompts you to add a step or create a tour to bootstrap it.
