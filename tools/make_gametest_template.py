"""Writes the empty structure template the GameTests run inside (dev only, never shipped).

The NBT must be gzipped and live in data/<modid>/structure/ (singular); the test classes use
@PrefixGameTestTemplate(false) so the name is used as is.
"""

import gzip
import os
import struct
import sys

DATA_VERSION = 3955  # 1.21.1


def _name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def encode(size):
    body = b""
    body += b"\x09" + _name("size") + b"\x03" + struct.pack(">i", 3) + b"".join(struct.pack(">i", v) for v in size)
    for key in ("entities", "blocks", "palette"):
        body += b"\x09" + _name(key) + b"\x0a" + struct.pack(">i", 0)
    body += b"\x03" + _name("DataVersion") + struct.pack(">i", DATA_VERSION)
    return b"\x0a" + _name("") + body + b"\x00"


if __name__ == "__main__":
    modid, name, edge = sys.argv[1], sys.argv[2], int(sys.argv[3])
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "neoforge", "src", "gametest", "resources", "data", modid, "structure", name + ".nbt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as handle:
        handle.write(gzip.compress(encode((edge, edge, edge)), mtime=0))
    print("wrote", out)
