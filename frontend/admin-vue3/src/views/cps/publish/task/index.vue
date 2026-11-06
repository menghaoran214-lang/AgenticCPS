<template>
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="90px"
    >
      <el-form-item label="任务名称" prop="taskName">
        <el-input
          v-model="queryParams.taskName"
          placeholder="请输入任务名称"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="触达渠道" prop="channelCode">
        <el-select v-model="queryParams.channelCode" placeholder="全部" clearable class="!w-150px">
          <el-option label="短信" value="SMS" />
          <el-option label="站内信" value="NOTIFY" />
          <el-option label="文案看板" value="CLIPBOARD" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已上线" value="ONLINE" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" /> 搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> 重置
        </el-button>
        <el-button
          type="primary"
          @click="openForm()"
          v-hasPermi="['cps:publish-task:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增投放任务
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="任务编码" align="center" prop="taskCode" width="180" show-overflow-tooltip />
      <el-table-column label="任务名称" align="center" prop="taskName" min-width="160" show-overflow-tooltip />
      <el-table-column label="触达渠道" align="center" prop="channelCode" width="110">
        <template #default="scope">
          <el-tag size="small">{{ channelLabel(scope.row.channelCode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标人群" align="center" prop="targetType" width="110">
        <template #default="scope">{{ targetLabel(scope.row.targetType) }}</template>
      </el-table-column>
      <el-table-column label="Cron" align="center" prop="cronExpression" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.cronExpression || '仅手工触发' }}</template>
      </el-table-column>
      <el-table-column label="每日上限" align="center" prop="frequencyLimitPerDay" width="100">
        <template #default="scope">
          {{ scope.row.frequencyLimitPerDay > 0 ? scope.row.frequencyLimitPerDay : '不限' }}
        </template>
      </el-table-column>
      <el-table-column label="下次投放" align="center" prop="nextDispatchTime" width="170">
        <template #default="scope">{{ scope.row.nextDispatchTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="320" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm(scope.row)"
            v-hasPermi="['cps:publish-task:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="primary"
            @click="openLog(scope.row)"
            v-hasPermi="['cps:publish-task:query']"
          >
            投放记录
          </el-button>
          <el-button
            link
            type="primary"
            @click="handleDispatch(scope.row)"
            v-hasPermi="['cps:publish-task:dispatch']"
          >
            立即投放
          </el-button>
          <el-button
            v-if="scope.row.status !== 'ONLINE'"
            link
            type="success"
            @click="handlePublish(scope.row)"
            v-hasPermi="['cps:publish-task:publish']"
          >
            上线
          </el-button>
          <el-button
            v-else
            link
            type="warning"
            @click="handleOffline(scope.row)"
            v-hasPermi="['cps:publish-task:offline']"
          >
            下线
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['cps:publish-task:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <el-dialog v-model="dialogVisible" :title="formData.id ? '编辑投放任务' : '新增投放任务'" width="760px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="formData.taskName" placeholder="例如：每日返利早报" />
      </el-form-item>
      <el-form-item label="触达渠道" prop="channelCode">
        <el-select v-model="formData.channelCode" placeholder="请选择触达渠道" class="!w-100%">
          <el-option label="短信" value="SMS" />
          <el-option label="站内信" value="NOTIFY" />
          <el-option label="文案看板（运营手动复制）" value="CLIPBOARD" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标人群" prop="targetType">
        <el-select v-model="formData.targetType" placeholder="请选择目标人群" class="!w-100%">
          <el-option label="指定会员" value="SPECIFIED" />
          <el-option label="全部返利会员" value="REBATE_MEMBER" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="formData.targetType === 'SPECIFIED'"
        label="会员 ID"
        prop="targetIds"
      >
        <el-input
          v-model="formData.targetIds"
          type="textarea"
          :rows="2"
          placeholder="多个会员 ID 用英文逗号分隔，例如 1001,1002"
        />
      </el-form-item>
      <el-form-item label="渠道模板编码" prop="templateCode">
        <el-input
          v-model="formData.templateCode"
          placeholder="短信 / 站内信模板编码，文案看板可留空"
        />
      </el-form-item>
      <el-form-item label="消息模板" prop="contentTemplate">
        <el-input
          v-model="formData.contentTemplate"
          type="textarea"
          :rows="4"
          placeholder="支持占位符 {member_id}、{task_name}、{url}"
        />
      </el-form-item>
      <el-form-item label="推广链接模板" prop="linkTemplate">
        <el-input v-model="formData.linkTemplate" placeholder="例如 https://你的域名/p/12345，需为完整链接，不支持占位符" />
      </el-form-item>
      <el-form-item label="Cron 表达式" prop="cronExpression">
        <el-input
          v-model="formData.cronExpression"
          placeholder="Spring 格式：秒 分 时 日 月 周，例如 0 0 9 * * ?；留空表示仅手工触发"
        />
      </el-form-item>
      <el-form-item label="投放开始时间" prop="startTime">
        <el-date-picker
          v-model="formData.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="留空表示立即生效"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item label="投放结束时间" prop="endTime">
        <el-date-picker
          v-model="formData.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="留空表示长期有效"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item label="每日投放上限" prop="frequencyLimitPerDay">
        <el-input-number v-model="formData.frequencyLimitPerDay" :min="0" :max="100" />
        <span class="ml-10px text-12px text-gray-500">0 表示不限制，按会员维度计算</span>
      </el-form-item>
      <el-form-item label="免打扰时段">
        <el-time-picker
          v-model="formData.quietStartTime"
          value-format="HH:mm:ss"
          placeholder="开始"
          class="!w-160px"
        />
        <span class="mx-10px">至</span>
        <el-time-picker
          v-model="formData.quietEndTime"
          value-format="HH:mm:ss"
          placeholder="结束"
          class="!w-160px"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="logVisible" title="投放记录" width="920px">
    <el-table v-loading="logLoading" :data="logList" max-height="420">
      <el-table-column label="会员 ID" align="center" prop="memberId" width="110" />
      <el-table-column label="渠道" align="center" prop="channelCode" width="100">
        <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
      </el-table-column>
      <el-table-column label="内容快照" align="center" prop="contentSnapshot" min-width="220" show-overflow-tooltip />
      <el-table-column label="链接" align="center" prop="linkUrl" min-width="180" show-overflow-tooltip />
      <el-table-column label="结果" align="center" prop="status" width="110">
        <template #default="scope">
          <el-tag :type="logTagType(scope.row.status)">{{ logLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原因" align="center" prop="errorMsg" min-width="150" show-overflow-tooltip />
      <el-table-column label="投放时间" align="center" prop="sentAt" width="170" />
    </el-table>
    <Pagination
      :total="logTotal"
      v-model:page="logQuery.pageNo"
      v-model:limit="logQuery.pageSize"
      @pagination="getLogList"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import {
  createCpsPublishTask,
  deleteCpsPublishTask,
  dispatchCpsPublishTask,
  getCpsPublishLogPage,
  getCpsPublishTaskPage,
  offlineCpsPublishTask,
  publishCpsPublishTask,
  updateCpsPublishTask,
  type CpsPublishLogPageReqVO,
  type CpsPublishTaskPageReqVO,
  type CpsPublishTaskVO
} from '@/api/cps/publishTask'

defineOptions({ name: 'CpsPublishTask' })

const { t } = useI18n()
const message = useMessage()

const loading = ref(true)
const list = ref<CpsPublishTaskVO[]>([])
const total = ref(0)

const queryParams = reactive<CpsPublishTaskPageReqVO>({
  pageNo: 1,
  pageSize: 10
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await getCpsPublishTaskPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const formData = reactive<CpsPublishTaskVO>({
  taskName: '',
  sourceType: 'MANUAL',
  channelCode: 'CLIPBOARD',
  targetType: 'SPECIFIED',
  targetIds: '',
  contentTemplate: '',
  frequencyLimitPerDay: 0
})
const formRules = {
  taskName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
  channelCode: [{ required: true, message: '触达渠道不能为空', trigger: 'change' }],
  targetType: [{ required: true, message: '目标人群不能为空', trigger: 'change' }],
  contentTemplate: [{ required: true, message: '消息模板不能为空', trigger: 'blur' }],
  targetIds: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (formData.targetType === 'SPECIFIED' && !value) {
          callback(new Error('指定会员时必须填写会员 ID'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openForm = (row?: CpsPublishTaskVO) => {
  if (row) {
    Object.assign(formData, row)
  } else {
    Object.assign(formData, {
      id: undefined,
      taskCode: undefined,
      taskName: '',
      sourceType: 'MANUAL',
      sourceId: undefined,
      channelCode: 'CLIPBOARD',
      targetType: 'SPECIFIED',
      targetIds: '',
      contentTemplate: '',
      linkTemplate: '',
      templateCode: '',
      cronExpression: '',
      startTime: undefined,
      endTime: undefined,
      frequencyLimitPerDay: 0,
      quietStartTime: undefined,
      quietEndTime: undefined,
      status: undefined,
      remark: ''
    })
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = { ...formData, sourceType: 'MANUAL' }
    if (payload.id) {
      await updateCpsPublishTask(payload)
    } else {
      await createCpsPublishTask(payload)
    }
    message.success(t('common.createSuccess'))
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

const handlePublish = async (row: CpsPublishTaskVO) => {
  await publishCpsPublishTask(row.id!)
  message.success('已上线')
  await getList()
}

const handleOffline = async (row: CpsPublishTaskVO) => {
  await offlineCpsPublishTask(row.id!)
  message.success('已下线')
  await getList()
}

const handleDispatch = async (row: CpsPublishTaskVO) => {
  const result = await dispatchCpsPublishTask(row.id!)
  message.success(
    `执行完成：成功 ${result.successCount}，失败 ${result.failedCount}，跳过 ${result.skippedCount}（${result.message}）`
  )
  await getList()
}

const handleDelete = async (row: CpsPublishTaskVO) => {
  await message.delConfirm()
  await deleteCpsPublishTask(row.id!)
  message.success(t('common.delSuccess'))
  await getList()
}

const logVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logTotal = ref(0)
const logQuery = reactive<CpsPublishLogPageReqVO>({
  pageNo: 1,
  pageSize: 10
})

const openLog = async (row: CpsPublishTaskVO) => {
  logQuery.taskId = row.id
  logQuery.pageNo = 1
  logVisible.value = true
  await getLogList()
}

const getLogList = async () => {
  logLoading.value = true
  try {
    const data = await getCpsPublishLogPage(logQuery)
    logList.value = data.list
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

const channelLabel = (code?: string) => {
  if (code === 'SMS') return '短信'
  if (code === 'NOTIFY') return '站内信'
  if (code === 'CLIPBOARD') return '文案看板'
  return code || '-'
}

const targetLabel = (code?: string) => {
  if (code === 'SPECIFIED') return '指定会员'
  if (code === 'REBATE_MEMBER') return '全部返利会员'
  return code || '-'
}

const statusLabel = (status?: string) => {
  if (status === 'DRAFT') return '草稿'
  if (status === 'ONLINE') return '已上线'
  if (status === 'OFFLINE') return '已下线'
  return status || '-'
}

const statusTagType = (status?: string) => {
  if (status === 'ONLINE') return 'success'
  if (status === 'OFFLINE') return 'info'
  return 'warning'
}

const logLabel = (status?: string) => {
  if (status === 'ACCEPTED') return '已受理'
  if (status === 'PREVIEW') return '已生成文案'
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'SKIPPED') return '已跳过'
  if (status === 'PROCESSING') return '处理中'
  return status || '-'
}

const logTagType = (status?: string) => {
  if (status === 'ACCEPTED' || status === 'SUCCESS' || status === 'PREVIEW') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

onMounted(() => {
  getList()
})
</script>
