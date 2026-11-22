"""Build and sign the native Android APK inside the repository workspace."""
import argparse
import hashlib
import json
import os
import pathlib
import secrets
import shutil
import subprocess
import zipfile
from check_apk import check_apk

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'android'
BUILD = ANDROID / 'build'
SDK = ROOT / '.tools' / 'android-sdk'
TOOLS = SDK / 'build-tools' / '35.0.0'
from project_env import java_home
JDK = java_home()
JAVA = JDK / 'bin' / 'java.exe'
PLATFORM = SDK / 'platforms' / 'android-35' / 'android.jar'
DIST = ROOT / 'dist'


def run(args, **kwargs):
    label = pathlib.Path(str(args[0])).name
    print('Running', label, flush=True)
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--compile-only', action='store_true')
    args = parser.parse_args()
    BUILD.mkdir(parents=True, exist_ok=True)
    (ROOT / '.cache' / 'tmp').mkdir(parents=True, exist_ok=True)
    os.environ['TEMP'] = str(ROOT / '.cache' / 'tmp')
    os.environ['TMP'] = os.environ['TEMP']
    os.environ['JAVA_HOME'] = str(JDK)
    os.environ['PATH'] = str(JDK / 'bin') + os.pathsep + os.environ['PATH']
    generated, classes, dex = (BUILD / n for n in ('generated', 'classes', 'dex'))
    for p in (generated, classes, dex):
        if p.resolve().parent != BUILD.resolve():
            raise RuntimeError('Build output must stay inside android/build.')
        if p.exists():
            shutil.rmtree(p)
        p.mkdir()
    dependency_lock = json.loads((ANDROID / 'dependencies.lock.json').read_text())
    libs = [ROOT / i['jar'] for i in dependency_lock]
    run([TOOLS / 'aapt2.exe', 'compile', '--dir', ANDROID / 'res', '-o', BUILD / 'resources.zip'])
    overlays, extra_packages = [], []
    for index, item in enumerate(dependency_lock):
        if not item.get('resources'):
            continue
        compiled = BUILD / ('dependency-resources-' + str(index) + '.zip')
        run([TOOLS / 'aapt2.exe', 'compile', '--dir', ROOT / item['resources'], '-o', compiled])
        overlays.extend(['-R', compiled])
        extra_packages.append(item['android_package'])
    extra = ['--extra-packages', ':'.join(extra_packages)] if extra_packages else []
    run([TOOLS / 'aapt2.exe', 'link', '--manifest', ANDROID / 'AndroidManifest.xml',
         '-I', PLATFORM, '--java', generated, '--min-sdk-version', '29', '--target-sdk-version', '35',
         '--version-code', '31', '--version-name', '0.5.1', BUILD / 'resources.zip',
         '--auto-add-overlay', *overlays, *extra,
         '-A', ANDROID / 'assets', '-o', BUILD / 'resources.ap_'])
    source_paths = sorted((ANDROID / 'src').rglob('*.java')) + list(generated.rglob('*.java'))
    arguments = ['-encoding', 'UTF-8', '--release', '17', '-cp',
                 os.pathsep.join(map(str, [PLATFORM, *libs])), '-d', str(classes), *map(str, source_paths)]
    argfile = BUILD / 'javac.args'
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in arguments), encoding='utf-8')
    run([JDK / 'bin' / 'javac.exe', '@' + str(argfile)])
    if args.compile_only:
        return
    program = BUILD / 'program.jar'
    with zipfile.ZipFile(program, 'w', zipfile.ZIP_DEFLATED) as z:
        for p in classes.rglob('*.class'):
            z.write(p, str(p.relative_to(classes)).replace('\\', '/'))
    compiler = ROOT / '.tools' / 'r8.jar'
    if not compiler.exists():
        raise RuntimeError('Run tools/prepare_android.py to install the compatible R8 compiler.')
    log = BUILD / 'dex-build.log'
    with log.open('w', encoding='utf-8') as output:
        try:
            run([JAVA, '-Xmx4g', '-Djava.io.tmpdir=' + os.environ['TEMP'], '-cp', compiler,
                 'com.android.tools.r8.D8', '--lib', PLATFORM, '--min-api', '29', '--output', dex, program, *libs],
                stdout=output, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError:
            print(log.read_text(encoding='utf-8', errors='replace')[-6000:])
            raise
    print('DEX build complete; diagnostic log:', log, flush=True)
    unsigned = BUILD / 'unsigned.apk'
    with zipfile.ZipFile(BUILD / 'resources.ap_') as resources:
        with zipfile.ZipFile(unsigned, 'w', zipfile.ZIP_DEFLATED) as z:
            # Preserve aapt2's storage method. Android 11+ rejects a compressed
            # resources.arsc for applications targeting API 30 or later.
            for entry in resources.infolist():
                z.writestr(entry, resources.read(entry.filename))
            for p in dex.glob('*.dex'):
                z.write(p, p.name)
            # Java/Kotlin runtime service loader resources matter for Ktor/SLF4J.
            merged = {}
            for p in libs:
                with zipfile.ZipFile(p) as jar:
                    for name in jar.namelist():
                        if name.startswith('META-INF/services/') and not name.endswith('/'):
                            merged.setdefault(name, set()).update(jar.read(name).decode('utf-8').splitlines())
                        elif not name.endswith(('.class', '/')) and not name.startswith(('META-INF/', 'module-info')):
                            if name not in z.namelist():
                                z.writestr(name, jar.read(name))
            for name, lines in merged.items():
                z.writestr(name, '\n'.join(sorted(lines)) + '\n')
            native = ROOT / '.tools' / 'android-libs' / 'jni'
            for p in native.rglob('*.so'):
                if p.parent.name in ('arm64-v8a', 'x86_64', 'armeabi-v7a'):
                    z.write(p, 'lib/' + str(p.relative_to(native)).replace('\\', '/'))
    aligned = BUILD / 'aligned.apk'
    run([TOOLS / 'zipalign.exe', '-f', '-P', '16', '4', unsigned, aligned])
    signing = ROOT / '.tools' / 'signing'
    signing.mkdir(exist_ok=True)
    password_file, key = signing / 'password', signing / 'workshop-video.keystore'
    if not password_file.exists():
        password_file.write_text(secrets.token_hex(24), encoding='ascii')
    env = dict(os.environ, WVB_SIGNING_PASSWORD=password_file.read_text(encoding='ascii').strip())
    if not key.exists():
        run([JDK / 'bin' / 'keytool.exe', '-genkeypair', '-keystore', key, '-alias', 'workshop-video',
             '-storepass:env', 'WVB_SIGNING_PASSWORD', '-keypass:env', 'WVB_SIGNING_PASSWORD',
             '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
             '-dname', 'CN=Workshop Video Local Development'], env=env)
    DIST.mkdir(exist_ok=True)
    apk = DIST / 'WallpaperMobile.apk'
    run([JAVA, '-jar', TOOLS / 'lib' / 'apksigner.jar', 'sign', '--ks', key,
         '--ks-key-alias', 'workshop-video', '--ks-pass', 'env:WVB_SIGNING_PASSWORD',
         '--key-pass', 'env:WVB_SIGNING_PASSWORD', '--out', apk, aligned], env=env)
    run([JAVA, '-jar', TOOLS / 'lib' / 'apksigner.jar', 'verify', '--verbose', apk])
    run([TOOLS / 'zipalign.exe', '-c', '-P', '16', '4', apk])
    check_apk(apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (DIST / 'WallpaperMobile.apk.sha256').write_text(digest + '  WallpaperMobile.apk\n', encoding='ascii')
    (DIST / 'WallpaperVideoBridge.apk').write_bytes(apk.read_bytes())
    (DIST / 'WallpaperVideoBridge.apk.sha256').write_text(digest + '  WallpaperVideoBridge.apk\n', encoding='ascii')
    # Give the user an unambiguous filename to distinguish the repaired APK.
    versioned = DIST / 'WallpaperMobile-0.5.1.apk'
    versioned.write_bytes(apk.read_bytes())
    versioned.with_suffix('.apk.sha256').write_text(digest + '  ' + versioned.name + '\n', encoding='ascii')
    print('APK:', apk, 'bytes:', apk.stat().st_size, 'SHA256:', digest, flush=True)


if __name__ == '__main__':
    main()
