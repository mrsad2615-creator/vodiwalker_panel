#!/usr/bin/env python3
"""Static Android API-level audit.

Reads compiled JVM class files (the `classes.jar` that `d8` turns into `classes.dex`) and reports
every reference into the `android.*` namespace that does **not** exist in a given `android.jar`.
That is exactly the set of calls that throws `NoSuchMethodError` / `NoClassDefFoundError` on a real
device at that API level, which is a crash the JVM test suite cannot see.

Usage:
    python3 apiscan.py <classes.jar> <min-api> [android.jar ...]

The last jar is treated as the newest level (the compile SDK); each earlier jar is checked as well,
so the output also says *when* a symbol appears.
"""

import sys
import zipfile
import struct

# ---------------------------------------------------------------- class file parsing


def parse_class(data):
    """Returns (this_name, [(member_kind, owner, name, desc)]) for refs, plus own members."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    pos = 8
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    pool = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:  # Utf8
            (length,) = struct.unpack_from(">H", data, pos)
            pos += 2
            pool[i] = ("utf8", data[pos:pos + length].decode("utf-8", "replace"))
            pos += length
        elif tag in (3, 4):  # Integer, Float
            pool[i] = (tag, None)
            pos += 4
        elif tag in (5, 6):  # Long, Double
            pool[i] = (tag, None)
            pos += 8
            i += 1
        elif tag == 7:  # Class
            (idx,) = struct.unpack_from(">H", data, pos)
            pool[i] = ("class", idx)
            pos += 2
        elif tag == 8:  # String
            pool[i] = ("string", None)
            pos += 2
        elif tag in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            a, b = struct.unpack_from(">HH", data, pos)
            pool[i] = ("ref", (tag, a, b))
            pos += 4
        elif tag == 12:  # NameAndType
            a, b = struct.unpack_from(">HH", data, pos)
            pool[i] = ("nat", (a, b))
            pos += 4
        elif tag in (15, 16, 18, 19, 20):  # MethodHandle & co: 3-4 bytes, skip payload
            size = 3 if tag == 15 else 2 if tag in (16, 19, 20) else 4
            pool[i] = (tag, None)
            pos += size
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        i += 1

    def utf8(idx):
        entry = pool[idx]
        if entry and entry[0] == "utf8":
            return entry[1]
        raise ValueError("bad utf8 index %d" % idx)

    def class_name(idx):
        entry = pool[idx]
        if entry[0] == "class":
            return utf8(entry[1])
        # arrays have no Class entry with a Utf8 name in old compilers, but here they do
        return utf8(entry[1])

    # class header
    (access,) = struct.unpack_from(">H", data, pos)
    pos += 2
    (this_idx, super_idx) = struct.unpack_from(">HH", data, pos)
    pos += 4
    (iface_count,) = struct.unpack_from(">H", data, pos)
    pos += 2 + 2 * iface_count
    (field_count,) = struct.unpack_from(">H", data, pos)
    pos += 2

    own = []
    for _ in range(field_count):
        facc, name_i, desc_i, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        own.append(("field", utf8(name_i), utf8(desc_i), facc))
        for _ in range(attr_count):
            (alen,) = struct.unpack_from(">I", data, pos + 2)
            pos += 6 + alen

    (method_count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    for _ in range(method_count):
        macc, name_i, desc_i, attr_count = struct.unpack_from(">HHHH", data, pos)
        pos += 8
        own.append(("method", utf8(name_i), utf8(desc_i), macc))
        for _ in range(attr_count):
            (alen,) = struct.unpack_from(">I", data, pos + 2)
            pos += 6 + alen

    refs = set()
    for entry in pool:
        if not entry or entry[0] != "ref":
            continue
        tag, class_i, nat_i = entry[1]
        nat = pool[nat_i]
        if not nat or nat[0] != "nat":
            continue
        name = utf8(nat[1][0])
        desc = utf8(nat[1][1])
        owner = class_name(class_i)
        kind = "field" if tag == 9 else "method"
        refs.add((kind, owner, name, desc))
    return class_name(this_idx), access, own, refs


def load_jar(path):
    classes = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            try:
                this, access, own, _ = parse_class(z.read(name))
            except Exception:
                continue
            methods = {(n, d) for (k, n, d, a) in own if k == "method"}
            fields = {(n, d) for (k, n, d, a) in own if k == "field"}
            classes[this] = (methods, fields)
    return classes


# ---------------------------------------------------------------- main


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    jar_path, min_api = sys.argv[1], int(sys.argv[2])
    jars = sys.argv[3:]

    refs = set()
    with zipfile.ZipFile(jar_path) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            try:
                _, _, _, cls_refs = parse_class(z.read(name))
            except Exception:
                continue
            refs |= cls_refs

    # Anything the platform itself provides: android.*, but also java.* and javax.*, whose
    # availability on Android is version dependent too (String.join is API 26, Map.getOrDefault 24).
    platform_prefixes = ("android/", "java/", "javax/", "dalvik/", "org/json/", "org/w3c/", "org/xml/")
    android_refs = {r for r in refs if r[1].startswith(platform_prefixes)}
    android_refs = {r for r in android_refs if not r[1].startswith("java/lang/Object")}
    if not android_refs:
        print("no android references found")
        return 1

    indexes = [(path, load_jar(path)) for path in jars]
    # level labels come from the file name, e.g. android-23.jar -> 23
    levels = []
    for path, index in indexes:
        tail = path.rsplit("android-", 1)[-1]
        label = "".join(ch for ch in tail.split("/")[0] if ch.isdigit())
        levels.append((label, index))

    problems = []
    for kind, owner, name, desc in sorted(android_refs):
        # find the oldest level that has it
        first = None
        for label, index in levels:
            entry = index.get(owner)
            if entry is None:
                continue
            members = entry[0] if kind == "method" else entry[1]
            if (name, desc) in members or name == "<init>":
                if name in ("<init>", "<clinit>") or (name, desc) in members:
                    first = label
                    break
        if first is None:
            problems.append((owner, kind, name, desc, "missing in all jars"))
            continue
        if int(first) > min_api:
            problems.append((owner, kind, name, desc, "API %s" % first))

    print("references into platform classes: %d" % len(android_refs))
    print("jars checked: %s" % ", ".join(lv for lv, _ in levels))
    print("minSdk declared: %d" % min_api)
    if not problems:
        print("OK: every referenced platform symbol exists at API %d" % min_api)
        return 0
    print("\n%d symbol(s) newer than the declared minimum:" % len(problems))
    for owner, kind, name, desc, why in problems:
        print("  %-58s %-6s %-28s %-22s %s" % (owner, kind, name, desc[:22], why))
    return 1


if __name__ == "__main__":
    sys.exit(main())
