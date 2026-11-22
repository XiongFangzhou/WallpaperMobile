import pathlib
import subprocess
import os
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
from project_env import java_home
JDK = java_home()
OUT = ROOT / 'android' / 'build' / 'core'
OUT.mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join(str(p) for p in (ROOT / '.tools' / 'android-libs').glob('*.jar'))
sources = list((ROOT / 'android' / 'src' / 'dev' / 'workshopvideo' / 'core').glob('*.java'))
sources += list((ROOT / 'android' / 'test').rglob('*.java'))
subprocess.run([str(JDK / 'bin' / 'javac.exe'), '-encoding', 'UTF-8', '--release', '17',
                '-cp', cp, '-d', str(OUT), *map(str, sources)], check=True)
result = subprocess.run([str(JDK / 'bin' / 'java.exe'),
    '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Djava.io.tmpdir=' + str(ROOT / '.cache' / 'tmp'), '-cp', str(OUT) + os.pathsep + cp,
    'dev.workshopvideo.core.CoreChecks', *sys.argv[1:]], cwd=ROOT)
raise SystemExit(result.returncode)
