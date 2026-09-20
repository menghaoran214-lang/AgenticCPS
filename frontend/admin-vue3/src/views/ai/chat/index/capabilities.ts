export function readCapabilitySummary(value?: string) {
  if (!value) return null
  try {
    const snapshot = JSON.parse(value)
    if (
      snapshot.schemaVersion !== 2 ||
      !Array.isArray(snapshot.tools) ||
      !Array.isArray(snapshot.skills)
    )
      return null
    return {
      roleCode: String(snapshot.roleCode || ''),
      roleVersion: Number(snapshot.roleVersion || 0),
      riskLevel: String(snapshot.riskLevel || 'READ_ONLY'),
      skills: snapshot.skills.map(
        (skill: { code: string; version: number }) => `${skill.code} v${skill.version}`
      ),
      serviceCount: new Set(
        snapshot.tools.map((tool: { serverId?: number }) => tool.serverId).filter(Boolean)
      ).size,
      toolCount: snapshot.tools.length
    }
  } catch {
    return null
  }
}

export function isConfirmationExpired(expiresAt: string, now = Date.now()) {
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/.test(expiresAt) ? expiresAt : `${expiresAt}+08:00`
  const expires = Date.parse(zoned)
  return !Number.isFinite(expires) || expires <= now
}
