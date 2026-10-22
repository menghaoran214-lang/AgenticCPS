import request from '@/config/axios'

export interface CpsAdminPromoterVO {
  id: number
  memberId: number
  nickname?: string
  mobile?: string
  inviteCode?: string
  parentMemberId?: number
  parentNickname?: string
  grandparentMemberId?: number
  promoterLevel: number
  promoterStatus: number
  teamCountL1: number
  teamCountL2: number
  teamOrderCount: number
  totalRebateCent: number
  totalRebateYuan: number
  totalSplitCent: number
  totalSplitYuan: number
  bindTime?: Date
  createTime?: Date
}

export interface CpsAdminPromoterPageReqVO extends PageParam {
  memberId?: number
  inviteCode?: string
  promoterLevel?: number
  promoterStatus?: number
}

export interface CpsAdminPromoterRebindReqVO {
  memberId: number
  newParentMemberId?: number | null
  reason?: string
}

export interface CpsAdminPromoterUpdateReqVO {
  memberId: number
  promoterLevel?: number
  promoterStatus?: number
}

// 获得推广员分页列表
export const getPromoterPage = (params: CpsAdminPromoterPageReqVO) => {
  return request.get({ url: '/cps/promoter/page', params })
}

// 管理员人工换绑
export const rebindPromoter = (data: CpsAdminPromoterRebindReqVO) => {
  return request.put({ url: '/cps/promoter/rebind', data })
}

// 修改推广员状态与层级
export const updatePromoterStatus = (data: CpsAdminPromoterUpdateReqVO) => {
  return request.put({ url: '/cps/promoter/update-status', data })
}
