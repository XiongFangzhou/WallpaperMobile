"""Restore the exact runtime artifacts recorded in the dependency lock file."""
import concurrent.futures
import hashlib
import json
from pathlib import Path, PurePosixPath
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def target(relative):
    result = (ROOT / relative).resolve()
    if not result.is_relative_to(ROOT):
        raise ValueError('Dependency output must stay inside the repository')
    return result


def restore(item):
    relative = item['artifact']
    archive = target('.cache/maven/' + relative)
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != item['sha256']:
        mirrors = ['https://dl.google.com/dl/android/maven2/'] if relative.startswith('androidx/') else [
            'https://maven-central.storage-download.googleapis.com/maven2/',
            'https://repo.maven.apache.org/maven2/']
        for mirror in mirrors:
            try:
                with urllib.request.urlopen(mirror + relative, timeout=45) as response:
                    data = response.read()
                if hashlib.sha256(data).hexdigest() != item['sha256']:
                    raise ValueError('Checksum mismatch: ' + item['coordinate'])
                archive.write_bytes(data)
                break
            except OSError:
                if mirror == mirrors[-1]:
                    raise
    output = target(item['jar'])
    output.parent.mkdir(parents=True, exist_ok=True)
    if archive.suffix == '.jar':
        output.write_bytes(archive.read_bytes())
    else:
        with zipfile.ZipFile(archive) as bundle:
            output.write_bytes(bundle.read('classes.jar'))
            for name in bundle.namelist():
                parts = PurePosixPath(name).parts
                if '..' in parts or name.endswith('/'):
                    continue
                destination = None
                if name.startswith('jni/'):
                    destination = target('.tools/android-libs/' + name)
                elif item.get('resources') and name.startswith('res/'):
                    destination = target(item['resources'] + '/' + name[4:])
                if destination:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(bundle.read(name))
        if item.get('resources'):
            target(item['resources']).mkdir(parents=True, exist_ok=True)
    return item['coordinate']


def main():
    lock = json.loads((ROOT / 'android/dependencies.lock.json').read_text(encoding='utf-8'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for coordinate in pool.map(restore, lock):
            print('Ready', coordinate, flush=True)
    print(f'Restored {len(lock)} locked dependencies.', flush=True)


if __name__ == '__main__':
    main()
