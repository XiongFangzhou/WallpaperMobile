"""Check the publishable Git file set without printing sensitive matched values."""
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.md', '.py', '.ps1', '.java', '.json', '.xml', '.js', '.html', '.css', '.txt', '.properties', '.yml', '.yaml'}
PATTERNS = {
    'absolute machine path': re.compile(r'(?<![A-Za-z])(?:[A-Za-z]:[/\\]|/(?:home|Users)/)[^\s"`<>]*'),
    'private network address': re.compile(r'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
    'credential': re.compile(r'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
}
# These two fixtures deliberately verify rejection of synthetic absolute paths.
FIXTURES = {
    'android/test/dev/workshopvideo/core/ContentChecks.java',
    'android/test/dev/workshopvideo/core/CoreChecks.java',
}


def main():
    files = set(subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')) - {''}
    failures = []

    def scan(name, data):
        for number, line in enumerate(data.decode('utf-8', errors='replace').splitlines(), 1):
            for label, pattern in PATTERNS.items():
                if pattern.search(line) and not (label == 'absolute machine path' and name in FIXTURES):
                    failures.append(f'{name}:{number}: {label}')

    for name in sorted(files):
        path = ROOT / name
        if not path.is_file():
            continue
        if name.startswith(('.cache/', '.tools/', 'dist/', 'ref/', 'downloads/')) or path.suffix in {'.keystore', '.jks', '.p12', '.pfx', '.key'} or path.name in {'.env', 'local.properties', 'keystore.properties'}:
            failures.append(f'{name}: private file in publication set')
        elif path.suffix in TEXT or path.name == '.gitignore':
            scan(name, path.read_bytes())
        elif path.suffix == '.zip':
            with zipfile.ZipFile(path) as bundle:
                for entry in bundle.namelist():
                    if Path(entry).suffix in TEXT:
                        scan(name + '!' + entry, bundle.read(entry))
    if failures:
        print('\n'.join(failures))
        raise SystemExit(1)
    print(f'PASS: {len(files)} publication files checked for machine paths, private addresses and common secret formats.')


if __name__ == '__main__':
    main()
