import request from '@/config/axios'

export interface CpsPublishTaskVO {
  id?: number
  taskCode?: string
  taskName: string
  sourceType: string
  sourceId?: number
  channelCode: string
  targetType: string
  targetIds?: string
  contentTemplate: string
  linkTemplate?: string
  templateCode?: string
  cronExpression?: string
  startTime?: string
  endTime?: string
  frequencyLimitPerDay?: number
  quietStartTime?: string
  quietEndTime?: string
  status?: string
  lastDispatchTime?: string
  nextDispatchTime?: string
  remark?: string
  createTime?: string
}

export interface CpsPublishTaskPageReqVO {
  pageNo: number
  pageSize: number
  taskCode?: string
  taskName?: string
  channelCode?: string
  sourceType?: string
  targetType?: string
  status?: string
}

export interface CpsPublishLogVO {
  id: number
  taskId: number
  taskCode: string
  channelCode: string
  memberId: number
  contentSnapshot?: string
  linkUrl?: string
  status: string
  errorMsg?: string
  sentAt?: string
}

export interface CpsPublishLogPageReqVO {
  pageNo: number
  pageSize: number
  taskId?: number
  taskCode?: string
  channelCode?: string
  memberId?: number
  status?: string
}

export interface CpsPublishDispatchRespVO {
  taskId?: number
  taskCode?: string
  successCount: number
  failedCount: number
  skippedCount: number
  message: string
}

export const getCpsPublishTaskPage = async (params: CpsPublishTaskPageReqVO) => {
  return await request.get({ url: '/cps/publish-task/page', params })
}

export const getCpsPublishTask = async (id: number) => {
  return await request.get({ url: '/cps/publish-task/get?id=' + id })
}

export const createCpsPublishTask = async (data: CpsPublishTaskVO) => {
  return await request.post({ url: '/cps/publish-task/create', data })
}

export const updateCpsPublishTask = async (data: CpsPublishTaskVO) => {
  return await request.put({ url: '/cps/publish-task/update', data })
}

export const deleteCpsPublishTask = async (id: number) => {
  return await request.delete({ url: '/cps/publish-task/delete?id=' + id })
}

export const publishCpsPublishTask = async (id: number) => {
  return await request.put({ url: '/cps/publish-task/publish?id=' + id })
}

export const offlineCpsPublishTask = async (id: number) => {
  return await request.put({ url: '/cps/publish-task/offline?id=' + id })
}

export const dispatchCpsPublishTask = async (id: number) => {
  return await request.post({ url: '/cps/publish-task/dispatch?id=' + id })
}

export const getCpsPublishLogPage = async (params: CpsPublishLogPageReqVO) => {
  return await request.get({ url: '/cps/publish-task/log-page', params })
}
