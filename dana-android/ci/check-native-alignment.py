"""Verify the installed APK's ABI set and ELF load segment alignment."""

import argparse
from pathlib import Path
import struct
from zipfile import ZipFile

# EI_CLASS, ELF e_machine, minimum PT_LOAD alignment.
ABI = {
    "armeabi-v7a": (1, 40, 4096),
    "arm64-v8a": (2, 183, 16384),
    "x86": (1, 3, 4096),
    "x86_64": (2, 62, 16384),
}


def check_elf(name, data, abi):
    cls, machine, page = ABI[abi]
    if data[:6] != b"\x7fELF" + bytes([cls, 1]) or struct.unpack_from("<H", data, 18)[0] != machine:
        raise SystemExit(f"ELF class, byte order or machine does not match {abi}: {name}")
    if cls == 2:
        start = struct.unpack_from("<Q", data, 32)[0]
        each, count = struct.unpack_from("<HH", data, 54)
        fmt = "<IIQQQQQQ"
    else:
        start = struct.unpack_from("<I", data, 28)[0]
        each, count = struct.unpack_from("<HH", data, 42)
        fmt = "<IIIIIIII"
    if not count or each != struct.calcsize(fmt) or start + each * count > len(data):
        raise SystemExit(f"Malformed ELF program headers: {name}")
    loads = 0
    for i in range(count):
        fields = struct.unpack_from(fmt, data, start + i * each)
        if fields[0] != 1:  # PT_LOAD
            continue
        loads += 1
        offset, addr = fields[2:4] if cls == 2 else fields[1:3]
        align = fields[-1]
        if align < page or align & (align - 1) or (addr - offset) % page:
            raise SystemExit(f"{page // 1024} KiB ELF PT_LOAD alignment failed for {name}: {align:#x}")
    if not loads:
        raise SystemExit(f"No loadable segments for {name}")
    print(f"{name}: {loads} PT_LOAD segments aligned to {page // 1024} KiB")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--edition", choices=("models", "compat"), default="models")
    args = parser.parse_args()
    compat = args.edition == "compat"
    expected = set(ABI) if compat else {"arm64-v8a", "x86_64"}
    if compat and args.apk.stat().st_size > 80 * 1024 * 1024:
        raise SystemExit("The compatibility APK exceeded its 80 MiB size budget.")

    with ZipFile(args.apk) as apk:
        files = apk.namelist()
        libs = [name for name in files if name.startswith("lib/") and name.endswith(".so")]
        if len(set(libs)) != len(libs) or any(len(name.split("/")) != 3 for name in libs):
            raise SystemExit("Duplicate or invalid APK native library entries")
        actual = {name.split("/")[1] for name in libs}
        if actual != expected:
            raise SystemExit(f"Expected APK ABIs {sorted(expected)}, found {sorted(actual)}")
        if compat and any(name.startswith("assets/models/") or name.endswith(".litertlm") or
                          "litert" in name.rsplit("/", 1)[-1].lower() for name in files):
            raise SystemExit("Offline inference runtime or model assets leaked into the compatibility APK")
        required = ["libtesseract.so"] if compat else ["libtesseract.so", "liblitertlm_jni.so"]
        for abi in sorted(expected):
            names = [name for name in libs if name.startswith(f"lib/{abi}/")]
            for library in required:
                if f"lib/{abi}/{library}" not in names:
                    raise SystemExit(f"Missing {library} for {abi}")
            for name in names:
                check_elf(name, apk.read(name), abi)


if __name__ == "__main__":
    main()
