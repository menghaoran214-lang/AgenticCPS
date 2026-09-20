import request from '@/config/axios'

export type McpRiskLevel = 'READ_ONLY' | 'ATTRIBUTION_WRITE' | 'ASSET_WRITE' | 'ADMIN'

export interface McpServerVO {
  id?: number
  code?: string
  name: string
  type?: string
  endpoint?: string
  transportProtocol?: string
  authType?: string
  secretRef?: string
  location?: string
  timeoutMillis?: number
  status?: number
  healthStatus?: string
  lastHealthCheckAt?: string
  lastError?: string
}

export interface McpToolVO {
  id?: number
  serverId?: number
  code?: string
  name: string
  title?: string
  description?: string
  riskLevel?: McpRiskLevel
  syncStatus?: string
  status?: number
}

export interface McpPolicyVO {
  id?: number
  code?: string
  name?: string
  roleId?: number
  skillId?: number
  mcpServerIds?: string
  mcpToolIds?: string
  riskLevel?: McpRiskLevel
  requireConfirmation?: number
  status?: number
}

export interface McpLogVO {
  id: string
  createTime: string
  roleCode?: string
  roleVersion?: string
  skillCode?: string
  skillVersion?: string
  toolName?: string
  mcpClientName?: string
  policyResult?: string
  rejectReason?: string
  durationMs?: number
}

export interface McpStatsVO {
  total: number
  succeeded: number
  failed: number
  averageDurationMs?: number
  p95DurationMs?: number
  denied?: number
  roleUsage?: { code: string; total: number }[]
  skillUsage?: { code: string; total: number }[]
}

export const McpApi = {
  getStats: (params?: Record<string, unknown>) =>
    request.get<McpStatsVO>({ url: '/ai/mcp/log/stats', params }),
  getServerPage: (params: Record<string, unknown>) =>
    request.get({ url: '/ai/mcp/server/page', params }),
  createServer: (data: McpServerVO) => request.post({ url: '/ai/mcp/server/create', data }),
  updateServer: (data: McpServerVO) => request.put({ url: '/ai/mcp/server/update', data }),
  testServer: (id: number) => request.post({ url: '/ai/mcp/server/test', params: { id } }),
  syncTools: (id: number) => request.post({ url: '/ai/mcp/server/sync-tools', params: { id } }),
  updateServerStatus: (id: number, enabled: boolean) =>
    request.post({ url: '/ai/mcp/server/status', params: { id, status: enabled ? 0 : 1 } }),
  getToolPage: (params: Record<string, unknown>) =>
    request.get({ url: '/ai/mcp/tool/page', params }),
  saveTool: (data: McpToolVO) => request.post({ url: '/ai/mcp/tool/save', data }),
  getPolicy: (params: Record<string, unknown>) =>
    request.get({ url: '/ai/mcp/policy/get', params }),
  getPolicyPage: (params: Record<string, unknown>) =>
    request.get({ url: '/ai/mcp/policy/page', params }),
  savePolicy: (data: McpPolicyVO) => request.post({ url: '/ai/mcp/policy/save', data }),
  getLogPage: (params: Record<string, unknown>) => request.get({ url: '/ai/mcp/log/page', params })
}
