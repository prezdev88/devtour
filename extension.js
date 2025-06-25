const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

function activate(context) {
  const workspaceFolders = vscode.workspace.workspaceFolders;

  if (workspaceFolders && workspaceFolders.length > 0) {
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

  let disposable = vscode.commands.registerCommand('devtour.start', function () {
    vscode.window.showInformationMessage('DevTour started!');
  });

  context.subscriptions.push(disposable);
}

function deactivate() {}

module.exports = {
  activate,
  deactivate
};
