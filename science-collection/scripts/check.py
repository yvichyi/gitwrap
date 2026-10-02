#!/usr/bin/env python3
"""Validate catalogue coverage, local links, HTML IDs, dependencies and JS syntax."""
import json
import re
import shutil
import subprocess
import tempfile
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


class Document(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids, self.links, self.dependencies, self.scripts = [], [], [], []
        self.lang, self.viewport, self.current = None, False, None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.append(attrs['id'])
        if tag == 'html':
            self.lang = attrs.get('lang')
        if tag == 'meta' and attrs.get('name') == 'viewport':
            self.viewport = True
        if tag == 'a' and 'href' in attrs:
            self.links.append(attrs['href'])
        if tag in ('script', 'img', 'iframe', 'source') and attrs.get('src'):
            self.dependencies.append(attrs['src'])
        if tag == 'link' and attrs.get('rel') == 'stylesheet':
            self.dependencies.append(attrs.get('href', ''))
        if tag == 'script' and not attrs.get('src') and attrs.get('type', '') not in ('application/json', 'application/ld+json'):
            self.current = ''

    def handle_data(self, value):
        if self.current is not None:
            self.current += value

    def handle_endtag(self, tag):
        if tag == 'script' and self.current is not None:
            self.scripts.append(self.current)
            self.current = None


def main():
    if not shutil.which('node'):
        raise SystemExit('Node.js is required for inline JavaScript syntax checks.')
    subprocess.run(['python3', str(ROOT / 'scripts/build_catalog.py'), '--check'], check=True)
    items = json.loads((ROOT / 'catalog.json').read_text(encoding='utf-8'))['items']
    documents, errors = {}, []
    for file in ROOT.glob('*.html'):
        doc = Document()
        doc.feed(file.read_text(encoding='utf-8'))
        documents[file.name] = doc
        duplicate = [key for key, n in Counter(doc.ids).items() if n > 1]
        if duplicate:
            errors.append(f'{file.name}: duplicate IDs: {duplicate}')
        if not doc.lang or not doc.viewport:
            errors.append(f'{file.name}: missing language or viewport metadata')
        for dependency in doc.dependencies:
            url = urlsplit(dependency)
            if url.scheme in ('http', 'https') or url.netloc:
                errors.append(f'{file.name}: external runtime dependency: {dependency}')
            elif url.scheme != 'data' and not (ROOT / unquote(url.path)).is_file():
                errors.append(f'{file.name}: missing dependency: {dependency}')
        for i, source in enumerate(doc.scripts, 1):
            with tempfile.TemporaryDirectory() as directory:
                script = Path(directory) / 'inline.js'
                script.write_text(source, encoding='utf-8')
                result = subprocess.run(['node', '--check', str(script)], capture_output=True, text=True)
                if result.returncode:
                    errors.append(f'{file.name}: script {i}: {result.stderr}')
    for name, doc in documents.items():
        for link in doc.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            target = unquote(url.path) or name
            path = ROOT / target
            if not path.is_file():
                errors.append(f'{name}: broken local link: {link}')
            elif url.fragment and target in documents and unquote(url.fragment) not in documents[target].ids:
                errors.append(f'{name}: missing anchor: {link}')
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: {len(items)} works + gallery; local links, IDs, metadata, offline dependencies and all inline JS checked.')


if __name__ == '__main__':
    main()
