const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

let devTourTreeDataProvider;

function activate(context) {
    checkForExistingDevTourFile();

    devTourTreeDataProvider = new DevTourTreeDataProvider();

    const addStepCmd = vscode.commands.registerCommand('devtour.addStep', () => {
        handleAddStep();
    });

    const openStepCmd = vscode.commands.registerCommand('devtour.openStep', step => {
        openDevTourStep(step);
    });

    const refreshCmd = vscode.commands.registerCommand('devtour.refreshSteps', () => {
        devTourTreeDataProvider?.refresh();
    });

    const openConfigCmd = vscode.commands.registerCommand('devtour.openConfig', () => {
        openDevTourConfig();
    });

    const deleteStepCmd = vscode.commands.registerCommand('devtour.deleteStep', item => {
        deleteDevTourStep(item);
    });

    context.subscriptions.push(
        addStepCmd,
        openStepCmd,
        refreshCmd,
        openConfigCmd,
        deleteStepCmd,
        vscode.window.registerTreeDataProvider('devtourSteps', devTourTreeDataProvider)
    );

    registerDevTourWatcher(context);
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

    saveStepToFile(workspaceFolder.uri.fsPath, step);
}

function saveStepToFile(projectPath, step) {
    const devtourDir = path.join(projectPath, '.devtour');
    const devtourFile = path.join(devtourDir, 'devtour.json');

    if (!fs.existsSync(devtourDir)) {
        fs.mkdirSync(devtourDir, { recursive: true });
    }

    const steps = readDevTourSteps(projectPath);

    // Añadir campo 'order' automáticamente
    const order = steps.length + 1;
    step.order = order;

    steps.push(step);
    fs.writeFileSync(devtourFile, JSON.stringify(steps, null, 2));
    vscode.window.showInformationMessage(`DevTour step #${order} added at line ${step.line}`);
    devTourTreeDataProvider?.refresh();
}

function readDevTourSteps(projectPath) {
    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    if (!fs.existsSync(devtourFile)) {
        return [];
    }

    try {
        const data = fs.readFileSync(devtourFile, 'utf8');
        const parsed = JSON.parse(data);
        return Array.isArray(parsed) ? parsed : [];
    } catch (err) {
        return [];
    }
}

function registerDevTourWatcher(context) {
    const workspaceFolder = getPrimaryWorkspaceFolder();
    if (!workspaceFolder) return;

    const pattern = new vscode.RelativePattern(workspaceFolder, '.devtour/devtour.json');
    const watcher = vscode.workspace.createFileSystemWatcher(pattern);

    context.subscriptions.push(
        watcher,
        watcher.onDidChange(() => devTourTreeDataProvider?.refresh()),
        watcher.onDidCreate(() => devTourTreeDataProvider?.refresh()),
        watcher.onDidDelete(() => devTourTreeDataProvider?.refresh())
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
        fs.writeFileSync(devtourFile, JSON.stringify([], null, 2));
        devTourTreeDataProvider?.refresh();
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

    const steps = readDevTourSteps(projectPath);
    const remaining = steps.filter(existing => !devTourStepsEqual(existing, step));

    if (remaining.length === steps.length) {
        vscode.window.showWarningMessage('Step not found in DevTour configuration.');
        return;
    }

    const reordered = remaining.map((existing, index) => ({
        ...existing,
        order: index + 1
    }));

    const devtourFile = path.join(projectPath, '.devtour', 'devtour.json');
    fs.writeFileSync(devtourFile, JSON.stringify(reordered, null, 2));
    vscode.window.showInformationMessage('DevTour step removed.');
    devTourTreeDataProvider?.refresh();
}

function devTourStepsEqual(a, b) {
    if (!a || !b) return false;

    if (typeof a.order === 'number' && typeof b.order === 'number') {
        return a.order === b.order;
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
        if (element) {
            return [];
        }

        const projectPath = getWorkspaceFolderPath();
        if (!projectPath) return [];

        const steps = readDevTourSteps(projectPath);
        return steps
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
            .map(step => new DevTourTreeItem(step, projectPath));
    }
}

class DevTourTreeItem extends vscode.TreeItem {
    constructor(step, projectPath) {
        const fileName = step.file ? path.basename(step.file) : undefined;
        const label = fileName ? `${fileName}${step.line ? `:${step.line}` : ''}` : (step.line ? `Line ${step.line}` : 'Step');
        super(label, vscode.TreeItemCollapsibleState.None);

        this.step = step;
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
        this.iconPath = new vscode.ThemeIcon('debug-step-over');
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

function deactivate() { }

module.exports = {
    activate,
    deactivate
};
