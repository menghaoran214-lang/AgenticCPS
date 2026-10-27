import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../sheep/helper/cpsInviteCore.js', import.meta.url), 'utf8');
const { createInviteManager, buildInviteLink } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

function setup(overrides = {}) {
  const saved = new Map();
  let loggedIn = true;
  let time = 1000;
  const calls = [];
  const manager = createInviteManager({
    get: (key) => saved.get(key),
    set: (key, value) => saved.set(key, value),
    remove: (key) => saved.delete(key),
    isLogin: () => loggedIn,
    now: () => time,
    bind: async (code) => { calls.push(code); return { code: 0, data: true }; },
    ...overrides,
  });
  return { manager, saved, calls, logout: () => { loggedIn = false; }, login: () => { loggedIn = true; },
    advance: () => { time += 8 * 86400000; } };
}

test('promotion link keeps H5 deployment subdirectory and encodes only the invite code', () => {
  assert.equal(buildInviteLink('https://shop.test/h5/?old=1#/old', 'ABCD1234'),
    'https://shop.test/h5/#/pages/cps/goods?inviteCode=ABCD1234');
  assert.equal(buildInviteLink('javascript:alert(1)', 'ABCD1234'), '');
  assert.equal(buildInviteLink('https://shop.test/', '<script>'), '');
});

test('guest keeps invite until a verified login then binds it once', async () => {
  const s = setup(); s.logout();
  assert.equal(s.manager.capture(' ABCD1234 '), true);
  assert.equal(await s.manager.bindPending(), 'login-required');
  assert.equal(s.calls.length, 0);
  s.login();
  assert.equal(await s.manager.bindPending(), 'bound');
  assert.deepEqual(s.calls, ['ABCD1234']);
  assert.equal(await s.manager.bindPending(), 'none');
});

test('a later landing cannot replace first-touch attribution', async () => {
  const s = setup();
  s.manager.capture('FIRST123');
  s.manager.capture('OTHER456');
  await s.manager.bindPending();
  assert.deepEqual(s.calls, ['FIRST123']);
});

test('expired invite can be replaced and is never bound', async () => {
  const s = setup();
  s.manager.capture('FIRST123'); s.advance();
  assert.equal(await s.manager.bindPending(), 'none');
  s.manager.capture('OTHER456');
  assert.equal(await s.manager.bindPending(), 'bound');
  assert.deepEqual(s.calls, ['OTHER456']);
});

test('network failures retain pending attribution for retry', async () => {
  let attempts = 0;
  const s = setup({ bind: async () => {
    if (++attempts === 1) throw new Error('offline');
    return { code: 0, data: true };
  } });
  s.manager.capture('ABCD1234');
  assert.equal(await s.manager.bindPending(), 'retry');
  assert.equal(await s.manager.bindPending(), 'bound');
});

test('server rejection clears the invalid invite without claiming success', async () => {
  const s = setup({ bind: async () => ({ code: 0, data: false }) });
  s.manager.capture('ABCD1234');
  assert.equal(await s.manager.bindPending(), 'rejected');
  assert.equal(await s.manager.bindPending(), 'none');
});

test('concurrent login and purchase share a single binding request', async () => {
  let release; let count = 0;
  const s = setup({ bind: async () => {
    count++;
    return new Promise(resolve => { release = resolve; });
  } });
  s.manager.capture('ABCD1234');
  const first = s.manager.bindPending();
  const second = s.manager.bindPending();
  release({ code: 0, data: true });
  assert.deepEqual(await Promise.all([first, second]), ['bound', 'bound']);
  assert.equal(count, 1);
});

test('invalid codes and malformed persisted values never reach the binding API', async () => {
  const s = setup();
  assert.equal(s.manager.capture('a'.repeat(100)), false);
  s.saved.set('cps_pending_invite', { code: '<script>', expiresAt: 100000 });
  assert.equal(await s.manager.bindPending(), 'none');
  assert.deepEqual(s.calls, []);
});

test('history routing produces a real goods path under the configured H5 base', () => {
  assert.equal(buildInviteLink('https://shop.test/h5/', 'ABCD1234'),
    'https://shop.test/h5/pages/cps/goods?inviteCode=ABCD1234');
});

test('history invitation from a nested page keeps only the H5 deployment root', () => {
  assert.equal(buildInviteLink('https://shop.test/h5/pages/cps/promoter/invite?old=1', 'ABCD1234'),
    'https://shop.test/h5/pages/cps/goods?inviteCode=ABCD1234');
});
