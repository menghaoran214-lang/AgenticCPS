import request from '@/config/axios'

export interface CpsSplitRuleVO {
  id?: number
  ruleName: string
  platformCode?: string
  memberLevelId?: number
  platformShareRate: number
  selfRebateRate: number
  parentRebateRate: number
  grandparentRebateRate: number
  partnerSpreadRate?: number
  attributionPriority?: number
  autoLockInviterOnFirstOrder?: number
  freezeDays?: number
  status: number
  priority?: number
  createTime?: Date
}

export interface CpsSplitRulePageReqVO extends PageParam {
  ruleName?: string
  platformCode?: string
  status?: number
}

// 获得分账规则分页
export const getSplitRulePage = (params: CpsSplitRulePageReqVO) => {
  return request.get({ url: '/cps/split-rule/page', params })
}

// 获得分账规则详情
export const getSplitRule = (id: number) => {
  return request.get({ url: '/cps/split-rule/get?id=' + id })
}

// 创建分账规则
export const createSplitRule = (data: CpsSplitRuleVO) => {
  return request.post({ url: '/cps/split-rule/create', data })
}

// 修改分账规则
export const updateSplitRule = (data: CpsSplitRuleVO) => {
  return request.put({ url: '/cps/split-rule/update', data })
}

// 删除分账规则
export const deleteSplitRule = (id: number) => {
  return request.delete({ url: '/cps/split-rule/delete?id=' + id })
}
