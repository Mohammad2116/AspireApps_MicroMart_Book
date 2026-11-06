#!/usr/bin/env python3
"""Validate local book excerpt provenance without executing or altering source."""
import hashlib
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
chapters = json.loads((root / 'docs/book-v2-excerpts.json').read_text())
assert [c['chapter'] for c in chapters] == list(range(1, 13))
base = 'https://github.com/Mohammad2116/AspireApps_MicroMart_Book/blob/book-v2.0.0/'
for chapter in chapters:
    excerpt = chapter['excerpt']
    path = root / excerpt['path']
    actual = '\n'.join(path.read_text().splitlines()[excerpt['start'] - 1:excerpt['end']])
    assert actual == excerpt['text'], excerpt['path']
    assert hashlib.sha256(actual.encode()).hexdigest() == excerpt['sha256']
    assert chapter['source_url'] == base + excerpt['path'] + f"#L{excerpt['start']}-L{excerpt['end']}"
    assert (root / chapter['test']).is_file(), chapter['test']
    for source in chapter['files']:
        assert (root / source).is_file(), source
        assert chapter['file_urls'][source] == base + source
print('12 chapters: exact excerpts, source/test paths, hashes and fixed-tag URL syntax verified locally.')
