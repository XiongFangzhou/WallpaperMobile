"""Download Google's Android Emulator Hypervisor Driver (AEHD) from the official SDK repository, SHA-1 checked.

Installing it is a separate, elevated step (silent_install.bat as administrator), done by the user's consent.
"""
import hashlib
import pathlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TARGET = ROOT / '.tools/android-sdk/extras/google/Android_Emulator_Hypervisor_Driver'
CACHE = ROOT / '.cache/sdk-downloads'
INDEX = 'https://dl.google.com/android/repository/addon2-3.xml'


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(INDEX, timeout=45) as response:
        root = ET.fromstring(response.read())
    found = None
    for package in root.iter():
        if package.tag.endswith('remotePackage') and package.get('path') == 'extras;google;Android_Emulator_Hypervisor_Driver':
            for archive in package.iter():
                if archive.tag.endswith('archive') and (archive.findtext('host-os') or 'windows') == 'windows':
                    complete = next(c for c in archive.iter() if c.tag.endswith('complete'))
                    url = next(c for c in complete if c.tag.endswith('url')).text
                    checksum = next(c for c in complete if c.tag.endswith('checksum')).text
                    found = found or (url, checksum)
    if found is None:
        raise SystemExit('AEHD is not listed in ' + INDEX)
    name, checksum = found
    archive = CACHE / name
    if not archive.is_file() or hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        with urllib.request.urlopen('https://dl.google.com/android/repository/' + name, timeout=120) as src:
            archive.write_bytes(src.read())
    if hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise SystemExit('Checksum mismatch for ' + name)
    TARGET.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        for entry in bundle.infolist():
            out = (TARGET / entry.filename).resolve()
            if not out.is_relative_to(TARGET.resolve()):
                raise SystemExit('Unsafe path in archive')
        bundle.extractall(TARGET)
    print('AEHD', name, 'sha1 ok ->', TARGET)


if __name__ == '__main__':
    main()
