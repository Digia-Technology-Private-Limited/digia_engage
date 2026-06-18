#!/usr/bin/env node
/* eslint-disable */
// Regenerates src/version.ts from package.json so the RN wrapper version
// reported to the native SDK always matches the published npm version.
//
// Runs automatically via the npm `version` lifecycle script (`npm version`),
// both locally and in the publish workflow.
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const pkg = require(path.join(root, 'package.json'));
const out = path.join(root, 'src', 'version.ts');

const contents =
  '// Generated code. Do not modify by hand.\n' +
  '// Kept in sync with package.json by scripts/sync-version.js (runs on `npm version`).\n' +
  `export const DIGIA_RN_SDK_VERSION = '${pkg.version}';\n`;

fs.writeFileSync(out, contents);
console.log(`[sync-version] src/version.ts -> ${pkg.version}`);
