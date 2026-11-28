// Runs assets/scene-script-runtime.js on an adapted scene's scripts.json (host check helper).
// Usage: node run_scene_scripts.js <runtime.js> <scripts.json> <plan.json> <out.json>
// plan: [{t, cursor:[x,y]|null, down, events:[{type,x,y,click}]}]; out: per step, the full state of every object that changed.
'use strict';
const fs = require('fs');
const vm = require('vm');
const [runtime, scripts, plan, out] = process.argv.slice(2);
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(runtime, 'utf8'), context, { filename: runtime });
const init = JSON.parse(context.wmInit(fs.readFileSync(scripts, 'utf8')));
const steps = JSON.parse(fs.readFileSync(plan, 'utf8'));
let previous = 0; const results = [];
for (const step of steps) {
  const frame = { runtime: step.t, frametime: step.t - previous, cursor: step.cursor || null, down: !!step.down, events: step.events || [] };
  if (step.user) frame.user = step.user;
  if (step.timeOfDay !== undefined) frame.timeOfDay = step.timeOfDay;
  previous = step.t;
  results.push(JSON.parse(context.wmFrame(JSON.stringify(frame))));
}
fs.writeFileSync(out, JSON.stringify({ init, results }));
