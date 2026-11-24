"""Render real app screens with sample data to PNG for visual review (host graphics, not a phone).

Run tools/build_android.py --compile-only first. Output: .cache/ui-previews/*.png
"""
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUILD = ROOT / 'android' / 'build'
OUT = BUILD / 'uipreview'
WORK = ROOT / '.cache' / 'ui-previews'
JDK = pathlib.Path(os.environ['WVB_JDK'])
SDK = ROOT / '.cache/robolectric-maven/org/robolectric/android-all/15-robolectric-13954326/android-all-15-robolectric-13954326.jar'


def main():
    if not (BUILD / 'classes').is_dir() or not (BUILD / 'resources.ap_').is_file():
        raise SystemExit('Run tools/build_android.py --compile-only first.')
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    for old in WORK.glob('*.png'):
        old.unlink()
    libs = [*(ROOT / '.tools' / 'robolectric-libs').glob('*.jar'), *(ROOT / '.tools' / 'android-libs').glob('*.jar')]
    cp = os.pathsep.join(map(str, [SDK, BUILD / 'classes', *libs]))
    sources = sorted((ROOT / 'android' / 'test-host' / 'uipreview').rglob('*.java'))
    subprocess.run([str(JDK / 'bin' / 'javac'), '-encoding', 'UTF-8', '--release', '17', '-cp', cp, '-d', str(OUT), *map(str, sources)], check=True)
    # Robolectric loads the app's real compiled resources and manifest from this file.
    config = OUT / 'com' / 'android' / 'tools' / 'test_config.properties'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text('\n'.join([
        'android_merged_manifest=' + (ROOT / 'android' / 'AndroidManifest.xml').as_posix(),
        'android_resource_apk=' + (BUILD / 'resources.ap_').as_posix(),
        'android_custom_package=dev.workshopvideo',
    ]) + '\n', encoding='utf-8')
    sdk_dir = ROOT / '.cache' / 'robolectric-sdk'
    sdk_dir.mkdir(parents=True, exist_ok=True)
    if not (sdk_dir / SDK.name).exists():
        os.link(SDK, sdk_dir / SDK.name)
    subprocess.run([str(JDK / 'bin' / 'java'), '-Xmx3g', '-Dfile.encoding=UTF-8',
                    '-Djava.io.tmpdir=' + str(ROOT / '.cache' / 'tmp'), '-Duser.home=' + str(ROOT / '.cache' / 'android-native-home'),
                    '-Drobolectric.offline=true', '-Drobolectric.usePreinstrumentedJars=false',
                    '-Drobolectric.dependency.dir=' + str(sdk_dir), '-Dpreview.work=' + str(WORK),
                    '-cp', str(OUT) + os.pathsep + cp, 'org.junit.runner.JUnitCore',
                    *(sys.argv[1:] or ['dev.workshopvideo.UiPreviews'])], check=True)
    print('Previews:', WORK, sorted(p.name for p in WORK.glob('*.png')), flush=True)


if __name__ == '__main__':
    main()
