"""Subsets Noto Sans SC and Noto Serif SC to the characters the boards use, via the Google Fonts text= API.
Usage: python3 -I fetch_cjk.py <fonts dir> <html files...>"""
import os
import re
import subprocess
import sys
import urllib.parse

D = sys.argv[1]
chars = set()
for f in sys.argv[2:]:
    chars |= {c for c in open(f, encoding="utf-8").read() if ord(c) > 0x2000}
text = "".join(sorted(chars))
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
out = []
for fam, weights in [("Noto Sans SC", [400, 500, 700, 900]), ("Noto Serif SC", [400, 500, 600, 700, 900])]:
    for w in weights:
        url = "https://fonts.googleapis.com/css2?family=" + fam.replace(" ", "+") + f":wght@{w}&text=" + urllib.parse.quote(text)
        css = subprocess.run(["curl", "-sS", "-m", "60", "-A", UA, url], capture_output=True, text=True, check=True).stdout
        src = re.search(r"url\((https://[^)]+)\)", css).group(1)
        name = f"{fam.replace(' ', '')}-{w}.woff2"
        subprocess.run(["curl", "-sS", "-m", "120", "-o", os.path.join(D, name), src], check=True)
        out.append(f"@font-face {{ font-family: '{fam}'; font-weight: {w}; src: url(file://{os.path.join(D, name)}) format('woff2'); }}")
open(os.path.join(D, "cjk.css"), "w").write("\n".join(out))
print(len(text), "chars,", len(out), "faces")
