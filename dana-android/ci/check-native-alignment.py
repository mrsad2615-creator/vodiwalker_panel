"""APK ZIP alignment is not enough: verify the ELF PT_LOAD alignment of 64-bit libraries."""

import struct
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as apk:
    for abi in ("arm64-v8a", "x86_64"):
        names = [name for name in apk.namelist() if name.startswith(f"lib/{abi}/") and name.endswith(".so")]
        for required in ("libtesseract.so", "liblitertlm_jni.so"):
            if f"lib/{abi}/{required}" not in names:
                raise SystemExit(f"Missing {required} for {abi}")
        for lib in names:
            data = apk.read(lib)
            if data[:6] != b"\x7fELF\x02\x01":
                raise SystemExit(f"Invalid 64-bit little-endian ELF: {lib}")
            start = struct.unpack_from("<Q", data, 32)[0]
            each, count = struct.unpack_from("<HH", data, 54)
            loads = 0
            for i in range(count):
                typ, _, offset, addr, _, _, _, align = struct.unpack_from("<IIQQQQQQ", data, start + i * each)
                if typ != 1:
                    continue
                loads += 1
                if align < 16384 or (addr - offset) % 16384:
                    raise SystemExit(f"16 KiB ELF PT_LOAD alignment failed for {lib}: {align:#x}")
            if not loads:
                raise SystemExit(f"No loadable segments for {lib}")
            print(f"{lib}: {loads} PT_LOAD segments aligned to 16 KiB")
