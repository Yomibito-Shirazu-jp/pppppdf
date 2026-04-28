#!/usr/bin/env python3
"""Extract a Windows-created DB.zip (with backslash separators) into a target dir."""
import os
import sys
import zipfile

src = sys.argv[1] if len(sys.argv) > 1 else "/tmp/DB-clean.zip"
dst = sys.argv[2] if len(sys.argv) > 2 else "/opt/stirling-pdf/customFiles/facilis-db"

os.makedirs(dst, exist_ok=True)
count = 0
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        rel = name.replace("\\", "/")
        if rel.endswith("/") or name.endswith("/"):
            os.makedirs(os.path.join(dst, rel), exist_ok=True)
            continue
        target = os.path.join(dst, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if os.path.isdir(target):
            continue
        with open(target, "wb") as f:
            f.write(z.read(name))
        count += 1
print(f"Extracted {count} files to {dst}")
