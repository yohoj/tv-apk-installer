#!/usr/bin/env python3
"""Print package name and versionCode from an APK's binary AndroidManifest.xml.

    apkmeta.py app.apk           -> "app.orchard.tv 55"
    apkmeta.py --package app.apk -> "app.orchard.tv"
    apkmeta.py --version app.apk -> "55"

Pure Python, no Android SDK needed. Exits 1 when the manifest cannot be read.
"""
import struct
import sys
import zipfile


def _string_pool(data, pos, csize):
    count, _styles, flags, strings_start, _styles_start = struct.unpack_from(
        "<IIIII", data, pos + 8
    )
    offsets = struct.unpack_from("<%dI" % count, data, pos + 28)
    utf8 = bool(flags & 0x100)
    base = pos + strings_start
    out = []
    for off in offsets:
        p = base + off
        try:
            if utf8:
                n = data[p]
                p += 1
                if n & 0x80:
                    p += 1
                ln = data[p]
                p += 1
                if ln & 0x80:
                    ln = ((ln & 0x7F) << 8) | data[p]
                    p += 1
                out.append(data[p : p + ln].decode("utf-8", "replace"))
            else:
                ln = struct.unpack_from("<H", data, p)[0]
                p += 2
                if ln & 0x8000:
                    ln = ((ln & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                    p += 2
                out.append(data[p : p + ln * 2].decode("utf-16-le", "replace"))
        except (IndexError, struct.error):
            out.append("")
    return out


def read_manifest(apk):
    with zipfile.ZipFile(apk) as z:
        return z.read("AndroidManifest.xml")


def parse(data):
    strings = []
    pos = 8
    while pos + 8 <= len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if csize < 8 or pos + csize > len(data):
            break
        if ctype == 0x0001:  # RES_STRING_POOL_TYPE
            strings = _string_pool(data, pos, csize)
        elif ctype == 0x0102:  # RES_XML_START_ELEMENT_TYPE
            ns, name, attr_start, attr_size, attr_count = struct.unpack_from(
                "<IIHHH", data, pos + 16
            )
            if name < len(strings) and strings[name] == "manifest":
                package = None
                version = None
                for i in range(attr_count):
                    # The XML node header is 16 bytes; attributeStart is relative to it.
                    a = pos + 16 + attr_start + i * attr_size
                    if a + 20 > len(data):
                        break
                    (ans, aname, araw, asize, ares0, atype, adata) = struct.unpack_from(
                        "<IIIHBBI", data, a
                    )
                    an = strings[aname] if aname < len(strings) else ""
                    if an == "package":
                        if atype == 0x03 and adata < len(strings):
                            package = strings[adata]
                        elif araw < len(strings):
                            package = strings[araw]
                    elif an == "versionCode" and atype == 0x10:
                        version = adata
                return package, version
        pos += csize
    return None, None


def main():
    args = sys.argv[1:]
    mode = "both"
    if args and args[0] in ("--package", "--version"):
        mode = args.pop(0)[2:]
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        package, version = parse(read_manifest(args[0]))
    except (OSError, zipfile.BadZipFile, KeyError) as exc:
        print("apkmeta: %s" % exc, file=sys.stderr)
        return 1
    if not package:
        print("apkmeta: package not found", file=sys.stderr)
        return 1
    if mode == "package":
        print(package)
    elif mode == "version":
        print(version if version is not None else "0")
    else:
        print("%s %s" % (package, version if version is not None else "0"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
