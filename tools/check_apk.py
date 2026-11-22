"""Check installer-sensitive APK storage rules and ARM64 ELF compatibility."""
import pathlib
import struct
import sys
import zipfile


def check_apk(path):
    with zipfile.ZipFile(path) as archive, open(path, 'rb') as raw:
        table = archive.getinfo('resources.arsc')
        if table.compress_type != zipfile.ZIP_STORED:
            raise ValueError('Android 11+ requires resources.arsc to be uncompressed for target API 30+.')
        raw.seek(table.header_offset)
        header = raw.read(30)
        name_size, extra_size = struct.unpack_from('<HH', header, 26)
        offset = table.header_offset + 30 + name_size + extra_size
        if offset % 4:
            raise ValueError('resources.arsc is not aligned to a 4-byte boundary.')
        native = [i for i in archive.infolist() if i.filename.startswith('lib/arm64-v8a/') and i.filename.endswith('.so')]
        if not native:
            raise ValueError('Missing ARM64 Android libraries.')
        for entry in native:
            data = archive.read(entry)
            if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
                raise ValueError('Invalid little-endian AArch64 ELF: ' + entry.filename)
            phoff = struct.unpack_from('<Q', data, 32)[0]
            size, count = struct.unpack_from('<HH', data, 54)
            loads = 0
            for n in range(count):
                pos = phoff + n * size
                if struct.unpack_from('<I', data, pos)[0] != 1:
                    continue
                loads += 1
                file_offset, virtual = struct.unpack_from('<QQ', data, pos + 8)
                alignment = struct.unpack_from('<Q', data, pos + 48)[0]
                if alignment < 16384 or (virtual - file_offset) % 16384:
                    raise ValueError('ELF LOAD segment does not support 16 KB pages: ' + entry.filename)
            if not loads:
                raise ValueError('No ELF LOAD segments: ' + entry.filename)
        if not any(name.startswith('classes') and name.endswith('.dex') for name in archive.namelist()):
            raise ValueError('Missing application DEX.')
    print('PASS: uncompressed/aligned resource table, ARM64 ELF and 16 KB LOAD alignment, DEX')


if __name__ == '__main__':
    check_apk(pathlib.Path(sys.argv[1]))
