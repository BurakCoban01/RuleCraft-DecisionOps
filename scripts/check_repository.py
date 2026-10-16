"""Check tracked release artifacts, portable documentation, and workflow contracts."""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]


def tracked_files() -> list[str]:
    result = subprocess.run(['git', '-C', str(ROOT), 'ls-files', '-z'],
                            check=True, stdout=subprocess.PIPE)
    return [name for name in result.stdout.decode().split('\0') if name]


def main() -> int:
    files = tracked_files()
    failures: list[str] = []
    generated = {'node_modules', 'target', 'dist', 'test-results', 'playwright-report', '__pycache__'}
    private_path = re.compile(r'[A-Za-z]:[\\/](?:Users|home)[\\/][^\s/\\]+|(?<![A-Za-z0-9_/])/(?:Users|home)/[A-Za-z0-9_.-]+')
    key_material = re.compile(r'BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|\bAKIA[0-9A-Z]{16}\b')
    links_checked = 0
    for name in files:
        path = ROOT / name
        parts = set(Path(name).parts)
        if generated & parts or path.suffix.lower() in {'.log', '.dump', '.pfx', '.p12', '.key', '.bundle'}:
            failures.append(f'Generated or private artifact tracked: {name}')
        if path.name.startswith('.env') and path.name not in {'.env.example', '.env.production.example'}:
            failures.append(f'Populated environment file tracked: {name}')
        data = path.read_bytes()
        if b'\0' in data:
            continue
        try:
            text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            continue
        if private_path.search(text) or key_material.search(text):
            failures.append(f'Private path or credential material found: {name}')
        if path.name in {'.env.example', '.env.production.example'}:
            for line in text.splitlines():
                if '=' not in line or line.lstrip().startswith('#'):
                    continue
                key, value = line.split('=', 1)
                if re.search(r'(?:PASSWORD|SECRET|TOKEN)$', key) and value and not value.startswith('replace-with-'):
                    failures.append(f'Environment example has a non-placeholder credential: {name}')
        if name.startswith('.github/workflows/'):
            for action in re.findall(r'^\s*-\s*uses:\s*(\S+)', text, re.M):
                if not re.fullmatch(r'[^\s@]+@[0-9a-f]{40}', action):
                    failures.append(f'Workflow action is not pinned to a commit SHA: {name}')
            if re.search(r'^\s*continue-on-error:\s*true\s*$', text, re.M):
                failures.append(f'Workflow suppresses a failed gate: {name}')
        if path.suffix.lower() == '.md':
            for destination in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', text):
                destination = destination.strip().split(' "', 1)[0].strip('<>')
                parsed = urlsplit(destination)
                if parsed.scheme or destination.startswith('#'):
                    continue
                target = unquote(parsed.path)
                if not target:
                    continue
                resolved = (path.parent / target).resolve()
                if not resolved.is_relative_to(ROOT) or not resolved.exists():
                    failures.append(f'Broken or non-portable documentation link: {name} -> {target}')
                links_checked += 1

    mode = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-s', 'api/mvnw']).decode()
    if not mode.startswith('100755 '):
        failures.append('Maven wrapper must be executable on Unix.')
    required = ['README.md', 'ARCHITECTURE.md', 'SECURITY.md', 'docs/decision-engine.md',
                'docs/expression-language.md', 'docs/api.md', 'docs/testing.md', 'docs/operations.md']
    failures.extend(f'Missing release document: {name}' for name in required if name not in files)
    print(json.dumps({'tracked_files': len(files), 'relative_links_checked': links_checked,
                      'failures': failures, 'passed': not failures}, indent=2))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
