"""Resolve the pinned JavaSteam Maven runtime graph into the repository workspace.

Uses Google's public Maven Central mirror; artifacts are checked against Maven
SHA-1 metadata, with a SHA-256 lock file for repeatable subsequent builds.
No Gradle daemon, global dependency cache, or installer is required.
"""
import concurrent.futures
import hashlib
import json
import pathlib
import re
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache' / 'maven'
OUT = ROOT / '.tools' / 'android-libs'
BASE = 'https://maven-central.storage-download.googleapis.com/maven2/'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
PINNED = {
    ('org.jetbrains.kotlin', 'kotlin-stdlib'): '2.2.21',
    ('org.jetbrains.kotlin', 'kotlin-stdlib-jdk7'): '2.2.21',
    ('org.jetbrains.kotlin', 'kotlin-stdlib-jdk8'): '2.2.21',
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm'): '1.10.2',
    ('com.google.protobuf', 'protobuf-java'): '4.31.1',
}


def download(relative):
    target = CACHE / relative
    if target.exists():
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(BASE + relative, timeout=30) as response:
                data = response.read()
            target.write_bytes(data)
            return target
        except OSError:
            if attempt == 2:
                raise
            time.sleep(0.3)


def path(group, artifact, version, ext):
    return f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.{ext}'


def txt(node, key, default=''):
    found = node.find('m:' + key, NS)
    return found.text.strip() if found is not None and found.text else default


MODELS = {}


def model(group, artifact, version):
    key = group, artifact, version
    if key in MODELS:
        return MODELS[key]
    tree = ET.fromstring(download(path(*key, 'pom')).read_bytes())
    props, management = {}, {}
    parent = tree.find('m:parent', NS)
    if parent is not None:
        p = model(txt(parent, 'groupId'), txt(parent, 'artifactId'), txt(parent, 'version'))
        props.update(p['properties'])
        management.update(p['management'])
    props.update({'project.groupId': group, 'project.artifactId': artifact,
                  'project.version': version, 'pom.version': version})
    properties = tree.find('m:properties', NS)
    if properties is not None:
        props.update({p.tag.rsplit('}', 1)[-1]: (p.text or '').strip() for p in properties})

    def resolve(value):
        for _ in range(10):
            new = re.sub(r'\$\{([^}]+)\}', lambda m: props.get(m[1], m[0]), value)
            if new == value:
                break
            value = new
        return value

    for dep in tree.findall('m:dependencyManagement/m:dependencies/m:dependency', NS):
        g, a, v = [resolve(txt(dep, x)) for x in ('groupId', 'artifactId', 'version')]
        if txt(dep, 'scope') == 'import' and txt(dep, 'type') == 'pom':
            management.update(model(g, a, v)['management'])
        elif v:
            management[(g, a)] = v
    deps = []
    for dep in tree.findall('m:dependencies/m:dependency', NS):
        if txt(dep, 'scope', 'compile') not in ('compile', 'runtime') or txt(dep, 'optional') == 'true':
            continue
        g, a = resolve(txt(dep, 'groupId')), resolve(txt(dep, 'artifactId'))
        if g.startswith(('org.junit', 'junit')):
            continue
        v = PINNED.get((g, a), resolve(txt(dep, 'version', management.get((g, a), ''))))
        if not v or '${' in v:
            raise RuntimeError(f'Unresolved dependency {g}:{a}:{v}')
        if v.startswith('[') and v.endswith(']'):
            v = v[1:-1]
        deps.append((g, a, v))
    result = {'properties': props, 'management': management, 'dependencies': deps,
              'packaging': txt(tree, 'packaging', 'jar')}
    MODELS[key] = result
    return result


def artifact(key):
    info = model(*key)
    ext = info['packaging']
    if ext == 'pom':
        return None
    if ext not in ('jar', 'aar'):
        raise RuntimeError(f'Unsupported packaging: {key}: {ext}')
    relative = path(*key, ext)
    data = download(relative)
    expected = download(relative + '.sha1').read_text().strip().split()[0]
    if hashlib.sha1(data.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Maven checksum mismatch: ' + relative)
    OUT.mkdir(parents=True, exist_ok=True)
    output = OUT / ('-'.join(key[1:]) + '.jar')
    if ext == 'jar':
        output.write_bytes(data.read_bytes())
    else:
        with zipfile.ZipFile(data) as z:
            output.write_bytes(z.read('classes.jar'))
            for name in z.namelist():
                if name.startswith('jni/') and not name.endswith('/'):
                    target = OUT / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(name))
    result = {'coordinate': ':'.join(key), 'artifact': relative,
              'sha256': hashlib.sha256(data.read_bytes()).hexdigest(),
              'jar': str(output.relative_to(ROOT)).replace('\\', '/')}
    print('Ready', result['coordinate'], flush=True)
    return result


def main():
    pending = [('in.dragonbra', 'javasteam', '1.8.0'),
               ('org.bouncycastle', 'bcprov-jdk18on', '1.80'),
               ('org.tukaani', 'xz', '1.10'),
               ('com.github.luben', 'zstd-jni', '1.5.7-6')]
    resolved = {}
    while pending:
        batch = [x for x in pending if (x[0], x[1]) not in resolved]
        pending = []
        if not batch:
            break
        with concurrent.futures.ThreadPoolExecutor(8) as pool:
            models = list(pool.map(lambda x: model(*x), batch))
        for key, info in zip(batch, models):
            ga = key[:2]
            if ga in resolved:
                continue
            resolved[ga] = key
            pending.extend(info['dependencies'])
    # zstd publishes Android native code as an AAR under the same coordinates.
    MODELS[('com.github.luben', 'zstd-jni', '1.5.7-6')]['packaging'] = 'aar'
    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        lock = [x for x in pool.map(artifact, resolved.values()) if x]
    lock.sort(key=lambda x: x['coordinate'])
    lockfile = ROOT / 'android' / 'dependencies.lock.json'
    if lockfile.exists():
        old = json.loads(lockfile.read_text(encoding='utf-8'))
        if old != lock:
            raise RuntimeError('Dependency lock changed; inspect before accepting.')
    else:
        lockfile.parent.mkdir(exist_ok=True)
        lockfile.write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8')
    print(f'{len(lock)} verified runtime artifacts ready.', flush=True)


if __name__ == '__main__':
    main()
