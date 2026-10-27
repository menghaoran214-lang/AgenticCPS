const KEY = 'cps_pending_invite';
const TTL = 7 * 86400000;
const validCode = (code) => typeof code === 'string' && /^[A-Za-z0-9]{6,32}$/.test(code);

export function buildInviteLink(base, code) {
  if (!validCode(code) || typeof base !== 'string' || !/^https?:\/\/[^\s/?#]+(?:[/?#]|$)/i.test(base)) return '';
  try {
    const address = new URL(base);
    const pageIndex = address.pathname.indexOf('/pages/');
    const root = pageIndex >= 0 ? address.pathname.slice(0, pageIndex + 1)
      : address.pathname.endsWith('/') ? address.pathname : address.pathname + '/';
    const prefix = address.hash.startsWith('#/') ? '#/' : '';
    return address.origin + root + prefix + 'pages/cps/goods?inviteCode=' + encodeURIComponent(code);
  } catch (_) {
    return '';
  }
}

// The server resolves the invite code and obtains member identity from its session.
// Client storage is only a short-lived reminder, never an attribution authority.
export function createInviteManager({ get, set, remove, isLogin, bind, now = Date.now }) {
  let inFlight;
  function pending() {
    const value = get(KEY);
    if (!value) return null;
    if (!validCode(value.code) || !Number.isFinite(value.expiresAt) ||
        value.expiresAt <= now() || value.expiresAt > now() + TTL) {
      remove(KEY);
      return null;
    }
    return value;
  }
  return {
    capture(raw) {
      const code = typeof raw === 'string' ? raw.trim() : '';
      if (!validCode(code)) return false;
      if (!pending()) set(KEY, { code, expiresAt: now() + TTL });
      return true;
    },
    async bindPending() {
      if (inFlight) return inFlight;
      const invite = pending();
      if (!invite) return 'none';
      if (!isLogin()) return 'login-required';
      inFlight = (async () => {
        try {
          const result = await bind(invite.code);
          if (result?.code !== 0) return 'retry';
          if (result.data !== true && result.data !== false) return 'retry';
          if (get(KEY)?.code === invite.code) remove(KEY);
          return result.data ? 'bound' : 'rejected';
        } catch (_) {
          return 'retry';
        }
      })();
      try { return await inFlight; } finally { inFlight = undefined; }
    },
  };
}
