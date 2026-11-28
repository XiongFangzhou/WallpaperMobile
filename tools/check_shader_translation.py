"""Translate every single-pass layer effect of the local workshop scenes with the bundled translator.

Optional corpus check: needs WVB_WORKSHOP_DIR (installed Wallpaper Engine workshop folder) and Node.
It proves the translator runs on real author shaders; GLSL compilation itself is checked on Android.
"""
import collections
import json
import os
import pathlib
import re
import shutil
import struct
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'android' / 'assets' / 'we-shader-translator.js'
ANNOTATION = re.compile(r'uniform\s+\w+\s+(\w+)\s*;\s*//\s*(\{[^\n]*\})')


class Package:
    def __init__(self, path):
        self.file = path.open('rb')
        number = lambda: struct.unpack('<I', self.file.read(4))[0]
        self.file.read(number()); count = number(); self.entries = {}
        for _ in range(count):
            name = self.file.read(number()).decode('utf-8', 'replace'); offset, size = number(), number()
            self.entries[name.replace('\\', '/')] = (offset, size)
        self.base = self.file.tell()

    def has(self, name):
        return name in self.entries or (ENGINE is not None and (ENGINE / name).is_file())

    def text(self, name):
        if name not in self.entries and ENGINE is not None and (ENGINE / name).is_file():
            # Wallpaper Engine's own assets fill in what the package lacks, as on the phone.
            return (ENGINE / name).read_text(encoding='utf-8-sig', errors='replace')
        offset, size = self.entries[name]; self.file.seek(self.base + offset)
        return self.file.read(size).decode('utf-8-sig', 'replace')

    def json(self, name):
        return json.loads(re.sub(r',(\s*[}\]])', r'\1', self.text(name)))


# WVB_ENGINE_ASSETS: Wallpaper Engine's assets folder (the phone downloads the same files with the user's licence).
ENGINE = pathlib.Path(os.environ['WVB_ENGINE_ASSETS']) if os.environ.get('WVB_ENGINE_ASSETS') else None


def engine_headers():
    """Original shader headers, used for #include names the package does not bundle."""
    if ENGINE is None or not (ENGINE / 'shaders').is_dir():
        return {}
    return {path.name: path.read_text(encoding='utf-8-sig', errors='replace') for path in (ENGINE / 'shaders').glob('*.h')}


def texture_combos(sources, bound):
    """Annotated texture slots with a "combo" switch that combo on when a texture is bound."""
    combos = {}
    for source in sources:
        for uniform, meta in ANNOTATION.findall(source):
            match = re.fullmatch(r'g_Texture(\d+)', uniform)
            if not match:
                continue
            try:
                combo = json.loads(meta).get('combo')
            except ValueError:
                continue
            if combo and int(match.group(1)) in bound:
                combos[combo] = 1
    return combos


def requests_for(folder):
    package = Package(next(folder.glob('*.pkg')))
    scene = package.json('scene.json')
    includes = engine_headers()
    includes.update({name[len('shaders/'):]: package.text(name) for name in package.entries if name.startswith('shaders/') and name.endswith('.h')})
    for layer in scene.get('objects', []):
        if not isinstance(layer.get('image'), str):
            continue
        for instance in layer.get('effects') or []:
            kind = str(instance.get('file', '')).replace('effects/', '').replace('/effect.json', '')
            try:
                effect = package.json(instance['file'])
                passes = effect.get('passes', [])
                declared = {fbo.get('name') for fbo in effect.get('fbos') or []}
                if not passes or any('command' in p or 'conditions' in p or 'compose' in p for p in passes):
                    yield kind, None, 'unsupported pass commands'
                    continue
                bound_names = {b.get('name') for p in passes for b in p.get('bind') or []} | {p.get('target') for p in passes if p.get('target')}
                if any(n and n != 'previous' and n not in declared for n in bound_names):
                    yield kind, None, 'reads the whole frame'
                    continue
                staged = []
                for index, item in enumerate(passes):
                    material = package.json(item['material'])['passes'][0]
                    shader = material['shader']
                    staged.append((index, material, package.text('shaders/' + shader + '.vert'), package.text('shaders/' + shader + '.frag')))
            except (KeyError, ValueError, IndexError) as error:
                yield kind, None, 'missing effect data: ' + str(error)
                continue
            for index, material, vertex, fragment in staged:
                overrides = instance.get('passes') or []
                override = overrides[index] if index < len(overrides) and isinstance(overrides[index], dict) else {}
                yield from one_pass(kind, material, vertex, fragment, override, includes)


def one_pass(kind, material, vertex, fragment, override, includes):
    textures = list(material.get('textures') or [])
    for index, name in enumerate(override.get('textures') or []):
        if name is not None:
            textures += [None] * (index + 1 - len(textures)); textures[index] = name
    bound = {index for index, name in enumerate(textures) if name}
    combos = dict(material.get('combos') or {})
    combos.update(texture_combos((vertex, fragment), bound))
    combos.update(override.get('combos') or {})
    yield kind, {'vertex': vertex, 'fragment': fragment, 'combos': combos, 'includes': includes}, None


UNIFORM = re.compile(r'^\s*uniform\s+\w+\s+(\w+)', re.M)
MODULO_FLOAT = re.compile(r'%\s*float\s*\(|\bfloat\s*\(\s*int\s*\([^;%]*\)\s*\)\s*%')


def lint(glsl):
    """GLSL problems the translator has produced before and Android compilers reject."""
    problems = []
    if MODULO_FLOAT.search(glsl):
        problems.append('% with a float operand')
    for match in UNIFORM.finditer(glsl):
        name = match.group(1)
        first = re.search(r'\b' + re.escape(name) + r'\b', glsl)
        if first and first.start() < match.start():
            problems.append(name + ' used before its declaration')
    return problems


def regressions(workshop):
    """Author shaders that once failed on the phone (3462491575): blurprecise, audio_responsive_oscilloscope."""
    folder = pathlib.Path(workshop) / '3462491575'
    if not (folder / 'scene.pkg').is_file():
        return []
    package = Package(folder / 'scene.pkg')
    includes = engine_headers()
    includes.update({name[len('shaders/'):]: package.text(name) for name in package.entries if name.startswith('shaders/') and name.endswith('.h')})
    out = []
    for base, combos in (('shaders/workshop/2981960200/effects/blur_precise_gaussian', {'VERTICAL': 1, 'ENABLEMASK': 1}),
                         ('shaders/workshop/2981960200/effects/blur_precise_gaussian', {}),
                         ('shaders/workshop/2799421411/effects/audio_responsive_oscilloscope', {})):
        if package.has(base + '.frag'):
            out.append(('regression ' + base.rsplit('/', 1)[-1], {'vertex': package.text(base + '.vert'), 'fragment': package.text(base + '.frag'), 'combos': combos, 'includes': includes}))
    return out


def main():
    workshop = os.environ.get('WVB_WORKSHOP_DIR')
    node = shutil.which('node')
    if not workshop or not node:
        print('SKIP: set WVB_WORKSHOP_DIR and install Node to translate the workshop corpus')
        return
    subprocess.run([sys.executable, str(ROOT / 'tools' / 'bundle_shader_translator.py')], check=True)
    kinds, requests, skipped = [], [], collections.Counter()
    for folder in sorted(pathlib.Path(workshop).iterdir()):
        project = folder / 'project.json'
        if not project.is_file() or str(json.loads(project.read_text(encoding='utf-8-sig')).get('type', '')).lower() != 'scene' or not any(folder.glob('*.pkg')):
            continue
        try:
            found = list(requests_for(folder))
        except (KeyError, ValueError, OSError, struct.error) as error:
            # A package Steam is still writing or validating reads as garbage; report it instead of failing the corpus.
            print(f'skipped unreadable package {folder.name}: {error!r}')
            continue
        for kind, request, reason in found:
            if request is None:
                skipped[kind + ': ' + reason] += 1
            else:
                kinds.append(kind); requests.append(request)
    for kind, request in regressions(workshop):
        kinds.append(kind); requests.append(request)
    with tempfile.TemporaryDirectory(dir=ROOT / '.cache') as work:
        source, target = pathlib.Path(work, 'requests.json'), pathlib.Path(work, 'results.json')
        source.write_text(json.dumps(requests), encoding='utf-8')
        subprocess.run([node, str(ROOT / 'tools' / 'run_shader_translator.js'), str(BUNDLE), str(source), str(target)], check=True)
        results = json.loads(target.read_text(encoding='utf-8'))
    passed, failures = collections.Counter(), collections.defaultdict(list)
    for kind, result in zip(kinds, results):
        good = result['ok'] and all(result[stage].startswith('#version 300 es') and 'void main' in result[stage] for stage in ('vertex', 'fragment'))
        problems = [stage + ': ' + p for stage in ('vertex', 'fragment') for p in (lint(result[stage]) if result.get('ok') else [])]
        if good and problems:
            good = False; result = dict(result, error='; '.join(problems))
        if good:
            passed[kind] += 1
        else:
            failures[kind].append(result.get('error', 'translated text has no entry point'))
    print('Translated:', dict(passed))
    for kind, errors in failures.items():
        print('FAILED', kind, len(errors), errors[0][:200])
    for reason, count in skipped.most_common():
        print('Not single-pass:', count, reason)
    total = sum(passed.values())
    if failures:
        raise SystemExit(f'{sum(len(e) for e in failures.values())} effect translations failed')
    print(f'PASS: {total} workshop layer effects translated with the bundled translator; GLSL compilation is checked on Android')


if __name__ == '__main__':
    main()
