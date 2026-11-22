"""Compare production layer shaders with native Android Canvas fixtures in desktop ANGLE."""
import base64
import ctypes as C
import json
import pathlib
import re
import runpy

root = pathlib.Path(__file__).resolve().parents[1]
# Reuse the EGL setup and the existing video/cached-camera shader checks.
runtime = runpy.run_path(str(root / 'tools/check_wallpaper_gl.py'))
api, gl = runtime['api'], runtime['gl']
I, U, P, F = C.c_int, C.c_uint, C.c_void_p, C.c_float
source = (root / 'android/src/dev/workshopvideo/SceneGpu.java').read_text(encoding='utf-8')
codes = [json.loads('"' + re.search(r'String ' + name + r'="((?:[^"\\]|\\.)*)"', source).group(1) + '"') for name in ('VERTEX', 'FRAGMENT')]
result = runtime['probe'](codes)
assert result['linked'], result
program = result['program']
uniform = lambda name: runtime['uniform'](program, name.encode())
u3 = api(gl, 'glUniform3f', None, [I, F, F, F])
um3 = api(gl, 'glUniformMatrix3fv', None, [I, I, U, P])
clear_color = api(gl, 'glClearColor', None, [F, F, F, F])
clear = api(gl, 'glClear', None, [U])
enable = api(gl, 'glEnable', None, [U])
blend = api(gl, 'glBlendFunc', None, [U, U])
cases = json.loads((root / '.cache/android-native-graphics/scene-gpu-cases.json').read_text())
surface = runtime['createSurface'](runtime['display'], runtime['config'], (I * 5)(0x3057, 64, 0x3056, 128, 0x3038))
assert runtime['makeCurrent'](runtime['display'], surface, surface, runtime['context'])
runtime['use'](program)
quad = (F * 8)(0, 0, 1, 0, 0, 1, 1, 1)
point = runtime['attribute'](program, b'point')
runtime['enable'](point)
runtime['pointer'](point, 2, 0x1406, 0, 8, quad)
enable(0x0BE2)
blend(1, 0x0303)
runtime['ui'](uniform('image'), 0)
textures = []
for entry in cases[0]['textures']:
    texture = U()
    runtime['textureNew'](1, C.byref(texture))
    runtime['textureBind'](0x0DE1, texture.value)
    for key, value in [(0x2801, 0x2601), (0x2800, 0x2601), (0x2802, 0x812F), (0x2803, 0x812F)]:
        runtime['parameter'](0x0DE1, key, value)
    data = C.create_string_buffer(base64.b64decode(entry['pixels']))
    runtime['textureImage'](0x0DE1, 0, 0x1908, entry['width'], entry['height'], 0, 0x1908, 0x1401, data)
    textures.append(texture.value)
for case in cases:
    w, h = case['width'], case['height']
    runtime['viewport'](0, 0, w, h)
    clear_color(0, 0, 0, 1)
    clear(0x4000)
    rgb = case['colors']
    matrix = (F * 9)(rgb[0], rgb[4], rgb[8], rgb[1], rgb[5], rgb[9], rgb[2], rgb[6], rgb[10])
    um3(uniform('colors'), 1, 0, matrix)
    u3(uniform('colorBias'), rgb[3], rgb[7], rgb[11])
    for command in case['commands']:
        pose = command['pose']
        for name, values in [('origin', pose[:2]), ('axisX', pose[2:4]), ('axisY', pose[4:])]:
            runtime['u2'](uniform(name), *values)
        runtime['u1'](uniform('opacity'), command['opacity'])
        u3(uniform('reveal'), *command['reveal'])
        runtime['textureBind'](0x0DE1, textures[command['texture']])
        runtime['draw'](5, 0, 4)
    output = (C.c_ubyte * (w * h * 4))()
    runtime['pixels'](0, 0, w, h, 0x1908, 0x1401, output)
    expected = base64.b64decode(case['expected'])
    differing = 0
    total_error = 0
    for y in range(h):
        for x in range(w):
            a, b = ((h - y - 1) * w + x) * 4, (y * w + x) * 4
            errors = [abs(output[a + c] - expected[b + c]) for c in range(3)]
            differing += max(errors) > 8
            total_error += sum(errors)
    assert differing < w * h * .05, (case['page'], case['reveal'], case['colored'], differing, total_error / (w * h * 3))
assert runtime['glError']() == 0
print(f'PASS: {len(cases)} production layer-shader frames match Android native Canvas: paging, parallax, rotation, color, alpha and X-ray; desktop ANGLE, not phone FPS')
