"""Inspect a HyperOS wallpaper-scroll flag; changes require an explicit command option."""
import argparse
import json
import pathlib
import subprocess
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
KEY = 'pref_key_wallpaper_screen_scrolled_span'
TABLE = 'secure'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', type=pathlib.Path, default=ROOT / '.tools/android-sdk/platform-tools/adb.exe')
    parser.add_argument('--port', type=int, default=5041)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--enable', action='store_true')
    modes.add_argument('--restore', type=pathlib.Path)
    parser.add_argument('--receipt', type=pathlib.Path, default=ROOT / '.cache/launcher-scroll-probe/receipt.json')
    args = parser.parse_args()
    base = [str(args.adb), '-P', str(args.port), '-s', args.serial, 'shell', 'settings']

    def command(*arguments):
        return subprocess.run([*base, *arguments], capture_output=True, encoding='utf-8', errors='replace', check=True).stdout.strip()

    before = command('get', TABLE, KEY)
    if not args.enable and args.restore is None:
        print(json.dumps({'namespace': TABLE, 'key': KEY, 'value': before, 'operation': 'read-only'}))
        return

    if args.restore:
        receipt = json.loads(args.restore.read_text(encoding='utf-8'))
        if (receipt.get('serial'), receipt.get('namespace'), receipt.get('key')) != (args.serial, TABLE, KEY):
            raise RuntimeError('Receipt does not match the selected device and scroll flag.')
        if before != receipt.get('after'):
            raise RuntimeError('The setting changed after the probe; restore was not applied.')
        original = receipt['before']
        if original == 'null':
            command('delete', TABLE, KEY)
        elif original.lstrip('-').isdigit():
            command('put', TABLE, KEY, original)
        else:
            raise RuntimeError('Receipt contains an unexpected original value.')
        restored = command('get', TABLE, KEY)
        if restored != original:
            raise RuntimeError('Restored value could not be verified.')
        print(json.dumps({'namespace': TABLE, 'key': KEY, 'restored': restored}))
        return

    if before == '1':
        print(json.dumps({'namespace': TABLE, 'key': KEY, 'value': before, 'operation': 'already-enabled'}))
        return
    if args.receipt.exists():
        raise RuntimeError('Receipt already exists; preserve it or use a different --receipt path.')
    if before != 'null' and not before.lstrip('-').isdigit():
        raise RuntimeError('Unexpected original setting value.')
    receipt = {'serial': args.serial, 'namespace': TABLE, 'key': KEY, 'before': before, 'after': '1',
               'time_utc': datetime.now(timezone.utc).isoformat(), 'applied': False}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    command('put', TABLE, KEY, '1')
    after = command('get', TABLE, KEY)
    receipt['applied'] = after == '1'
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    if after != '1':
        raise RuntimeError('Enabled value could not be verified; original value remains in the receipt.')
    print(json.dumps({'namespace': TABLE, 'key': KEY, 'before': before, 'after': after,
                      'restore_receipt': str(args.receipt.relative_to(ROOT)) if args.receipt.is_relative_to(ROOT) else str(args.receipt)}))


if __name__ == '__main__':
    main()
