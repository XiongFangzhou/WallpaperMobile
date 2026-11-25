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
SETTINGS = """'use strict';
export var scriptProperties = createScriptProperties().addSlider({ name: 'size', value: 1 }).finish();
let notified = 0;
export function applyUserProperties(changed) { if (changed.glow !== undefined) notified = changed.glow ? 2 : 3; }
export function update(value) { return new Vec3(scriptProperties.size, notified, 1); }
"""
# A text layer script in the form of Wallpaper Engine's clock preset: update(value) returns the new text.
CLOCK = """'use strict';
export var scriptProperties = createScriptProperties().addText({ name: 'delimiter', value: ':' }).finish();
export function update(value) { thisLayer.pointsize = 40; return 'T' + scriptProperties.delimiter + Math.floor(engine.runtime); }
"""
# A camera object placed by script, as authors expose "camera position" sliders (fractions of the canvas).
CAMERA = """'use strict';
export var scriptProperties = createScriptProperties().addSlider({ name: 'x', value: 0 }).finish();
export function update(value) { value.x = scriptProperties.x * engine.canvasSize.x; value.y = 0; if (engine.runtime > 1) thisScene.bloomstrength = 3; return value; }
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
        {'id': 7, 'name': 'sized', 'origin': [0, 0, 0], 'size': [1, 1], 'alpha': 1, 'visible': True, 'kind': 'image',
         'scripts': {'scale': {'source': SETTINGS, 'properties': {'size': {'user': 'bigness', 'value': 1.5}}}}},
        {'id': 8, 'name': 'clock', 'origin': [500, 250, 0], 'size': [100, 40], 'alpha': 1, 'visible': True, 'kind': 'text', 'text': '12:34', 'pointsize': 32, 'color': [1, 0.5, 0],
         'scripts': {'text': {'source': CLOCK, 'properties': {'delimiter': {'value': '-'}}}}},
        {'id': 10, 'name': '', 'kind': 'camera', 'zoom': 1, 'origin': [0, 0, 0], 'size': [0, 0], 'alpha': 1, 'visible': True,
         'scripts': {'origin': {'source': CAMERA, 'properties': {'x': {'value': 0.1}}}}},
        {'id': 9, 'name': 'collar', 'parent': 1, 'origin': [10, 0, 0], 'scale': [1, 1, 1], 'angles': [0, 0, 0], 'size': [4, 4], 'alpha': 1, 'visible': True, 'kind': 'image'},
    ],
}
PLAN = [
    {'t': 0, 'cursor': [300, 200]},
    {'t': 0.5, 'cursor': [320, 210], 'events': [{'type': 'move', 'x': 320, 'y': 210}]},
    {'t': 1.2, 'cursor': [800, 400], 'down': True, 'events': [{'type': 'down', 'x': 800, 'y': 400}]},
    {'t': 1.3, 'cursor': [800, 400], 'events': [{'type': 'up', 'x': 800, 'y': 400, 'click': True}]},
    {'t': 1.4, 'cursor': [10, 10], 'events': [{'type': 'up', 'x': 10, 'y': 10, 'click': True}]},
    {'t': 1.5, 'cursor': [10, 10], 'user': {'bigness': 2.5, 'glow': True}},
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
    check(history[4]['7'][3] == 1.5 and history[5]['7'][3] == 2.5 and history[5]['7'][4] == 2, 'author settings update bound script parameters and reach applyUserProperties')
    check(out['init']['scripts'] == 7 and not out['init']['errors'], 'all scripts compile: %s' % out['init'])
    check(history[0]['1'][:2] == [300, 200] and history[1]['1'][:2] == [320, 210], 'origin follows the pointer through an imported WEMath')
    check(abs(history[1]['2'][9] - (0.5 + 0.5 * __import__('math').sin(0.5 * 2))) < 1e-3, 'saved script properties override slider defaults')
    check('4' not in history[0] and history[3]['4'][10] == 1, 'a click on a layer runs its cursorClick handler; untouched layers are not reported')
    check(history[4]['4'][10] == 1, 'clicks outside a layer do not reach it')
    check(history[3]['5'][11] == 0, 'scripts stop particle systems')
    check(history[0]['9'][12:14] == [310, 200] and history[1]['9'][12:14] == [330, 210], 'children follow the scripted origin of their parent in the reported world transform')
    cameras = [r.get('camera') for r in out['results']]
    check(cameras[0] == [100, 0, 1] and cameras[1] is None, 'a scripted camera object moves the scene camera, reported when it changes: %s' % cameras)
    blooms = [r.get('bloom') for r in out['results']]
    check(blooms[:2] == [None, None] and blooms[2] is not None and blooms[2][1] == 3 and blooms[3] is None, 'scripts changing thisScene bloom fields are reported once: %s' % blooms)
    texts = [r.get('texts', {}) for r in out['results']]
    check(texts[0].get('8') == ['T-0', 40, 1, 0.5, 0] and '8' not in texts[1] and texts[2].get('8', [''])[0] == 'T-1' and '8' not in texts[3], 'text scripts reach text layers (text, pointsize, color) only when they change: %s' % texts)
    errors = [e for r in out['results'] for e in r['errors']]
    check(any(e.startswith('broken.scale') for e in errors) and '6' not in history[-1], 'a failing script is reported and leaves its property unchanged')
    if failures:
        for message in failures:
            print('FAIL:', message)
        sys.exit(1)
    print('PASS: scene script runtime (%d frames, pointer, properties, events, particles, text layers, camera, bloom, error isolation)' % len(frames))


if __name__ == '__main__':
    main()
