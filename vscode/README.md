# DevTour

DevTour is a Visual Studio Code extension that helps you document and explore a project's logical flow without leaving the editor. Each step is stored in `.devtour/devtour.json`, and you can browse them from a dedicated sidebar view.

## Key features
- **DevTour Explorer panel**: Displays every recorded step in order, showing the file name, related line, and the optional description you add.
- **Quick access to the configuration file**: Open the DevTour JSON with a single click to edit it manually if needed.
- **Jump straight to the code**: Selecting a step focuses the corresponding file and line in the editor.
- **Keyboard shortcut**: Capture the current context without reaching for the mouse.
- **Tour groups**: Organize steps by endpoint, feature, or flow by creating multiple named tours and switching between them.
- **Interactive playback**: Start a tour, use the DevTour Controls view in the sidebar (play, next, previous, refresh, stop), and see each step’s description while you navigate.
- **Line decorations**: Each tracked line shows a DevTour marker in the gutter so you can spot steps directly in the editor.

## How to use
1. Open the project you want to document.
2. If needed, create a tour with **DevTour: Create Tour** (e.g., “User API”, “Checkout Flow”) and ensure it is selected.
3. Place the cursor on a relevant line and run **DevTour: Add DevTour Step** (context menu or `Ctrl+D Ctrl+A`). When prompted, pick an existing tour or create a new one for that step.
4. (Optional) Provide a description; it will be saved to `.devtour/devtour.json`.
5. Open the **DEVTOUR** panel in the Activity Bar to inspect tours and their steps. Click any item to jump to the code or use the context menu to switch the active tour.
6. Press **DevTour: Start DevTour Tour** (toolbar button or Command Palette) to enter guided mode. Use the **DevTour Controls** view at the top of the DevTour sidebar (or `Shift+Alt+Down` / `Shift+Alt+Up`) for next/previous steps.

## Available commands
- `devtour.createTour` – Creates a new named tour/group (e.g., “Authentication”).
- `devtour.selectTour` – Switches the active tour (also available from the tree view).
- `devtour.addStep` – Adds a step at the current file/line (default shortcut `Ctrl+D Ctrl+A`) after letting you choose/create the tour.
- `devtour.openStep` – Opens the file and focuses the line for the selected step.
- `devtour.startTour` – Begins a guided DevTour session (toolbar button and Command Palette).
- `devtour.nextStep` – Jumps to the next step (`Shift+Alt+Down`).
- `devtour.previousStep` – Returns to the previous step (`Shift+Alt+Up`).
- `devtour.stopTour` – Stops the interactive tour (`Shift+Alt+Backspace`) from the controls view or keyboard.
- `devtour.refreshSteps` – Reloads the steps list manually.
- `devtour.openConfig` – Opens (or creates) the `.devtour/devtour.json` file.

## Panel and menus
- **DevTour Explorer** in the Activity Bar lists the available tours first, then their ordered steps.
- Use the title bar actions (refresh/edit icons) to update the view or open the configuration file.
- The editor context menu shows “Devtour: Add DevTour Step” whenever the editor has focus.

## `devtour.json` structure
Each entry is stored as an object with the following shape:

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

You can edit the JSON manually; the view refreshes automatically when the file changes.

## Requirements
- VS Code 1.101.0 or later.

## Development
```bash
npm install
npm run lint
```

### Build a local VSIX (pre-release)
Use the VS Code packaging CLI to generate an installable `.vsix`:

```bash
npx @vscode/vsce package --pre-release
```

## Notes
- The extension only reads/writes inside the open workspace.
- If you delete `.devtour/devtour.json`, the view will remain empty until you add new steps.

Enjoy creating guided tours for your projects!
