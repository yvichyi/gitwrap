"""Keeps the latin blocks of Google Fonts stylesheets, downloads their woff2 files next to them,
and writes one local stylesheet pointing at them. Usage: python3 -I fetch_fonts.py <fonts dir>"""
import os
import re
import subprocess
import sys

D = sys.argv[1]
out = []
for css in sorted(f for f in os.listdir(D) if f.endswith(".css") and f != "local.css"):
    text = open(os.path.join(D, css)).read()
    for comment, block in re.findall(r"/\* ([\w-]+) \*/\s*(@font-face \{.*?\})", text, re.S):
        if comment != "latin":
            continue
        url = re.search(r"url\((https://[^)]+)\)", block).group(1)
        family = re.search(r"font-family: '([^']+)'", block).group(1)
        weight = re.search(r"font-weight: (\d+)", block).group(1)
        style = re.search(r"font-style: (\w+)", block).group(1)
        name = f"{family.replace(' ', '')}-{weight}{'i' if style == 'italic' else ''}.woff2"
        path = os.path.join(D, name)
        if not os.path.exists(path):
            subprocess.run(["curl", "-sS", "-m", "60", "-o", path, url], check=True)
        out.append(block.replace(url, "file://" + path))
open(os.path.join(D, "local.css"), "w").write("\n".join(out))
print(len(out), "faces")
