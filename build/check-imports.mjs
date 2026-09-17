import {basename} from 'node:path';

// A deliberately conservative source index, not a Java/Kotlin type resolver.
// Resolve the outer class only: static members and nested types need a compiler.
export function importedClass(name) {
  if (!/^(com\.intellij\.|com\.jetbrains\.|org\.jetbrains\.(idea|plugins)\.)/.test(name)) return null;
  const parts = name.replaceAll('$', '.').split('.');
  const index = parts.findIndex(part => /^[A-Z][\w]*$/.test(part));
  return index < 0 ? null : parts.slice(0, index + 1).join('.');
}

export class ImportIndex {
  symbols = new Set();
  references = new Map();

  addSymbols(file, code, raw = code) {
    const packageName = code.match(/^\s*package\s+([\w.]+)/m)?.[1];
    if (packageName) {
      // Include secondary declarations, objects and type aliases. Nested names
      // may over-approximate the index; candidates must be confirmed by compilation.
      for (const match of code.matchAll(/\b(?:enum(?:\s+class)?|class|interface|object|record|typealias)\s+([A-Za-z_][\w]*)/g)) {
        // Copy the small name so V8 cannot retain the entire source string.
        this.symbols.add(Buffer.from(`${packageName}.${match[1]}`).toString());
      }
      if (file.endsWith('.kt')) {
        // Kotlin can import capitalized top-level/extension properties such as
        // Dispatchers.EDT. They are not missing classes.
        for (const match of code.matchAll(/\b(?:val|var|fun)\s+(?:<[^>\n]*>\s*)?(?:[^\s=(]+\.)?([A-Z][\w]*)/g)) {
          this.symbols.add(Buffer.from(`${packageName}.${match[1]}`).toString());
        }
        const stem = basename(file, '.kt');
        const facade = raw.match(/@file:(?:kotlin\.jvm\.)?JvmName\("([\w]+)"\)/)?.[1] ?? `${stem[0].toUpperCase()}${stem.slice(1)}Kt`;
        this.symbols.add(Buffer.from(`${packageName}.${facade}`).toString());
      }
    }
  }

  addImports(file, code) {
    for (const match of code.matchAll(/^[\t ]*import\s+(?:static\s+)?([\w.$*]+)/gm)) {
      const name = importedClass(match[1]);
      if (!name || this.symbols.has(name)) continue;
      let locations = this.references.get(name);
      if (!locations) this.references.set(name, locations = []);
      locations.push({file, line: code.slice(0, match.index).split('\n').length});
    }
  }

  missing() {
    return [...this.references].filter(([name]) => !this.symbols.has(name))
      .map(([name, locations]) => ({name, locations}))
      .sort((a, b) => b.locations.length - a.locations.length || a.name.localeCompare(b.name));
  }
}
