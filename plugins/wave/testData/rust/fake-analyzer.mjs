#!/usr/bin/env node
// Controlled stdio fixture for manual Rust LSP completion, definition, and diagnostics acceptance.
// It never starts Cargo, rustc, or a formatter. Use only in a disposable tiny workspace.
let input = Buffer.alloc(0);
let lastUri;

function send(message) {
  const body = Buffer.from(JSON.stringify(message), 'utf8');
  process.stdout.write(`Content-Length: ${body.length}\r\n\r\n`);
  process.stdout.write(body);
}

function receive(message) {
  const reply = result => send({jsonrpc: '2.0', id: message.id, result});
  switch (message.method) {
    case 'initialize':
      reply({capabilities: {textDocumentSync: 1, completionProvider: {}, definitionProvider: true},
        serverInfo: {name: 'WfP Rust fixture', version: '1'}});
      break;
    case 'textDocument/didOpen':
      lastUri = message.params.textDocument.uri;
      send({jsonrpc: '2.0', method: 'textDocument/publishDiagnostics', params: {uri: lastUri,
        diagnostics: [{range: {start: {line: 0, character: 0}, end: {line: 0, character: 2}},
          severity: 2, source: 'WfP fixture', message: 'Controlled fixture diagnostic'}]}});
      break;
    case 'textDocument/completion':
      reply([{label: 'fixture_symbol', kind: 3, insertText: 'fixture_symbol'}]);
      break;
    case 'textDocument/definition':
      reply({uri: lastUri, range: {start: {line: 0, character: 0}, end: {line: 0, character: 2}}});
      break;
    case 'shutdown': reply(null); break;
    case 'exit': process.exit(0); break;
    default: if (message.id !== undefined) reply(null);
  }
}

process.stdin.on('data', data => {
  input = Buffer.concat([input, data]);
  if (input.length > 4 * 1024 * 1024) process.exit(2);
  while (true) {
    const boundary = input.indexOf('\r\n\r\n');
    if (boundary < 0) break;
    const match = /Content-Length:\s*(\d+)/i.exec(input.subarray(0, boundary).toString('ascii'));
    if (!match) process.exit(2);
    const length = Number(match[1]);
    if (input.length < boundary + 4 + length) break;
    const message = JSON.parse(input.subarray(boundary + 4, boundary + 4 + length).toString('utf8'));
    input = input.subarray(boundary + 4 + length);
    receive(message);
  }
});
