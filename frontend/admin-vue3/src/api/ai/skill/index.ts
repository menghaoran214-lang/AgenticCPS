import request from '@/config/axios'

export type SkillPublishStatus = 'DRAFT' | 'REVIEWING' | 'PUBLISHED' | 'ARCHIVED'

export interface SkillVO {
  id?: number
  code?: string
  name: string
  category?: string
  description?: string
  channelScope?: string
  riskLevel?: string
  status?: number
  currentVersion?: number
  publishStatus?: SkillPublishStatus
}

export interface SkillVersionVO {
  id?: number
  skillId?: number
  version?: number
  systemPrompt?: string
  inputSchema?: string
  outputTemplate?: string
  confirmationPolicy?: string
  publishStatus?: SkillPublishStatus
  changeLog?: string
  revision?: number
  expectedRevision?: number
}

export interface SkillBindingVO {
  id?: number
  skillVersionId?: number
  bindingType: 'LOCAL_TOOL' | 'MCP_TOOL' | 'KNOWLEDGE'
  localToolName?: string
  mcpServerId?: number
  mcpToolName?: string
  knowledgeBaseId?: number
  enabled?: number
}

export const SkillApi = {
  getPage: (params: Record<string, unknown>) => request.get({ url: '/ai/skill/page', params }),
  create: (data: SkillVO) => request.post({ url: '/ai/skill/create', data }),
  update: (data: SkillVO) => request.put({ url: '/ai/skill/update', data }),
  getVersions: (skillId: number) =>
    request.get<SkillVersionVO[]>({ url: '/ai/skill/version/list', params: { skillId } }),
  createVersion: (data: SkillVersionVO) => request.post({ url: '/ai/skill/version/create', data }),
  updateVersion: (data: SkillVersionVO) => request.put({ url: '/ai/skill/version/update', data }),
  publishVersion: (id: number) =>
    request.post({ url: '/ai/skill/version/publish', params: { id } }),
  archiveVersion: (id: number) =>
    request.post({ url: '/ai/skill/version/archive', params: { id } }),
  reviewVersion: (id: number) => request.post({ url: '/ai/skill/version/review', params: { id } }),
  rollbackVersion: (skillId: number, version: number) =>
    request.post({ url: '/ai/skill/version/rollback', params: { skillId, version } }),
  test: (data: {
    skillVersionId: number
    input: string
    executeModel?: boolean
    modelId?: number
  }) => request.post({ url: '/ai/skill/test', data }),
  getBindings: (params: Record<string, unknown>) =>
    request.get({ url: '/ai/skill/bindings', params }),
  saveBindings: (skillVersionId: number, expectedRevision: number, data: SkillBindingVO[]) =>
    request.post({ url: '/ai/skill/bindings', params: { skillVersionId, expectedRevision }, data })
}
