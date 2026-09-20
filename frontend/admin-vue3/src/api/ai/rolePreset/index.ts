import request from '@/config/axios'
import type { ChatRoleVO } from '@/api/ai/model/chatRole'

export interface RolePresetVO extends ChatRoleVO {
  presetCode?: string
  roleType?: 'PRESET' | 'CUSTOM'
  audience?: string
  channelScope?: string
  skillIds?: number[]
  mcpPolicyId?: number
  riskLevel?: string
  version?: number
  publishStatus?: string
  allowMutation?: boolean
}

export const RolePresetApi = {
  getList: (params?: Record<string, unknown>) =>
    request.get<RolePresetVO[]>({ url: '/ai/chat-role/preset-list', params }),
  getCapabilitySummary: (roleId: number) =>
    request.get({ url: '/ai/chat-role/capability-summary', params: { roleId } })
}
