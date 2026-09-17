import request from '@/config/axios'

export const ChatMcpTestApi = {
  create: (data: { memberId: number; roleId?: number }) =>
    request.post<string>({ url: '/ai/chat/conversation/create-mcp-test', data })
}
