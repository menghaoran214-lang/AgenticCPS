<template>
  <ContentWrap>
    <div class="mb-16px flex items-center justify-between">
      <div>
        <div class="text-18px font-bold">MCP 管理</div>
        <div class="mt-4px text-13px text-[var(--el-text-color-secondary)]">
          管理 MCP 服务、工具目录和运行健康状态
        </div>
      </div>
      <el-button v-hasPermi="['ai:mcp:manage']" type="primary" @click="openCreate"
        ><Icon icon="ep:plus" class="mr-4px" />注册服务</el-button
      >
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="服务注册" name="servers">
        <el-form :model="query" inline class="mb-12px">
          <el-form-item label="服务编码"
            ><el-input v-model="query.code" clearable @keyup.enter="loadServers"
          /></el-form-item>
          <el-form-item label="服务名称"
            ><el-input v-model="query.name" clearable @keyup.enter="loadServers"
          /></el-form-item>
          <el-form-item label="协议"
            ><el-select v-model="query.transportProtocol" clearable class="!w-150px"
              ><el-option label="SSE" value="SSE" /><el-option
                label="Streamable HTTP"
                value="STREAMABLE_HTTP" /></el-select
          ></el-form-item>
          <el-form-item label="状态"
            ><el-select v-model="query.status" clearable class="!w-120px"
              ><el-option label="启用" :value="0" /><el-option label="停用" :value="1" /></el-select
          ></el-form-item>
          <el-form-item
            ><el-button type="primary" @click="loadServers">查询</el-button></el-form-item
          >
        </el-form>
        <el-table v-loading="loading" :data="servers" stripe>
          <el-table-column prop="code" label="服务编码" min-width="140" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="location" label="位置" width="90" />
          <el-table-column prop="transportProtocol" label="传输协议" width="110" />
          <el-table-column prop="endpoint" label="Endpoint" min-width="230" show-overflow-tooltip />
          <el-table-column prop="healthStatus" label="健康状态" width="110">
            <template #default="scope"
              ><el-tag :type="healthTagType(scope.row.healthStatus)">{{
                scope.row.healthStatus || 'UNKNOWN'
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column prop="lastHealthCheckAt" label="最近检测" min-width="170" />
          <el-table-column
            prop="lastError"
            label="最近错误"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column prop="status" label="启用" width="90"
            ><template #default="scope"
              ><el-switch
                v-hasPermi="['ai:mcp:manage']"
                :model-value="scope.row.status === 0"
                @change="toggleServer(scope.row, $event)" /></template
          ></el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="scope">
              <el-button
                v-hasPermi="['ai:mcp:manage']"
                link
                type="primary"
                @click="testServer(scope.row)"
                >测试</el-button
              >
              <el-button
                v-hasPermi="['ai:mcp:manage']"
                link
                type="primary"
                @click="syncTools(scope.row)"
                >同步工具</el-button
              >
              <el-button v-hasPermi="['ai:mcp:manage']" link @click="openEdit(scope.row)"
                >编辑</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:page="query.pageNo"
          v-model:limit="query.pageSize"
          :total="total"
          @pagination="loadServers"
        />
      </el-tab-pane>
      <el-tab-pane label="工具目录" name="tools">
        <el-form :model="toolQuery" inline class="mb-12px">
          <el-form-item label="所属服务"
            ><el-select v-model="toolQuery.serverId" clearable class="!w-220px"
              ><el-option
                v-for="server in serverOptions"
                :key="server.id"
                :label="`${server.name} (${server.code})`"
                :value="server.id!" /></el-select
          ></el-form-item>
          <el-form-item label="工具名称"
            ><el-input v-model="toolQuery.name" clearable @keyup.enter="loadTools"
          /></el-form-item>
          <el-form-item><el-button type="primary" @click="loadTools">查询</el-button></el-form-item>
        </el-form>
        <el-table v-loading="toolLoading" :data="tools" stripe>
          <el-table-column prop="id" label="工具编号" width="100" />
          <el-table-column label="MCP 服务" min-width="180"
            ><template #default="{ row }">{{
              serverLabel(row.serverId)
            }}</template></el-table-column
          >
          <el-table-column prop="name" label="工具名称" min-width="180" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="riskLevel" label="风险等级" width="140"
            ><template #default="scope"
              ><el-tag :type="scope.row.riskLevel === 'READ_ONLY' ? 'success' : 'warning'">{{
                scope.row.riskLevel || '未分类'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="syncStatus" label="同步状态" width="120" />
          <el-table-column label="风险与启用" width="340"
            ><template #default="{ row }"
              ><el-select v-model="row.riskLevel" class="!w-160px" v-hasPermi="['ai:mcp:manage']"
                ><el-option
                  v-for="risk in risks"
                  :key="risk"
                  :value="risk"
                  :label="risk" /></el-select
              ><el-switch
                v-model="row.status"
                :active-value="0"
                :inactive-value="1"
                class="mx-8px"
                v-hasPermi="['ai:mcp:manage']"
              /><el-button v-hasPermi="['ai:mcp:manage']" link type="primary" @click="saveTool(row)"
                >保存</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <Pagination
          v-model:page="toolQuery.pageNo"
          v-model:limit="toolQuery.pageSize"
          :total="toolTotal"
          @pagination="loadTools"
        />
      </el-tab-pane>
      <el-tab-pane label="权限策略" name="policies">
        <el-form :model="policyQuery" inline class="mb-12px">
          <el-form-item label="编码"
            ><el-input v-model="policyQuery.code" clearable
          /></el-form-item>
          <el-form-item label="名称"
            ><el-input v-model="policyQuery.name" clearable
          /></el-form-item>
          <el-form-item
            ><el-button type="primary" @click="loadPolicies">查询</el-button></el-form-item
          >
          <el-button v-hasPermi="['ai:mcp:manage']" type="primary" @click="newPolicy"
            >新建策略</el-button
          >
        </el-form>
        <el-table v-loading="policyLoading" :data="policies" stripe>
          <el-table-column prop="code" label="编码" min-width="160" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="riskLevel" label="风险上限" width="160" />
          <el-table-column prop="requireConfirmation" label="用户确认" width="100"
            ><template #default="{ row }">{{
              row.requireConfirmation === 1 ? '需要' : '不需要'
            }}</template></el-table-column
          >
          <el-table-column prop="status" label="状态" width="90"
            ><template #default="{ row }"
              ><el-tag :type="row.status === 0 ? 'success' : 'info'">{{
                row.status === 0 ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="操作" width="90"
            ><template #default="{ row }"
              ><el-button
                v-hasPermi="['ai:mcp:manage']"
                link
                type="primary"
                @click="editPolicy(row)"
                >编辑</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <Pagination
          v-model:page="policyQuery.pageNo"
          v-model:limit="policyQuery.pageSize"
          :total="policyTotal"
          @pagination="loadPolicies"
        />
      </el-tab-pane>
      <el-tab-pane label="调用日志" name="logs">
        <div
          v-if="stats"
          class="grid grid-cols-2 gap-12px mb-16px lg:grid-cols-5"
          data-testid="mcp-stats"
        >
          <el-statistic title="累计调用" :value="stats.total" />
          <el-statistic title="成功率 (%)" :value="successRate" :precision="1" />
          <el-statistic title="失败次数" :value="stats.failed" />
          <el-statistic title="策略拒绝" :value="stats.denied || 0" />
          <el-statistic title="P95 延迟 (ms)" :value="stats.p95DurationMs || 0" />
        </div>
        <el-collapse v-if="stats" class="mb-16px"
          ><el-collapse-item title="角色与 Skill 使用量" name="usage"
            ><div class="grid grid-cols-2 gap-16px"
              ><el-table :data="stats.roleUsage || []"
                ><el-table-column prop="code" label="角色" /><el-table-column
                  prop="total"
                  label="调用量" /></el-table
              ><el-table :data="stats.skillUsage || []"
                ><el-table-column prop="code" label="Skill" /><el-table-column
                  prop="total"
                  label="调用量" /></el-table></div></el-collapse-item
        ></el-collapse>
        <el-form inline
          ><el-form-item label="角色编码"
            ><el-input v-model="logQuery.roleCode" clearable /></el-form-item
          ><el-form-item label="Skill 编码"
            ><el-input v-model="logQuery.skillCode" clearable /></el-form-item
          ><el-button @click="loadLogs">查询</el-button></el-form
        >
        <el-table :data="logs" v-loading="logLoading"
          ><el-table-column prop="createTime" label="调用时间" min-width="170" /><el-table-column
            prop="roleCode"
            label="角色" /><el-table-column prop="roleVersion" label="角色版本" /><el-table-column
            prop="skillCode"
            label="Skill" /><el-table-column
            prop="skillVersion"
            label="Skill 版本" /><el-table-column
            prop="mcpClientName"
            label="服务" /><el-table-column prop="toolName" label="工具" /><el-table-column
            prop="policyResult"
            label="策略判定" /><el-table-column
            prop="rejectReason"
            label="拒绝原因"
            show-overflow-tooltip /><el-table-column prop="durationMs" label="耗时(ms)"
        /></el-table>
        <Pagination
          v-model:page="logQuery.pageNo"
          v-model:limit="logQuery.pageSize"
          :total="logTotal"
          @pagination="loadLogs"
        />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog
    v-model="dialogVisible"
    :title="editing ? '编辑 MCP 服务' : '注册 MCP 服务'"
    width="560px"
  >
    <el-form ref="formRef" :model="form" :rules="serverRules" label-width="100px">
      <el-form-item label="服务编码" prop="code" required
        ><el-input v-model="form.code" :disabled="editing"
      /></el-form-item>
      <el-form-item label="服务名称" prop="name" required
        ><el-input v-model="form.name"
      /></el-form-item>
      <el-form-item label="服务位置" prop="location" required
        ><el-segmented v-model="form.location" :options="locationOptions" />
      </el-form-item>
      <el-form-item label="传输协议"
        ><el-select v-model="form.transportProtocol" class="w-full"
          ><el-option label="SSE" value="SSE" /><el-option
            label="Streamable HTTP"
            value="STREAMABLE_HTTP" /></el-select
      ></el-form-item>
      <el-form-item label="Endpoint" prop="endpoint"
        ><el-input v-model="form.endpoint"
      /></el-form-item>
      <el-form-item label="认证方式"
        ><el-select v-model="form.authType"
          ><el-option label="无认证" value="NONE" /><el-option
            label="Bearer 密钥引用"
            value="BEARER" /></el-select
      ></el-form-item>
      <el-form-item v-if="form.authType === 'BEARER'" label="密钥引用" prop="secretRef"
        ><el-input v-model="form.secretRef" :placeholder="secretPlaceholder"
      /></el-form-item>
      <el-form-item label="超时(ms)"
        ><el-input-number v-model="form.timeoutMillis" :min="1000" :max="60000"
      /></el-form-item>
      <el-form-item label="启用"
        ><el-switch v-model="form.status" :active-value="0" :inactive-value="1"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="dialogVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveServer">保存</el-button></template
    >
  </Dialog>

  <Dialog
    v-model="policyDialogVisible"
    :title="policy.id ? '编辑 MCP 策略' : '新建 MCP 策略'"
    width="680px"
  >
    <el-form label-width="110px">
      <el-form-item label="编码" required
        ><el-input v-model="policy.code" :disabled="Boolean(policy.id)"
      /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="policy.name" /></el-form-item>
      <el-form-item label="绑定角色编号"
        ><el-input-number v-model="policy.roleId" :min="1"
      /></el-form-item>
      <el-form-item label="绑定 Skill 编号"
        ><el-input-number v-model="policy.skillId" :min="1"
      /></el-form-item>
      <el-form-item label="服务白名单">
        <el-select v-model="policyServerIds" multiple filterable class="w-full">
          <el-option
            v-for="server in serverOptions"
            :key="server.id"
            :label="`${server.name} (${server.code})`"
            :value="server.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="工具白名单">
        <el-select v-model="policyToolIds" multiple filterable class="w-full">
          <el-option
            v-for="tool in policyToolOptions"
            :key="tool.id"
            :label="`${tool.title || tool.name} (${serverLabel(tool.serverId)})`"
            :value="tool.id!"
            :disabled="isPolicyToolDisabled(tool)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="风险上限"
        ><el-select v-model="policy.riskLevel"
          ><el-option v-for="risk in risks" :key="risk" :value="risk" :label="risk" /></el-select
      ></el-form-item>
      <el-form-item label="用户确认"
        ><el-switch v-model="policy.requireConfirmation" :active-value="1" :inactive-value="0"
      /></el-form-item>
      <el-form-item label="启用"
        ><el-switch v-model="policy.status" :active-value="0" :inactive-value="1"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="policyDialogVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="savePolicy">保存</el-button></template
    >
  </Dialog>
</template>

<script setup lang="ts">
import {
  McpApi,
  type McpServerVO,
  type McpToolVO,
  type McpPolicyVO,
  type McpStatsVO,
  type McpLogVO
} from '@/api/ai/mcp'

import { getTenantId } from '@/utils/auth'
import type { FormInstance, FormRules } from 'element-plus'

defineOptions({ name: 'AiMcpCenter' })
const secretPlaceholder = `env:AI_MCP_TENANT_${getTenantId()}_SERVICE_TOKEN`
const message = useMessage()
const activeTab = ref('servers')
const loading = ref(false)
const toolLoading = ref(false)
const policyLoading = ref(false)
const saving = ref(false)
const servers = ref<McpServerVO[]>([])
const serverOptions = ref<McpServerVO[]>([])
const tools = ref<McpToolVO[]>([])
const policyToolOptions = ref<McpToolVO[]>([])
const policies = ref<McpPolicyVO[]>([])
const total = ref(0)
const toolTotal = ref(0)
const policyTotal = ref(0)
const risks = ['READ_ONLY', 'ATTRIBUTION_WRITE', 'ASSET_WRITE', 'ADMIN']
const stats = ref<McpStatsVO>()
const successRate = computed(() =>
  stats.value?.total ? (stats.value.succeeded / stats.value.total) * 100 : 0
)
const logLoading = ref(false)
const logTotal = ref(0)
const logs = ref<McpLogVO[]>([])
const logQuery = reactive({ pageNo: 1, pageSize: 20, roleCode: '', skillCode: '' })
const policy = ref<McpPolicyVO>({ riskLevel: 'READ_ONLY', requireConfirmation: 0, status: 0 })
const policyServerIds = ref<number[]>([])
const policyToolIds = ref<number[]>([])
const policyDialogVisible = ref(false)
const policyQuery = reactive({ pageNo: 1, pageSize: 10, code: '', name: '' })
const parseIds = (value?: string) =>
  (value || '')
    .replace(/[\[\]]/g, '')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isSafeInteger(item) && item > 0)
const newPolicy = () => {
  policy.value = { riskLevel: 'READ_ONLY', requireConfirmation: 0, status: 0 }
  policyServerIds.value = []
  policyToolIds.value = []
  policyDialogVisible.value = true
}
const editPolicy = (row: McpPolicyVO) => {
  policy.value = { ...row }
  policyServerIds.value = parseIds(row.mcpServerIds)
  policyToolIds.value = parseIds(row.mcpToolIds)
  policyDialogVisible.value = true
}
const loadPolicies = async () => {
  policyLoading.value = true
  try {
    const data = await McpApi.getPolicyPage(policyQuery)
    policies.value = data.list || []
    policyTotal.value = data.total || 0
  } finally {
    policyLoading.value = false
  }
}
const savePolicy = async () => {
  if (!policy.value.code || !policy.value.name) return message.warning('请填写编码和名称')
  saving.value = true
  try {
    policy.value.mcpServerIds = policyServerIds.value.join(',')
    policy.value.mcpToolIds = policyToolIds.value.join(',')
    policy.value.id = await McpApi.savePolicy(policy.value)
    message.success('策略已保存')
    policyDialogVisible.value = false
    await loadPolicies()
  } finally {
    saving.value = false
  }
}
const saveTool = async (row: McpToolVO) => {
  await McpApi.saveTool(row)
  message.success('工具策略已保存')
}
const loadLogs = async () => {
  logLoading.value = true
  try {
    const [page, summary] = await Promise.all([
      McpApi.getLogPage(logQuery),
      McpApi.getStats(logQuery)
    ])
    stats.value = summary
    logs.value = page.list
    logTotal.value = page.total
  } finally {
    logLoading.value = false
  }
}
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  transportProtocol: '',
  status: undefined as number | undefined
})
const toolQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  serverId: undefined as number | undefined,
  name: ''
})
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const locationOptions = [
  { label: '远程', value: 'REMOTE' },
  { label: '本地', value: 'LOCAL' }
]
const tenantId = getTenantId()
const serverRules: FormRules = {
  code: [
    { required: true, message: '请输入服务编码', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_-]{1,64}$/,
      message: '仅支持字母、数字、下划线和连字符',
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  location: [{ required: true, message: '请选择服务位置', trigger: 'change' }],
  endpoint: [
    { required: true, message: '请输入 Endpoint', trigger: 'blur' },
    { type: 'url', message: '请输入有效的 HTTP(S) 地址', trigger: 'blur' }
  ],
  secretRef: [
    {
      validator: (_rule, value, callback) => {
        const pattern = new RegExp(`^env:AI_MCP_TENANT_${tenantId}_[A-Z0-9_]+$`)
        if (form.authType !== 'BEARER' || pattern.test(value || '')) callback()
        else callback(new Error('请输入当前租户的环境变量引用'))
      },
      trigger: 'blur'
    }
  ]
}
const form = reactive<McpServerVO>({
  name: '',
  code: '',
  location: 'REMOTE',
  authType: 'NONE',
  transportProtocol: 'SSE',
  timeoutMillis: 10000,
  status: 1
})

const healthTagType = (status?: string): 'success' | 'danger' | 'info' =>
  status === 'UP' ? 'success' : status === 'DOWN' ? 'danger' : 'info'
const serverLabel = (serverId?: number) => {
  const server = serverOptions.value.find((item) => item.id === serverId)
  return server ? `${server.name} (${server.code})` : serverId || '-'
}
const isPolicyToolDisabled = (tool: McpToolVO) =>
  !tool.serverId || !policyServerIds.value.includes(tool.serverId)
const loadOptions = async () => {
  const [serverPage, toolPage] = await Promise.all([
    McpApi.getServerPage({ pageNo: 1, pageSize: 100 }),
    McpApi.getToolPage({ pageNo: 1, pageSize: 100 })
  ])
  serverOptions.value = serverPage.list || []
  policyToolOptions.value = toolPage.list || []
}

const loadServers = async () => {
  loading.value = true
  try {
    const data = await McpApi.getServerPage(query)
    servers.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const loadTools = async () => {
  toolLoading.value = true
  try {
    const data = await McpApi.getToolPage(toolQuery)
    tools.value = data.list || []
    toolTotal.value = data.total || 0
  } finally {
    toolLoading.value = false
  }
}
const openCreate = () => {
  editing.value = false
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    location: 'REMOTE',
    transportProtocol: 'SSE',
    endpoint: '',
    authType: 'NONE',
    secretRef: '',
    timeoutMillis: 10000,
    status: 1
  })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}
const openEdit = (row: McpServerVO) => {
  editing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}
const saveServer = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    editing.value ? await McpApi.updateServer(form) : await McpApi.createServer(form)
    message.success('保存成功')
    dialogVisible.value = false
    await Promise.all([loadServers(), loadOptions()])
  } finally {
    saving.value = false
  }
}
const testServer = async (row: McpServerVO) => {
  const healthy = await McpApi.testServer(row.id as number)
  healthy ? message.success('连接正常') : message.error('连接失败，请检查服务配置和健康状态')
  await loadServers()
}
const syncTools = async (row: McpServerVO) => {
  const count = await McpApi.syncTools(row.id as number)
  message.success(`已同步 ${count} 个工具`)
  await Promise.all([loadOptions(), activeTab.value === 'tools' ? loadTools() : Promise.resolve()])
}
const toggleServer = async (row: McpServerVO, enabled: string | number | boolean) => {
  await McpApi.updateServerStatus(row.id as number, Boolean(enabled))
  row.status = enabled ? 0 : 1
  message.success(enabled ? '服务已启用' : '服务已停用')
  await loadOptions()
}
watch(activeTab, (value) => {
  if (value === 'tools') void loadTools()
  if (value === 'policies') void loadPolicies()
  if (value === 'logs') void loadLogs()
})
watch(
  () => form.authType,
  (value) => {
    if (value === 'NONE') form.secretRef = ''
  }
)
watch(policyServerIds, (serverIds) => {
  const allowed = new Set(
    policyToolOptions.value
      .filter((tool) => serverIds.includes(tool.serverId as number))
      .map((tool) => tool.id as number)
  )
  policyToolIds.value = policyToolIds.value.filter((id) => allowed.has(id))
})
onMounted(() => Promise.all([loadServers(), loadOptions()]))
</script>
