"""Download verified official Android build tools into the repository, without an installer."""
import concurrent.futures
import hashlib
import pathlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SDK = ROOT / '.tools' / 'android-sdk'
CACHE = ROOT / '.cache' / 'downloads'


def install(package):
    name, url, digest, size = package
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE / url
    if not archive.exists() or archive.stat().st_size != size:
        print('Downloading', url, flush=True)
        with urllib.request.urlopen('https://dl.google.com/android/repository/' + url, timeout=60) as source:
            with archive.open('wb') as target:
                while chunk := source.read(1024 * 1024):
                    target.write(chunk)
    if hashlib.sha1(archive.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Android package checksum mismatch: ' + url)
    dest = SDK / name.replace(';', '/')
    if not dest.exists():
        staging = SDK / ('extract-' + name.split(';')[0])
        staging.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as z:
            z.extractall(staging)
        children = list(staging.iterdir())
        dest.parent.mkdir(parents=True, exist_ok=True)
        if len(children) == 1 and children[0].is_dir():
            children[0].rename(dest)
        else:
            staging.rename(dest)
        if staging.exists():
            staging.rmdir()
    print('Ready', dest, flush=True)


if __name__ == '__main__':
    xml = ROOT / '.cache' / 'reference' / 'android-repository.xml'
    if not xml.exists():
        xml.parent.mkdir(parents=True, exist_ok=True)
        xml.write_bytes(urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml', timeout=30).read())
    tree = ET.fromstring(xml.read_bytes())
    packages = []
    for p in tree.findall('remotePackage'):
        name = p.attrib['path']
        if name not in ('platforms;android-35', 'build-tools;35.0.0', 'platform-tools'):
            continue
        for a in p.findall('./archives/archive'):
            if a.findtext('host-os') in (None, 'windows'):
                c = a.find('complete')
                packages.append((name, c.findtext('url'), c.findtext('checksum'), int(c.findtext('size'))))
    with concurrent.futures.ThreadPoolExecutor(3) as pool:
        list(pool.map(install, packages))
    # Kotlin 2.2 requires a newer D8 than Android build-tools 35 bundles.
    compiler = ROOT / '.tools' / 'r8.jar'
    digest = '9743e95de295fb6f5490e21da26536e4d0921809511423122ff20328a572e7f5'
    if not compiler.exists() or hashlib.sha256(compiler.read_bytes()).hexdigest() != digest:
        url = 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.5.33/r8-9.5.33.jar'
        data = urllib.request.urlopen(url, timeout=45).read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError('R8 compiler checksum mismatch')
        compiler.write_bytes(data)
    (ROOT / '.tools' / 'r8.version').write_text('9.5.33', encoding='ascii')
    print('Ready R8 9.5.33', flush=True)
