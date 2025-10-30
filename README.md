# DevTour

DevTour is a Visual Studio Code extension that helps you document and explore a project's logical flow without leaving the editor. Each step is stored in `.devtour/devtour.json`, and you can browse them from a dedicated sidebar view.

## Key features
- **DevTour Explorer panel**: Displays every recorded step in order, showing the file name, related line, and the optional description you add.
- **Quick access to the configuration file**: Open the DevTour JSON with a single click to edit it manually if needed.
- **Jump straight to the code**: Selecting a step focuses the corresponding file and line in the editor.
- **Keyboard shortcut**: Capture the current context without reaching for the mouse.
- **Line decorations**: Each tracked line shows a DevTour marker in the gutter so you can spot steps directly in the editor.

## How to use
1. Open the project you want to document.
2. Place the cursor on a relevant line and run **DevTour: Add DevTour Step** (context menu or `Ctrl+D Ctrl+A`).
3. (Optional) Provide a description; it will be saved to `.devtour/devtour.json`.
4. Open the **DEVTOUR** panel in the Activity Bar to see the ordered list of steps. Click any item to jump to the code.

## Available commands
- `devtour.addStep` – Adds a step for the active file/line (default shortcut `Ctrl+D Ctrl+A`).
- `devtour.openStep` – Opens the file and focuses the line for the selected step.
- `devtour.refreshSteps` – Reloads the steps list manually.
- `devtour.openConfig` – Opens (or creates) the `.devtour/devtour.json` file.

## Panel and menus
- **DevTour Explorer** in the Activity Bar lists steps ordered by the `order` field.
- Use the title bar actions (refresh/edit icons) to update the view or open the configuration file.
- The editor context menu shows “Devtour: Add DevTour Step” whenever the editor has focus.

## `devtour.json` structure
Each entry is stored as an object with the following shape:

```json
{
  "file": "src/app.ts",
  "line": 42,
  "description": "Explains how the app is initialized",
  "order": 3
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

Use `npm test` to run the test suite provided by `@vscode/test-cli`.

## Notes
- The extension only reads/writes inside the open workspace.
- If you delete `.devtour/devtour.json`, the view will remain empty until you add new steps.

Enjoy creating guided tours for your projects!
