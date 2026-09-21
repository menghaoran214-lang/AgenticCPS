import request from '@/config/axios'

// AI 聊天角色 VO
export interface ChatRoleVO {
  id: number // 角色编号
  modelId: number // 模型编号
  name: string // 角色名称
  avatar: string // 角色头像
  category: string // 角色类别
  sort: number // 角色排序
  description: string // 角色描述
  systemMessage: string // 角色设定
  welcomeMessage: string // 角色设定
  publicStatus: boolean // 是否公开
  memberEnabled: boolean // 是否允许会员使用
  status: number // 状态
  knowledgeIds?: number[] // 引用的知识库 ID 列表
  toolIds?: number[] // 引用的工具 ID 列表
  mcpClientNames?: string[] // 引用的 MCP Client 名字列表
  roleType?: 'PRESET' | 'CUSTOM'
  presetCode?: string
  audience?: string
  channelScope?: string
  skillIds?: number[]
  mcpPolicyId?: number
  riskLevel?: string
  version?: number
  publishStatus?: string
  allowMutation?: boolean
}

// AI 聊天角色 分页请求 vo
export interface ChatRolePageReqVO {
  name?: string // 角色名称
  category?: string // 角色类别
  publicStatus: boolean // 是否公开
  pageNo: number // 是否公开
  pageSize: number // 是否公开
}

// AI 聊天角色 API
export interface ChatRoleVersionVO {
  id: number
  roleId: number
  version: number
  publishStatus: string
  definitionSnapshot: string
  creator: string
  createTime: string
}

export const ChatRoleApi = {
  getVersions: (roleId: number) =>
    request.get<ChatRoleVersionVO[]>({ url: '/ai/chat-role/version/list', params: { roleId } }),
  // 查询聊天角色分页
  getChatRolePage: async (params: any) => {
    return await request.get({ url: `/ai/chat-role/page`, params })
  },

  // 查询聊天角色详情
  getChatRole: async (id: number) => {
    return await request.get({ url: `/ai/chat-role/get?id=` + id })
  },

  // 新增聊天角色
  createChatRole: async (data: ChatRoleVO) => {
    return await request.post({ url: `/ai/chat-role/create`, data })
  },

  // 修改聊天角色
  updateChatRole: async (data: ChatRoleVO) => {
    return await request.put({ url: `/ai/chat-role/update`, data })
  },

  // 删除聊天角色
  deleteChatRole: async (id: number) => {
    return await request.delete({ url: `/ai/chat-role/delete?id=` + id })
  },

  // ======= chat 聊天

  // 获取 my role
  getMyPage: async (params: ChatRolePageReqVO) => {
    return await request.get({ url: `/ai/chat-role/my-page`, params })
  },

  // 获取角色分类
  getCategoryList: async () => {
    return await request.get({ url: `/ai/chat-role/category-list` })
  },

  // 获取已发布聊天角色，用于新建会话角色选择
  getPublishedList: async (params?: Record<string, unknown>) => {
    return await request.get<ChatRoleVO[]>({ url: `/ai/chat-role/published-list`, params })
  },

  // 获取角色在当前租户下的有效能力摘要
  getCapabilitySummary: async (roleId: number) => {
    return await request.get({ url: `/ai/chat-role/capability-summary`, params: { roleId } })
  },

  // 创建角色
  createMy: async (data: ChatRoleVO) => {
    return await request.post({ url: `/ai/chat-role/create-my`, data })
  },

  // 更新角色
  updateMy: async (data: ChatRoleVO) => {
    return await request.put({ url: `/ai/chat-role/update-my`, data })
  },

  // 删除角色 my
  deleteMy: async (id: number) => {
    return await request.delete({ url: `/ai/chat-role/delete-my?id=` + id })
  }
}
