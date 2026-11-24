"""Checks the scene script runtime (android/assets/scene-script-runtime.js) with Node on a synthetic scene."""
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'android' / 'assets' / 'scene-script-runtime.js'
RUNNER = ROOT / 'tools' / 'run_scene_scripts.js'

FOLLOW = """'use strict';
import * as WEMath from 'WEMath';
export function update(value) { value.x = input.cursorWorldPosition.x; value.y = WEMath.mix(value.y, input.cursorWorldPosition.y, 1); return value; }
"""
PULSE = """'use strict';
export var scriptProperties = createScriptProperties().addSlider({ name: 'speed', value: 1 }).finish();
export function update(value) { return 0.5 + 0.5 * Math.sin(engine.runtime * scriptProperties.speed); }
"""
CLICK = """'use strict';
let shown = false;
export function init(value) { engine.setTimeout(() => { shared.timer = true; }, 1000); return value; }
export function cursorClick(event) { shown = !shown; thisScene.getLayer('panel').visible = shown; thisScene.getLayer('dust').stop(); }
export function update(value) { return value; }
"""
BROKEN = """'use strict';
export function update(value) { return value.nothing.here; }
"""
SCENE = {
    'width': 1000, 'height': 500, 'properties': {'flag': True},
    'objects': [
        {'id': 1, 'name': 'pet', 'origin': [100, 100, 0], 'scale': [1, 1, 1], 'angles': [0, 0, 0], 'size': [10, 10], 'alpha': 1, 'visible': True, 'kind': 'image',
         'scripts': {'origin': {'source': FOLLOW, 'properties': {}}}},
        {'id': 2, 'name': 'glow', 'origin': [500, 250, 0], 'size': [10, 10], 'alpha': 1, 'visible': True, 'kind': 'image',
         'scripts': {'alpha': {'source': PULSE, 'properties': {'speed': {'value': 2}}}}},
        {'id': 3, 'name': 'button', 'origin': [800, 400, 0], 'size': [100, 50], 'alpha': 1, 'visible': True, 'kind': 'image',
         'scripts': {'origin': {'source': CLICK, 'properties': {}}}},
        {'id': 4, 'name': 'panel', 'origin': [500, 250, 0], 'size': [200, 200], 'alpha': 1, 'visible': False, 'kind': 'image'},
        {'id': 5, 'name': 'dust', 'origin': [0, 0, 0], 'size': [0, 0], 'alpha': 1, 'visible': True, 'kind': 'particle'},
        {'id': 6, 'name': 'broken', 'origin': [0, 0, 0], 'size': [1, 1], 'alpha': 1, 'visible': True, 'kind': 'image',
         'scripts': {'scale': {'source': BROKEN, 'properties': {}}}},
    ],
}
PLAN = [
    {'t': 0, 'cursor': [300, 200]},
    {'t': 0.5, 'cursor': [320, 210], 'events': [{'type': 'move', 'x': 320, 'y': 210}]},
    {'t': 1.2, 'cursor': [800, 400], 'down': True, 'events': [{'type': 'down', 'x': 800, 'y': 400}]},
    {'t': 1.3, 'cursor': [800, 400], 'events': [{'type': 'up', 'x': 800, 'y': 400, 'click': True}]},
    {'t': 1.4, 'cursor': [10, 10], 'events': [{'type': 'up', 'x': 10, 'y': 10, 'click': True}]},
]


def main():
    node = shutil.which('node')
    if node is None:
        print('SKIP: Node is not installed; scene script runtime not checked')
        return
    with tempfile.TemporaryDirectory(dir=ROOT / '.cache') as work:
        work = pathlib.Path(work)
        (work / 'scene.json').write_text(json.dumps(SCENE), encoding='utf-8')
        (work / 'plan.json').write_text(json.dumps(PLAN), encoding='utf-8')
        subprocess.run([node, str(RUNNER), str(RUNTIME), str(work / 'scene.json'), str(work / 'plan.json'), str(work / 'out.json')], check=True)
        out = json.loads((work / 'out.json').read_text(encoding='utf-8'))
    frames = [r['layers'] for r in out['results']]
    state = {}
    history = []
    for frame in frames:
        for key, value in frame.items():
            state[key] = value
        history.append(json.loads(json.dumps(state)))
    failures = []
    def check(condition, message):
        if not condition:
            failures.append(message)
    check(out['init']['scripts'] == 4 and not out['init']['errors'], 'all scripts compile: %s' % out['init'])
    check(history[0]['1'][:2] == [300, 200] and history[1]['1'][:2] == [320, 210], 'origin follows the pointer through an imported WEMath')
    check(abs(history[1]['2'][9] - (0.5 + 0.5 * __import__('math').sin(0.5 * 2))) < 1e-3, 'saved script properties override slider defaults')
    check(history[0]['4'][10] == 0 and history[3]['4'][10] == 1, 'a click on a layer runs its cursorClick handler')
    check(history[4]['4'][10] == 1, 'clicks outside a layer do not reach it')
    check(history[3]['5'][11] == 0, 'scripts stop particle systems')
    errors = [e for r in out['results'] for e in r['errors']]
    check(any(e.startswith('broken.scale') for e in errors) and history[-1]['6'][3] == 1, 'a failing script is reported and leaves its property unchanged')
    if failures:
        for message in failures:
            print('FAIL:', message)
        sys.exit(1)
    print('PASS: scene script runtime (%d frames, pointer, properties, events, particles, error isolation)' % len(frames))


if __name__ == '__main__':
    main()
