#!/usr/bin/env python3
"""Minimal zipalign / zip repack with 4-byte alignment for stored entries (stdlib only)."""
import sys, zipfile, struct, zlib, shutil, os

def align_pack(src, dst, alignment=4):
    zin = zipfile.ZipFile(src)
    out = bytearray()
    central = []
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename.endswith('/'):
            continue
        method = zipfile.ZIP_STORED if info.filename == 'resources.arsc' else info.compress_type
        if method == zipfile.ZIP_STORED:
            comp = data
        else:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            comp = co.compress(data) + co.flush()
        crc = zlib.crc32(data) & 0xFFFFFFFF
        nlen = info.filename.encode('utf-8')
        if method == zipfile.ZIP_STORED:
            base = len(out) + 30 + len(nlen) + 4
            pad = (-base) % alignment
            extra = struct.pack('<HH', 0x1234, pad) + b'\x00' * pad if pad else b''
        else:
            extra = b''
        off = len(out)
        out += struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0, method, 0, 0x21, crc,
                           len(comp), len(data), len(nlen), len(extra))
        out += nlen + extra + comp
        central.append((nlen, method, crc, len(comp), len(data), off))
    cd_off = len(out)
    for nlen, method, crc, csize, usize, off in central:
        out += struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0, method, 0, 0x21,
                           crc, csize, usize, len(nlen), 0, 0, 0, 0, 0, off)
        out += nlen
    cd_size = len(out) - cd_off
    out += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(central), len(central),
                       cd_size, cd_off, 0)
    with open(dst, 'wb') as f:
        f.write(out)

def add_files(apk_in, apk_out, pairs):
    tmp = apk_in + '.tmp.zip'
    shutil.copyfile(apk_in, tmp)
    with zipfile.ZipFile(tmp, 'a', zipfile.ZIP_DEFLATED) as z:
        for src, arc in pairs:
            z.write(src, arc)
    align_pack(tmp, apk_out)
    os.remove(tmp)

if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'add':
        apk_in, apk_out = sys.argv[2], sys.argv[3]
        pairs = []
        for arg in sys.argv[4:]:
            src, arc = arg.split(':', 1)
            pairs.append((src, arc))
        add_files(apk_in, apk_out, pairs)
    elif cmd == 'align':
        align_pack(sys.argv[2], sys.argv[3])
