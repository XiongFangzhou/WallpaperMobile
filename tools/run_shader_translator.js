// Runs the bundled shader translator on a JSON list of requests (host check helper).
// Usage: node run_shader_translator.js <bundle.js> <requests.json> <results.json>
'use strict';
const fs = require('fs');
const vm = require('vm');

const [bundle, requests, results] = process.argv.slice(2);
const context = vm.createContext({ console });
vm.runInContext(fs.readFileSync(bundle, 'utf8'), context, { filename: bundle });
const output = JSON.parse(fs.readFileSync(requests, 'utf8')).map((request) => {
  try {
    return Object.assign({ ok: true }, JSON.parse(context.weTranslateShader(JSON.stringify(request))));
  } catch (error) {
    return { ok: false, error: String((error && error.message) || error) };
  }
});
fs.writeFileSync(results, JSON.stringify(output));
