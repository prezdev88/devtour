const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

let devTourTreeDataProvider;
let devTourDecorationType;
let devTourStepsCache = new Map();
let devTourSession;
let devTourControlsProvider;

const DEVTOUR_CONTROL_COMMANDS = new Set([
    'devtour.startTour',
    'devtour.stopTour',
    'devtour.nextStep',
    'devtour.previousStep',
    'devtour.refreshSteps'
]);

function activate(context) {
    checkForExistingDevTourFile();

    devTourTreeDataProvider = new DevTourTreeDataProvider();
    devTourControlsProvider = new DevTourControlsProvider(context.extensionUri);
    devTourSession = new DevTourSession(devTourControlsProvider);

    const lightIcon = vscode.Uri.file(path.join(context.extensionPath, 'media', 'devtour-gutter-light.svg'));
    const darkIcon = vscode.Uri.file(path.join(context.extensionPath, 'media', 'devtour-gutter-dark.svg'));
    devTourDecorationType = vscode.window.createTextEditorDecorationType({
        gutterIconSize: 'contain',
        overviewRulerLane: vscode.OverviewRulerLane.Center,
        overviewRulerColor: new vscode.ThemeColor('editorInfo.foreground'),
        light: {
            gutterIconPath: lightIcon
        },
        dark: {
            gutterIconPath: darkIcon
        }
    });
    context.subscriptions.push(devTourDecorationType);

    const addStepCmd = vscode.commands.registerCommand('devtour.addStep', () => {
        handleAddStep();
    });

    const openStepCmd = vscode.commands.registerCommand('devtour.openStep', step => {
        openDevTourStep(step);
    });

    const refreshCmd = vscode.commands.registerCommand('devtour.refreshSteps', () => {
        devTourTreeDataProvider?.refresh();
        refreshDevTourDecorations();
        devTourSession?.reloadSteps();
    });

    const openConfigCmd = vscode.commands.registerCommand('devtour.openConfig', () => {
        openDevTourConfig();
    });

    const deleteStepCmd = vscode.commands.registerCommand('devtour.deleteStep', item => {
        deleteDevTourStep(item);
    });

    const startTourCmd = vscode.commands.registerCommand('devtour.startTour', () => {
        devTourSession?.start();
    });

    const nextStepCmd = vscode.commands.registerCommand('devtour.nextStep', () => {
        devTourSession?.next();
    });

    const previousStepCmd = vscode.commands.registerCommand('devtour.previousStep', () => {
        devTourSession?.previous();
    });

    const stopTourCmd = vscode.commands.registerCommand('devtour.stopTour', () => {
        devTourSession?.stop();
    });

    const createTourCmd = vscode.commands.registerCommand('devtour.createTour', () => {
        createDevTour();
    });

    const selectTourCmd = vscode.commands.registerCommand('devtour.selectTour', item => {
        if (item?.tourId) {
            setActiveTourById(item.tourId);
        } else {
            selectDevTour();
        }
    });

    const hoverProvider = vscode.languages.registerHoverProvider({ scheme: 'file' }, {
        provideHover(document, position) {
            return provideDevTourHover(document, position);
        }
    });

    const controlsViewRegistration = vscode.window.registerWebviewViewProvider(
        'devtourControlsView',
        devTourControlsProvider,
        { webviewOptions: { retainContextWhenHidden: true } }
    );

    context.subscriptions.push(
        addStepCmd,
        openStepCmd,
        refreshCmd,
        openConfigCmd,
        deleteStepCmd,
        startTourCmd,
        nextStepCmd,
        previousStepCmd,
        stopTourCmd,
        createTourCmd,
        selectTourCmd,
        hoverProvider,
        controlsViewRegistration,
        vscode.window.registerTreeDataProvider('devtourSteps', devTourTreeDataProvider),
        vscode.window.onDidChangeActiveTextEditor(() => refreshDevTourDecorations()),
        vscode.window.onDidChangeVisibleTextEditors(() => refreshDevTourDecorations())
    );

    registerDevTourWatcher(context);
    refreshDevTourDecorations();
}

function checkForExistingDevTourFile() {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) return;

    const devtourPath = path.join(projectPath, '.devtour', 'devtour.json');

    if (fs.existsSync(devtourPath)) {
        vscode.window.showInformationMessage(
            'DevTour configuration detected. Would you like to open it?',
            'Yes', 'No'
        ).then(selection => {
            if (selection === 'Yes') {
                const openPath = vscode.Uri.file(devtourPath);
                vscode.window.showTextDocument(openPath);
            }
        });
    }
}

async function handleAddStep() {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showWarningMessage('No active editor');
        return;
    }

    const filePath = editor.document.uri.fsPath;
    const workspaceFolder = vscode.workspace.getWorkspaceFolder(editor.document.uri);
    if (!workspaceFolder) {
        vscode.window.showWarningMessage('File is not inside the workspace');
        return;
    }

    const relativePath = path.relative(workspaceFolder.uri.fsPath, filePath);
    const line = editor.selection.active.line + 1;

    const description = await vscode.window.showInputBox({
        prompt: 'Enter a description for this DevTour step (optional)',
    });

    if (description === undefined) {
        vscode.window.showInformationMessage('DevTour step creation cancelled.');
        return;
    }

    const step = {
        file: relativePath,
        line,
        description: description || ''
    };

    const selectedTour = await promptForTourSelectionOrCreation(workspaceFolder.uri.fsPath);
    if (!selectedTour) {
        vscode.window.showInformationMessage('DevTour step creation cancelled.');
        return;
    }

    addStepToTour(workspaceFolder.uri.fsPath, step, selectedTour.id);
}

function addStepToTour(projectPath, step, targetTourId) {
    const devtourDir = path.join(projectPath, '.devtour');
    const devtourFile = path.join(devtourDir, 'devtour.json');

    if (!fs.existsSync(devtourDir)) {
        fs.mkdirSync(devtourDir, { recursive: true });
    }

    let data = loadDevTourData(projectPath);
    if (data.tours.length === 0) {
        const defaultTour = createTourObject('Main Tour');
        data.tours.push(defaultTour);
        data.activeTourId = defaultTour.id;
    }

    let targetTour = targetTourId ? findTourById(data, targetTourId) : getActiveTour(data);
    if (!targetTour) {
        vscode.window.showErrorMessage('No DevTour found. Please create a tour first.');
        return;
    }

    data.activeTourId = targetTour.id;

    const order = targetTour.steps.length + 1;
    const enrichedStep = {
        ...step,
        id: crypto.randomUUID(),
        order,
        tourId: targetTour.id
    };

    targetTour.steps.push(enrichedStep);
    saveDevTourData(devtourFile, data);
    vscode.window.showInformationMessage(`DevTour step #${order} added to "${targetTour.name}" at line ${step.line}`);
    devTourTreeDataProvider?.refresh();
    refreshDevTourDecorations();
    devTourSession?.reloadSteps();
}

function registerDevTourWatcher(context) {
    const workspaceFolder = getPrimaryWorkspaceFolder();
    if (!workspaceFolder) return;

    const pattern = new vscode.RelativePattern(workspaceFolder, '.devtour/devtour.json');
    const watcher = vscode.workspace.createFileSystemWatcher(pattern);

    context.subscriptions.push(
        watcher,
        watcher.onDidChange(() => {
            devTourTreeDataProvider?.refresh();
            refreshDevTourDecorations();
            devTourSession?.reloadSteps();
        }),
        watcher.onDidCreate(() => {
            devTourTreeDataProvider?.refresh();
            refreshDevTourDecorations();
            devTourSession?.reloadSteps();
        }),
        watcher.onDidDelete(() => {
            devTourTreeDataProvider?.refresh();
            refreshDevTourDecorations();
            devTourSession?.reloadSteps();
        })
    );
}

function openDevTourStep(step) {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const targetPath = path.join(projectPath, step.file);

    vscode.workspace.openTextDocument(targetPath).then(
        document => vscode.window.showTextDocument(document).then(editor => {
            const line = Math.max((step.line || 1) - 1, 0);
            const position = new vscode.Position(line, 0);
            const selection = new vscode.Selection(position, position);
            editor.selection = selection;
            editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenter);
        }),
        err => vscode.window.showErrorMessage(`Could not open DevTour step: ${err.message}`)
    );
}

function openDevTourConfig() {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const devtourDir = path.join(projectPath, '.devtour');
    const devtourFile = path.join(devtourDir, 'devtour.json');

    if (!fs.existsSync(devtourDir)) {
        fs.mkdirSync(devtourDir, { recursive: true });
    }

    if (!fs.existsSync(devtourFile)) {
        const defaultTour = createTourObject('Main Tour');
        const initialData = {
            tours: [defaultTour],
            activeTourId: defaultTour.id
        };
        saveDevTourData(devtourFile, initialData);
        devTourTreeDataProvider?.refresh();
        refreshDevTourDecorations();
        devTourSession?.reloadSteps();
    }

    vscode.workspace.openTextDocument(devtourFile).then(
        document => vscode.window.showTextDocument(document),
        err => {
            if (err && err.message) {
                vscode.window.showErrorMessage(`Could not open DevTour configuration: ${err.message}`);
            }
        }
    );
}

async function deleteDevTourStep(item) {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const step = item?.step ?? item;
    if (!step) {
        vscode.window.showWarningMessage('Could not determine which DevTour step to delete.');
        return;
    }

    const fileName = step.file ? path.basename(step.file) : 'Unknown file';
    const lineLabel = typeof step.line === 'number' ? `:${step.line}` : '';
    const confirm = await vscode.window.showWarningMessage(
        `Remove step ${fileName}${lineLabel}?`,
        { modal: true },
        'Remove'
    );

    if (confirm !== 'Remove') return;

    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    const data = loadDevTourData(projectPath);
    const tourId = item?.tourId || step.tourId || data.activeTourId;
    const targetTour = findTourById(data, tourId) || findTourContainingStep(data, step);

    if (!targetTour) {
        vscode.window.showWarningMessage('Step not found in DevTour configuration.');
        return;
    }

    const filtered = targetTour.steps.filter(existing => !devTourStepsEqual(existing, step));
    if (filtered.length === targetTour.steps.length) {
        vscode.window.showWarningMessage('Step not found in DevTour configuration.');
        return;
    }

    targetTour.steps = filtered.map((existing, index) => ({
        ...existing,
        order: index + 1
    }));

    saveDevTourData(devtourFile, data);
    vscode.window.showInformationMessage('DevTour step removed.');
    devTourTreeDataProvider?.refresh();
    refreshDevTourDecorations();
    devTourSession?.reloadSteps();
}

function devTourStepsEqual(a, b) {
    if (!a || !b) return false;

    if (a.id && b.id) {
        return a.id === b.id;
    }

    if (a.tourId && b.tourId && a.tourId !== b.tourId) {
        return false;
    }

    return (
        a.file === b.file &&
        a.line === b.line &&
        (a.description || '') === (b.description || '')
    );
}

function getPrimaryWorkspaceFolder() {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders || workspaceFolders.length === 0) return undefined;
    return workspaceFolders[0];
}

function getWorkspaceFolderPath() {
    const workspaceFolder = getPrimaryWorkspaceFolder();
    return workspaceFolder ? workspaceFolder.uri.fsPath : undefined;
}

class DevTourTreeDataProvider {
    constructor() {
        this._onDidChangeTreeData = new vscode.EventEmitter();
        this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    }

    refresh() {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element) {
        return element;
    }

    getChildren(element) {
        const projectPath = getWorkspaceFolderPath();
        if (!projectPath) return [];

        const data = loadDevTourData(projectPath);

        if (!element) {
            return data.tours.map(tour => new DevTourTourItem(tour, data.activeTourId === tour.id));
        }

        if (element instanceof DevTourTourItem) {
            return element.tour.steps
                .slice()
                .sort((a, b) => {
                    const orderA = typeof a.order === 'number' ? a.order : Number.MAX_SAFE_INTEGER;
                    const orderB = typeof b.order === 'number' ? b.order : Number.MAX_SAFE_INTEGER;
                    if (orderA !== orderB) return orderA - orderB;

                    const fileA = a.file || '';
                    const fileB = b.file || '';
                    if (fileA !== fileB) return fileA.localeCompare(fileB);

                    const lineA = typeof a.line === 'number' ? a.line : Number.MAX_SAFE_INTEGER;
                    const lineB = typeof b.line === 'number' ? b.line : Number.MAX_SAFE_INTEGER;
                    return lineA - lineB;
                })
                .map(step => new DevTourTreeItem(step, projectPath, element.tour.id, element.isActive));
        }

        return [];
    }
}

class DevTourTourItem extends vscode.TreeItem {
    constructor(tour, isActive) {
        super(tour.name || 'Untitled Tour', vscode.TreeItemCollapsibleState.Expanded);
        this.tour = tour;
        this.isActive = isActive;
        this.contextValue = 'devTourTour';
        this.description = isActive ? 'Active tour' : undefined;
        this.iconPath = isActive ? new vscode.ThemeIcon('star-full') : new vscode.ThemeIcon('folder');
        this.tooltip = isActive ? `${tour.name} (active)` : tour.name;
        this.tourId = tour.id;
    }
}

class DevTourTreeItem extends vscode.TreeItem {
    constructor(step, projectPath, tourId, tourIsActive) {
        const fileName = step.file ? path.basename(step.file) : undefined;
        const label = fileName ? `${fileName}${step.line ? `:${step.line}` : ''}` : (step.line ? `Line ${step.line}` : 'Step');
        super(label, vscode.TreeItemCollapsibleState.None);

        this.step = step;
        this.tourId = tourId;
        const tooltipLines = [];
        if (step.file) {
            tooltipLines.push(`${step.file}${step.line ? `:${step.line}` : ''}`);
        } else if (step.line) {
            tooltipLines.push(`Line ${step.line}`);
        }
        if (step.description) {
            tooltipLines.push(`Description: ${step.description}`);
        }
        this.tooltip = tooltipLines.join('\n');
        this.description = step.description || undefined;
        this.command = {
            command: 'devtour.openStep',
            title: 'Open DevTour Step',
            arguments: [step]
        };
        this.resourceUri = step.file ? vscode.Uri.file(path.join(projectPath, step.file)) : undefined;
        this.iconPath = tourIsActive ? new vscode.ThemeIcon('debug-step-over') : new vscode.ThemeIcon('circle-large-outline');
        this.contextValue = 'devTourStep';
        this.buttons = [
            {
                command: 'devtour.deleteStep',
                tooltip: 'Delete this DevTour step',
                icon: new vscode.ThemeIcon('dash'),
                arguments: [this]
            }
        ];
    }
}

function createTourObject(name) {
    return {
        id: crypto.randomUUID(),
        name: name || 'Untitled Tour',
        steps: []
    };
}

function loadDevTourData(projectPath) {
    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    if (!fs.existsSync(devtourFile)) {
        return { tours: [], activeTourId: undefined };
    }

    try {
        const raw = JSON.parse(fs.readFileSync(devtourFile, 'utf8'));
        return normalizeDevTourData(raw);
    } catch (err) {
        return { tours: [], activeTourId: undefined };
    }
}

function normalizeDevTourData(raw) {
    if (Array.isArray(raw)) {
        const defaultTour = createTourObject('Main Tour');
        defaultTour.steps = raw.map((step, index) => ({
            ...step,
            id: step.id || crypto.randomUUID(),
            order: typeof step.order === 'number' ? step.order : index + 1,
            tourId: defaultTour.id
        }));
        return { tours: [defaultTour], activeTourId: defaultTour.id };
    }

    const tours = Array.isArray(raw?.tours) ? raw.tours : [];
    const normalizedTours = tours.map((tour, idx) => {
        const resolvedId = tour.id || crypto.randomUUID();
        return {
            id: resolvedId,
            name: tour.name || `Tour ${idx + 1}`,
            steps: Array.isArray(tour.steps)
                ? tour.steps.map((step, index) => ({
                    ...step,
                    id: step.id || crypto.randomUUID(),
                    order: typeof step.order === 'number' ? step.order : index + 1,
                    tourId: resolvedId
                }))
                : []
        };
    });

    const activeTourId = raw?.activeTourId && normalizedTours.some(t => t.id === raw.activeTourId)
        ? raw.activeTourId
        : (normalizedTours[0]?.id);

    return {
        tours: normalizedTours,
        activeTourId
    };
}

function saveDevTourData(devtourFile, data) {
    const dir = path.dirname(devtourFile);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    const serializable = {
        tours: data.tours.map(tour => ({
            id: tour.id,
            name: tour.name,
            steps: tour.steps.map(step => ({
                ...step,
                tourId: tour.id
            }))
        })),
        activeTourId: data.activeTourId
    };
    fs.writeFileSync(devtourFile, JSON.stringify(serializable, null, 2));
}

function getActiveTour(data) {
    if (!data || !data.tours) return undefined;
    let tour = data.tours.find(t => t.id === data.activeTourId);
    if (!tour) {
        tour = data.tours[0];
        if (tour) {
            data.activeTourId = tour.id;
        }
    }
    return tour;
}

function getAllDevTourSteps(projectPath) {
    const data = loadDevTourData(projectPath);
    const steps = [];
    data.tours.forEach(tour => {
        tour.steps.forEach(step => {
            steps.push({
                ...step,
                tourId: tour.id
            });
        });
    });
    return steps;
}

function findTourById(data, tourId) {
    if (!tourId) return undefined;
    return data.tours.find(tour => tour.id === tourId);
}

function findTourContainingStep(data, targetStep) {
    return data.tours.find(tour => tour.steps.some(step => devTourStepsEqual(step, targetStep)));
}

class DevTourControlsProvider {
    constructor(extensionUri) {
        this.extensionUri = extensionUri;
        this.webviewView = undefined;
        this.state = {
            active: false,
            index: -1,
            total: 0,
            hasSteps: false,
            description: '',
            tourName: 'No tour selected'
        };
    }

    resolveWebviewView(webviewView) {
        this.webviewView = webviewView;
        const webview = webviewView.webview;
        webview.options = {
            enableScripts: true,
            localResourceRoots: [this.extensionUri]
        };
        webview.html = getControlsViewHtml(webview);
        webview.onDidReceiveMessage(message => {
            if (!message || typeof message.command !== 'string') return;
            if (!DEVTOUR_CONTROL_COMMANDS.has(message.command)) return;
            vscode.commands.executeCommand(message.command);
        });
        this.pushState();
    }

    updateState(partial) {
        this.state = {
            ...this.state,
            ...partial
        };
        this.pushState();
    }

    pushState() {
        if (!this.webviewView) return;
        this.webviewView.webview.postMessage({ type: 'state', state: this.state });
    }
}

function getControlsViewHtml(webview) {
    const nonce = Date.now().toString();
    return /* html */`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource} https: data:; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
    <style>
        :root { color-scheme: light dark; }
        body {
            margin: 0;
            padding: 8px;
            font-family: var(--vscode-font-family);
            color: var(--vscode-foreground);
            background: transparent;
        }
        .wrapper {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .toolbar {
            display: flex;
            gap: 6px;
            align-items: center;
            background: var(--vscode-editorWidget-background);
            border: 1px solid var(--vscode-editorWidget-border);
            border-radius: 8px;
            padding: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.25);
        }
        button {
            border: none;
            border-radius: 5px;
            width: 32px;
            height: 32px;
            background: var(--vscode-button-secondaryBackground);
            color: var(--vscode-button-foreground);
            font-size: 16px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.1s ease, background 0.1s ease, opacity 0.1s ease;
        }
        button.accent {
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
        }
        button:hover:not(:disabled) {
            background: var(--vscode-button-hoverBackground);
            transform: translateY(-1px);
        }
        button:disabled {
            opacity: 0.4;
            cursor: default;
        }
        .status {
            font-size: 12px;
            line-height: 1.4;
            padding: 4px 6px;
            border-radius: 6px;
            background: var(--vscode-editorWidget-background);
            border: 1px solid var(--vscode-editorWidget-border);
        }
        .status-title {
            font-weight: 600;
        }
        .status-description {
            color: var(--vscode-descriptionForeground);
        }
        .status-tour {
            color: var(--vscode-descriptionForeground);
            font-size: 11px;
        }
    </style>
</head>
<body>
    <div class="wrapper">
        <div class="toolbar">
            <button class="accent" data-command="devtour.startTour" title="Start / Restart (Shift+Alt+Down)">▶</button>
            <button data-command="devtour.previousStep" title="Previous (Shift+Alt+Up)">↑</button>
            <button data-command="devtour.nextStep" title="Next (Shift+Alt+Down)">↓</button>
            <button data-command="devtour.refreshSteps" title="Reload steps">↺</button>
            <button data-command="devtour.stopTour" title="Stop (Shift+Alt+Backspace)">■</button>
        </div>
        <div class="status">
            <div class="status-title" id="devtour-statusLabel">DevTour idle</div>
            <div class="status-description" id="devtour-statusDescription">Add steps to begin.</div>
            <div class="status-tour" id="devtour-statusTour">Tour: none</div>
        </div>
    </div>
    <script nonce="${nonce}">
        const vscode = acquireVsCodeApi();
        const startBtn = document.querySelector('[data-command="devtour.startTour"]');
        const prevBtn = document.querySelector('[data-command="devtour.previousStep"]');
        const nextBtn = document.querySelector('[data-command="devtour.nextStep"]');
        const refreshBtn = document.querySelector('[data-command="devtour.refreshSteps"]');
        const stopBtn = document.querySelector('[data-command="devtour.stopTour"]');
        const statusLabel = document.getElementById('devtour-statusLabel');
        const statusDescription = document.getElementById('devtour-statusDescription');
        const statusTour = document.getElementById('devtour-statusTour');

        document.querySelectorAll('button[data-command]').forEach(button => {
            button.addEventListener('click', () => {
                const command = button.getAttribute('data-command');
                vscode.postMessage({ command });
            });
        });

        window.addEventListener('message', event => {
            const { type, state } = event.data || {};
            if (type !== 'state' || !state) return;

            const { active, index, total, hasSteps, description, tourName } = state;
            const stepLabel = active && total > 0 ? \`Step \${index + 1} / \${total}\` : 'DevTour idle';
            statusLabel.textContent = stepLabel;
            statusDescription.textContent = description || (hasSteps ? 'Press play to start.' : 'Add steps to begin.');
            statusTour.textContent = tourName ? \`Tour: \${tourName}\` : 'Tour: none';

            startBtn.disabled = !hasSteps;
            prevBtn.disabled = !(active && total > 0 && index > 0);
            nextBtn.disabled = !(active && total > 0 && index < total - 1);
            stopBtn.disabled = !active;
            refreshBtn.disabled = !hasSteps;
        });
    </script>
</body>
</html>
`;
}

function formatStepDescription(step) {
    if (!step) return '';
    if (step.description && step.description.trim().length > 0) {
        return step.description.trim();
    }
    if (step.file) {
        return `${path.basename(step.file)}:${step.line ?? ''}`;
    }
    if (typeof step.line === 'number') {
        return `Line ${step.line}`;
    }
    return 'DevTour step';
}

function refreshDevTourDecorations() {
    if (!devTourDecorationType) return;

    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        devTourStepsCache = new Map();
        clearDecorations();
        return;
    }

    const steps = getAllDevTourSteps(projectPath);
    const stepsByFile = new Map();
    steps.forEach(step => {
        if (!step.file) return;
        const key = normalizePath(path.join(projectPath, step.file));
        if (!stepsByFile.has(key)) {
            stepsByFile.set(key, []);
        }
        stepsByFile.get(key).push(step);
    });

    devTourStepsCache = stepsByFile;

    vscode.window.visibleTextEditors.forEach(editor => {
        applyDecorationsToEditor(editor, projectPath, stepsByFile);
    });
}

function clearDecorations() {
    if (!devTourDecorationType) return;
    vscode.window.visibleTextEditors.forEach(editor => {
        editor.setDecorations(devTourDecorationType, []);
    });
}

function applyDecorationsToEditor(editor, projectPath, stepsByFile) {
    if (!editor || editor.document.uri.scheme !== 'file') {
        if (editor) {
            editor.setDecorations(devTourDecorationType, []);
        }
        return;
    }

    const absolutePath = normalizePath(editor.document.uri.fsPath);
    const key = absolutePath;
    const steps = stepsByFile.get(key) || [];

    const decorations = steps.map(step => {
        const lineIndex = Math.max((step.line || 1) - 1, 0);
        const range = new vscode.Range(lineIndex, 0, lineIndex, 0);
        const hoverParts = [];
        if (typeof step.order === 'number') {
            hoverParts.push(`DevTour step #${step.order}`);
        } else {
            hoverParts.push('DevTour step');
        }
        if (step.description) {
            hoverParts.push(step.description);
        }
        hoverParts.push(`Line: ${step.line ?? 'unknown'}`);
        if (step.file) {
            hoverParts.push(step.file);
        }
        return {
            range,
            hoverMessage: hoverParts.join('\n')
        };
    });

    editor.setDecorations(devTourDecorationType, decorations);
}

function normalizePath(value) {
    if (!value) return '';
    return value.replace(/\\/g, '/');
}

function provideDevTourHover(document, position) {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) return undefined;

    const key = normalizePath(document.uri.fsPath);
    const steps = devTourStepsCache.get(key);
    if (!steps || steps.length === 0) return undefined;

    const step = steps.find(item => {
        const lineIndex = Math.max((item.line || 1) - 1, 0);
        return lineIndex === position.line;
    });

    if (!step) return undefined;

    const lines = [];
    if (typeof step.order === 'number') {
        lines.push(`**DevTour step #${step.order}**`);
    } else {
        lines.push('**DevTour step**');
    }
    if (step.description) {
        lines.push(step.description);
    }
    lines.push(`\`Line ${step.line ?? '?'}\``);
    if (step.file) {
        lines.push(`\`${step.file}\``);
    }

    return new vscode.Hover(lines.join('\n\n'));
}

class DevTourSession {
    constructor(controlsProvider) {
        this.controlsProvider = controlsProvider;
        this.steps = [];
        this.index = -1;
        this.active = false;
        this.currentTourName = '';
        this.loadSteps();
    }

    loadSteps() {
        const projectPath = getWorkspaceFolderPath();
        if (!projectPath) {
            this.steps = [];
            this.index = -1;
            this.active = false;
            this.currentTourName = '';
            this.notifyControls();
            return;
        }
        const data = loadDevTourData(projectPath);
        const activeTour = getActiveTour(data);
        this.steps = activeTour ? activeTour.steps.slice().sort((a, b) => (a.order ?? 0) - (b.order ?? 0)) : [];
        this.currentTourName = activeTour ? activeTour.name : '';
        this.index = -1;
        this.active = false;
        this.notifyControls();
    }

    reloadSteps() {
        const previousActive = this.active;
        const previousIndex = this.index;
        this.loadSteps();
        if (previousActive && this.steps.length > 0) {
            this.active = true;
            this.index = Math.min(Math.max(previousIndex, 0), this.steps.length - 1);
            this.notifyControls(this.steps[this.index]);
        } else if (previousActive && this.steps.length === 0) {
            this.stop(false);
        } else {
            this.notifyControls();
        }
    }

    start() {
        if (!this.steps || this.steps.length === 0) {
            vscode.window.showInformationMessage('No DevTour steps to start.');
            return;
        }
        this.active = true;
        this.index = 0;
        this.notifyControls(this.steps[this.index]);
        this.moveToCurrentStep();
        vscode.window.showInformationMessage('DevTour started. Use Shift+Alt+Down/Up to navigate.');
    }

    next() {
        if (!this.ensureActive()) return;
        if (this.index >= this.steps.length - 1) {
            vscode.window.showInformationMessage('End of DevTour.');
            return;
        }
        this.index += 1;
        this.moveToCurrentStep();
    }

    previous() {
        if (!this.ensureActive()) return;
        if (this.index <= 0) {
            vscode.window.showInformationMessage('Already at the first DevTour step.');
            return;
        }
        this.index -= 1;
        this.moveToCurrentStep();
    }

    ensureActive() {
        if (!this.steps || this.steps.length === 0) {
            vscode.window.showInformationMessage('No DevTour steps available.');
            return false;
        }
        if (!this.active) {
            this.start();
            return false;
        }
        return true;
    }

    stop(showMessage = true) {
        if (!this.active) {
            if (showMessage) {
                vscode.window.showInformationMessage('DevTour is not running.');
            }
            return;
        }

        this.active = false;
        this.index = -1;
        this.notifyControls();
        if (showMessage) {
            vscode.window.showInformationMessage('DevTour stopped.');
        }
    }

    moveToCurrentStep() {
        if (!this.steps || this.steps.length === 0 || this.index < 0 || this.index >= this.steps.length) {
            return;
        }
        const step = this.steps[this.index];
        openDevTourStep(step);

        const description = formatStepDescription(step);
        vscode.window.showInformationMessage(`DevTour ${this.index + 1}/${this.steps.length}: ${description}`);
        this.notifyControls(step);
    }

    notifyControls(step) {
        if (!this.controlsProvider) return;
        const payload = {
            active: this.active,
            index: this.index,
            total: this.steps.length,
            hasSteps: this.steps.length > 0,
            description: step ? formatStepDescription(step) : '',
            tourName: this.currentTourName || 'No tour selected'
        };
        this.controlsProvider.updateState(payload);
    }
}

function deactivate() { }

module.exports = {
    activate,
    deactivate
};
async function createDevTour() {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const data = loadDevTourData(projectPath);
    const defaultName = `Tour ${data.tours.length + 1}`;

    const name = await vscode.window.showInputBox({
        title: 'Create DevTour',
        prompt: 'Enter a name for the new tour',
        value: defaultName
    });

    if (!name) {
        vscode.window.showInformationMessage('DevTour creation cancelled.');
        return;
    }

    const newTour = createTourObject(name.trim());
    data.tours.push(newTour);
    data.activeTourId = newTour.id;

    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    saveDevTourData(devtourFile, data);
    vscode.window.showInformationMessage(`DevTour "${newTour.name}" created and set as active.`);
    devTourTreeDataProvider?.refresh();
    devTourSession?.reloadSteps();
}

async function selectDevTour() {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const data = loadDevTourData(projectPath);
    if (data.tours.length === 0) {
        vscode.window.showInformationMessage('No tours available. Create one first.');
        return;
    }

    const picks = data.tours.map(tour => ({
        label: tour.name || 'Untitled Tour',
        description: tour.id === data.activeTourId ? 'Active tour' : '',
        tourId: tour.id
    }));

    const selection = await vscode.window.showQuickPick(picks, {
        placeHolder: 'Select a DevTour to activate'
    });

    if (!selection?.tourId) return;

    setActiveTourById(selection.tourId);
}

function setActiveTourById(tourId) {
    const projectPath = getWorkspaceFolderPath();
    if (!projectPath) {
        vscode.window.showWarningMessage('No workspace open.');
        return;
    }

    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    const data = loadDevTourData(projectPath);
    const targetTour = findTourById(data, tourId);
    if (!targetTour) {
        vscode.window.showWarningMessage('Tour not found.');
        return;
    }

    data.activeTourId = targetTour.id;
    saveDevTourData(devtourFile, data);
    vscode.window.showInformationMessage(`DevTour "${targetTour.name}" is now active.`);
    devTourTreeDataProvider?.refresh();
    devTourSession?.reloadSteps();
}

async function promptForTourSelectionOrCreation(projectPath) {
    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    let data = loadDevTourData(projectPath);

    const createTourWithPrompt = async () => {
        const defaultName = `Tour ${data.tours.length + 1}`;
        const name = await vscode.window.showInputBox({
            title: 'Create DevTour',
            prompt: 'Enter a name for the new tour',
            value: defaultName
        });
        if (!name) return undefined;
        const newTour = createTourObject(name.trim());
        data.tours.push(newTour);
        data.activeTourId = newTour.id;
        saveDevTourData(devtourFile, data);
        vscode.window.showInformationMessage(`DevTour "${newTour.name}" created.`);
        devTourTreeDataProvider?.refresh();
        devTourSession?.reloadSteps();
        return newTour;
    };

    if (data.tours.length === 0) {
        return await createTourWithPrompt();
    }

    const picks = data.tours.map(tour => ({
        label: tour.name || 'Untitled Tour',
        description: `${tour.steps.length} step${tour.steps.length === 1 ? '' : 's'}` + (tour.id === data.activeTourId ? ' — Active' : ''),
        tour
    }));

    picks.push({
        label: '$(add) Create new tour',
        description: 'Start a new DevTour group',
        create: true
    });

    const selection = await vscode.window.showQuickPick(picks, {
        placeHolder: 'Select a tour (or create a new one) for this step'
    });

    if (!selection) return undefined;

    if (selection.create) {
        return await createTourWithPrompt();
    }

    if (data.activeTourId !== selection.tour.id) {
        data.activeTourId = selection.tour.id;
        saveDevTourData(devtourFile, data);
        devTourTreeDataProvider?.refresh();
        devTourSession?.reloadSteps();
    }

    return selection.tour;
}
