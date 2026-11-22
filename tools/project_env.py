"""Resolve developer tools without machine-specific paths."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def java_home():
    candidates = [os.environ.get('WVB_JDK'), os.environ.get('JAVA_HOME')]
    config = ROOT / '.tools/jdk-path.txt'
    if config.is_file():
        configured = Path(config.read_text(encoding='utf-8-sig').strip())
        candidates.append(configured if configured.is_absolute() else ROOT / configured)
    candidates.append(ROOT / '.tools/jdk')
    compiler = shutil.which('javac')
    if compiler:
        candidates.append(Path(compiler).resolve().parent.parent)
    for candidate in candidates:
        if candidate and (Path(candidate) / 'bin/javac.exe').is_file():
            return Path(candidate).resolve()
    raise RuntimeError('Install JDK 21 and set JAVA_HOME or WVB_JDK, or configure .tools/jdk-path.txt.')
