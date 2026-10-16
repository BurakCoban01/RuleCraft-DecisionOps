"""Verify a disposable local RuleCraft edge with an operator-supplied identity."""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:50113')
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.url)
    if parsed.scheme != 'http' or parsed.hostname not in {'127.0.0.1', 'localhost', '::1'}:
        parser.error('Use the loopback HTTP edge of a disposable smoke environment.')
    identity = {key: os.environ.get('RULECRAFT_SMOKE_' + key.upper(), '')
                for key in ('workspace', 'email', 'password')}
    if not all(identity.values()):
        parser.error('Set RULECRAFT_SMOKE_WORKSPACE, RULECRAFT_SMOKE_EMAIL, and RULECRAFT_SMOKE_PASSWORD.')

    cookies = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))

    def request(path, method='GET', body=None, expected=200):
        headers = {'Accept': 'application/json'}
        if body is not None:
            headers['Content-Type'] = 'application/json'
        if method != 'GET':
            token = next((cookie.value for cookie in cookies if cookie.name == 'XSRF-TOKEN'), None)
            if token:
                headers['X-XSRF-TOKEN'] = urllib.parse.unquote(token)
        req = urllib.request.Request(args.url.rstrip('/') + path,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers=headers, method=method)
        try:
            response = client.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if response.status != expected:
                raise RuntimeError(f'{method} {path}: expected {expected}, received {response.status}')
            payload = response.read()
            content_type = response.headers.get('Content-Type', '')
            return (json.loads(payload) if payload and 'json' in content_type else None), response.headers

    _, headers = request('/')
    for header, expected in [('X-Frame-Options', 'DENY'), ('X-Content-Type-Options', 'nosniff'),
                             ('Referrer-Policy', 'no-referrer')]:
        if headers.get(header) != expected:
            raise RuntimeError(f'Missing edge hardening header: {header}')
    request('/healthz')
    request('/actuator/health', expected=404)
    request('/v3/api-docs', expected=404)
    request('/api/v1/projects', expected=401)
    request('/api/v1/session')
    session, _ = request('/api/v1/session/login', 'POST', identity)
    if not session.get('authenticated'):
        raise RuntimeError('Sign-in did not create an authenticated session.')
    request('/api/v1/session')
    project, _ = request('/api/v1/projects', 'POST', {
        'projectKey': 'S' + uuid.uuid4().hex[:10].upper(),
        'name': 'Container smoke decisions', 'description': 'Synthetic verification data.'}, expected=201)
    projects, _ = request('/api/v1/projects')
    if project['id'] not in {item['id'] for item in projects}:
        raise RuntimeError('Created project was not persisted.')
    operations, _ = request('/api/v1/operations')
    if operations['posture']['status'] != 'READY' or operations['posture']['schemaVersion'] != '16':
        raise RuntimeError('Operational posture or schema version did not match the application.')
    request('/api/v1/session', 'DELETE')
    request('/api/v1/projects', expected=401)
    print('Container smoke passed: edge, probes, authentication, persisted project, V16, Operations, and sign-out.')


if __name__ == '__main__':
    main()
