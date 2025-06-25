const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

function activate(context) {
    checkForExistingDevTourFile();

    const startCmd = vscode.commands.registerCommand('devtour.start', () => {
        vscode.window.showInformationMessage('DevTour started!');
    });

    const addStepCmd = vscode.commands.registerCommand('devtour.addStep', () => {
        handleAddStep();
    });

    context.subscriptions.push(startCmd, addStepCmd);
}

function checkForExistingDevTourFile() {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders || workspaceFolders.length === 0) return;

    const projectPath = workspaceFolders[0].uri.fsPath;
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
        fs.mkdirSync(devtourDir);
    }

    let steps = [];
    if (fs.existsSync(devtourFile)) {
        try {
            steps = JSON.parse(fs.readFileSync(devtourFile, 'utf8'));
            if (!Array.isArray(steps)) steps = [];
        } catch (err) {
            steps = [];
        }
    }

    // Añadir campo 'order' automáticamente
    const order = steps.length + 1;
    step.order = order;

    steps.push(step);
    fs.writeFileSync(devtourFile, JSON.stringify(steps, null, 2));
    vscode.window.showInformationMessage(`DevTour step #${order} added at line ${step.line}`);
}

function deactivate() { }

module.exports = {
    activate,
    deactivate
};
