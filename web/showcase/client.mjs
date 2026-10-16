import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { request } from '@playwright/test';

export function localOrigin(value) {
  const url = new URL(value);
  assert(['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), 'Showcase tooling only accepts loopback hosts.');
  assert(['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && url.pathname === '/', 'Use a loopback HTTP origin without credentials or a path.');
  return url.origin;
}

export function requestId(key) {
  const bytes = createHash('sha256').update(`rulecraft-retail-lending-v1:${key}`).digest();
  bytes[6] = (bytes[6] & 15) | 80;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.subarray(0, 16).toString('hex');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

export class LocalClient {
  static async signIn(origin, workspace, email, password) {
    const context = await request.newContext({ baseURL: localOrigin(origin) });
    const client = new LocalClient(context);
    await client.call('/api/v1/session');
    await client.call('/api/v1/session/login', 'POST', { workspace, email, password });
    await client.call('/api/v1/session');
    return client;
  }
  constructor(context) { this.context = context; }
  async call(path, method = 'GET', data, extraHeaders = {}) {
    const state = await this.context.storageState();
    const token = state.cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
    const headers = { Accept: 'application/json', ...extraHeaders };
    if (token && method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(token.value);
    const response = await this.context.fetch(path, { method, data, headers });
    if (!response.ok()) {
      const problem = await response.json().catch(() => ({}));
      // Do not print request bodies, response bodies or credentials on failure.
      throw new Error(`${method} ${path}: ${response.status()} ${problem.code ?? ''} ${problem.detail ?? 'Request failed'}`);
    }
    return response.status() === 204 ? null : response.json();
  }
  async close() { await this.context.dispose(); }
}
