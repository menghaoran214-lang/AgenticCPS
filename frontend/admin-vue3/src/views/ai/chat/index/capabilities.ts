interface CapabilityFallback {
  skillSnapshot?: string
  mcpClientName?: string
  roleCode?: string
  roleVersion?: number
}

export function readCapabilitySummary(value?: string, fallback: CapabilityFallback = {}) {
  try {
    const snapshot = value ? JSON.parse(value) : null
    if (
      snapshot &&
      snapshot.schemaVersion === 2 &&
      Array.isArray(snapshot.tools) &&
      Array.isArray(snapshot.skills)
    ) {
      const tools = snapshot.tools as Array<{
        name?: string
        serverId?: number
        serverCode?: string
        title?: string
        skillCode?: string
        skillVersion?: number
      }>
      const mcps = [
        ...new Map<string, { code: string; toolCount: number }>(
          tools
            .filter((tool) => tool.serverId != null || tool.serverCode)
            .map((tool) => {
              const code = String(tool.serverCode || `MCP #${tool.serverId}`)
              return [code, { code, toolCount: 0 }] as [string, { code: string; toolCount: number }]
            })
        ).values()
      ]
      tools.forEach((tool) => {
        const code = tool.serverCode || (tool.serverId != null ? `MCP #${tool.serverId}` : '')
        const mcp = mcps.find((item) => item.code === code)
        if (mcp) mcp.toolCount += 1
      })
      return {
        roleCode: String(snapshot.roleCode || fallback.roleCode || ''),
        roleVersion: Number(snapshot.roleVersion || fallback.roleVersion || 0),
        riskLevel: String(snapshot.riskLevel || 'READ_ONLY'),
        skills: snapshot.skills.map((skill: { code: string; version: number }) => ({
          code: String(skill.code || 'Skill'),
          version: Number(skill.version || 0)
        })),
        mcps,
        serviceCount: new Set(
          tools.map((tool) => tool.serverCode || tool.serverId).filter(Boolean)
        ).size,
        toolCount: tools.length,
        tools
      }
    }

    const skills = fallback.skillSnapshot ? JSON.parse(fallback.skillSnapshot) : []
    const normalizedSkills = Array.isArray(skills)
      ? skills.map((skill: { code?: string; version?: number }) => ({
          code: String(skill.code || 'Skill'),
          version: Number(skill.version || 0)
        }))
      : []
    const mcps = fallback.mcpClientName
      ? [{ code: fallback.mcpClientName, toolCount: 0 }]
      : []
    if (!normalizedSkills.length && !mcps.length) return null
    return {
      roleCode: String(fallback.roleCode || ''),
      roleVersion: Number(fallback.roleVersion || 0),
      riskLevel: 'READ_ONLY',
      skills: normalizedSkills,
      mcps,
      serviceCount: mcps.length,
      toolCount: 0,
      tools: []
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
