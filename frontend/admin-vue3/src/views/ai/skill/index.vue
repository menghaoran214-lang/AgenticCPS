<template>
  <ContentWrap>
    <div class="mb-16px flex justify-between"
      ><h2>Skills 管理</h2
      ><el-button type="primary" @click="openSkill()">新建 Skill</el-button></div
    >
    <el-form inline @submit.prevent="loadList"
      ><el-form-item label="名称"><el-input v-model="query.name" clearable /></el-form-item
      ><el-form-item><el-button @click="loadList">查询</el-button></el-form-item></el-form
    >
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="code" label="编码" /><el-table-column
        prop="name"
        label="名称"
      /><el-table-column prop="category" label="分类" /><el-table-column
        prop="currentVersion"
        label="当前发布版本"
      /><el-table-column prop="riskLevel" label="风险等级" />
      <el-table-column label="操作" width="200"
        ><template #default="{ row }"
          ><el-button link @click="openSkill(row)">编辑</el-button
          ><el-button link type="primary" @click="openVersions(row)"
            >版本与发布</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="loadList"
    />
  </ContentWrap>
  <Dialog v-model="skillVisible" title="Skill 基本信息">
    <el-form label-width="100px">
      <el-form-item label="编码" required
        ><el-input v-model="skill.code" :disabled="!!skill.id" /></el-form-item
      ><el-form-item label="名称" required><el-input v-model="skill.name" /></el-form-item
      ><el-form-item label="分类"><el-input v-model="skill.category" /></el-form-item
      ><el-form-item label="适用渠道"><el-input v-model="skill.channelScope" /></el-form-item
      ><el-form-item label="描述"
        ><el-input v-model="skill.description" type="textarea"
      /></el-form-item>
      <el-form-item label="风险等级"
        ><el-select v-model="skill.riskLevel"
          ><el-option
            v-for="risk in risks"
            :key="risk"
            :value="risk"
            :label="risk" /></el-select></el-form-item
      ><el-form-item label="启用"
        ><el-switch v-model="skill.status" :active-value="0" :inactive-value="1"
      /></el-form-item> </el-form
    ><template #footer
      ><el-button :loading="saving" type="primary" @click="saveSkill">保存</el-button></template
    >
  </Dialog>
  <Dialog
    v-model="versionsVisible"
    :title="`${selectedSkill?.name || ''} · 版本与发布`"
    width="1000px"
  >
    <el-button type="primary" class="mb-12px" @click="editVersion()">创建新版本</el-button>
    <el-button class="mb-12px ml-8px" :disabled="versions.length < 2" @click="compareVisible = true"
      >版本比较</el-button
    >
    <el-table :data="versions"
      ><el-table-column prop="version" label="版本" width="80" /><el-table-column
        prop="publishStatus"
        label="状态"
        width="130"
      /><el-table-column prop="changeLog" label="变更说明" />
      <el-table-column label="操作" width="470"
        ><template #default="{ row }"
          ><el-button link @click="editVersion(row)">{{
            row.publishStatus === 'DRAFT' ? '编辑' : '查看'
          }}</el-button
          ><el-button link @click="openBindings(row)">能力绑定</el-button
          ><el-button link @click="openSandbox(row)">只读预览</el-button
          ><el-button v-if="row.publishStatus === 'DRAFT'" link type="primary" @click="review(row)"
            >提交审核</el-button
          ><el-button
            v-if="row.publishStatus === 'REVIEWING'"
            link
            type="success"
            @click="publish(row)"
            >发布版本</el-button
          ><el-button
            v-if="
              row.publishStatus === 'PUBLISHED' && row.version !== selectedSkill?.currentVersion
            "
            link
            type="warning"
            @click="rollback(row)"
            >回滚到此版本</el-button
          ><el-button
            v-if="
              row.publishStatus === 'PUBLISHED' && row.version !== selectedSkill?.currentVersion
            "
            link
            type="danger"
            @click="archive(row)"
            >归档</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </Dialog>
  <Dialog v-model="compareVisible" title="版本比较" width="1000px">
    <div class="grid grid-cols-2 gap-16px">
      <div v-for="(_, index) in comparedIds" :key="index"
        ><el-select v-model="comparedIds[index]" placeholder="选择版本"
          ><el-option
            v-for="item in versions"
            :key="item.id"
            :value="item.id!"
            :label="`v${item.version} · ${item.publishStatus}`" /></el-select
        ><pre class="whitespace-pre-wrap break-all">{{ comparedVersion(comparedIds[index]) }}</pre>
      </div>
    </div>
  </Dialog>
  <Dialog v-model="versionVisible" title="Skill 版本" width="760px">
    <el-form label-width="110px" :disabled="versionReadonly"
      ><el-form-item label="版本号" required
        ><el-input-number
          v-model="version.version"
          :min="1"
          :disabled="!!version.id" /></el-form-item
      ><el-form-item label="系统提示词" required
        ><el-input v-model="version.systemPrompt" type="textarea" :rows="6" /></el-form-item
      ><el-form-item label="输入契约"
        ><el-input
          v-model="version.inputSchema"
          type="textarea"
          :rows="3"
          placeholder="JSON Schema" /></el-form-item
      ><el-form-item label="输出模板"
        ><el-input v-model="version.outputTemplate" type="textarea" :rows="3" /></el-form-item
      ><el-form-item label="确认策略"
        ><el-select v-model="version.confirmationPolicy"
          ><el-option label="只读，无需确认" value="NONE" /><el-option
            label="需要用户确认"
            value="REQUIRED" /></el-select></el-form-item
      ><el-form-item label="变更说明"><el-input v-model="version.changeLog" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button v-if="!versionReadonly" type="primary" :loading="saving" @click="saveVersion"
        >保存草稿</el-button
      ></template
    >
  </Dialog>
  <Dialog v-model="bindingsVisible" title="版本能力绑定" width="900px">
    <el-alert
      title="仅草稿可以修改。发布后能力绑定随版本固定。"
      :closable="false"
      class="mb-12px"
    />
    <el-table :data="bindings"
      ><el-table-column label="类型" width="180"
        ><template #default="{ row }"
          ><el-select v-model="row.bindingType" :disabled="bindingReadonly"
            ><el-option
              v-for="type in bindingTypes"
              :key="type"
              :value="type"
              :label="type" /></el-select></template></el-table-column
      ><el-table-column label="工具 / 知识库"
        ><template #default="{ row }"
          ><el-input
            v-if="row.bindingType === 'LOCAL_TOOL'"
            v-model="row.localToolName"
            :disabled="bindingReadonly"
            placeholder="本地工具名称" /><div
            v-else-if="row.bindingType === 'MCP_TOOL'"
            class="flex gap-4px"
            ><el-input-number
              v-model="row.mcpServerId"
              :min="1"
              :disabled="bindingReadonly"
              placeholder="服务 ID" /><el-input
              v-model="row.mcpToolName"
              :disabled="bindingReadonly"
              placeholder="MCP 工具名称" /></div
          ><el-input-number
            v-else
            v-model="row.knowledgeBaseId"
            :min="1"
            :disabled="bindingReadonly"
            placeholder="知识库 ID" /></template></el-table-column
      ><el-table-column label="启用" width="80"
        ><template #default="{ row }"
          ><el-switch
            v-model="row.enabled"
            :active-value="1"
            :inactive-value="0"
            :disabled="bindingReadonly" /></template></el-table-column
      ><el-table-column label="操作" width="80"
        ><template #default="{ $index }"
          ><el-button link :disabled="bindingReadonly" @click="bindings.splice($index, 1)"
            >移除</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <template #footer
      ><el-button
        v-if="!bindingReadonly"
        @click="bindings.push({ bindingType: 'LOCAL_TOOL', enabled: 1 })"
        >添加绑定</el-button
      ><el-button v-if="!bindingReadonly" :loading="saving" type="primary" @click="saveBindings"
        >保存绑定</el-button
      ></template
    >
  </Dialog>
  <Dialog v-model="sandboxVisible" title="只读测试沙箱" width="760px"
    ><el-alert
      :title="
        sandboxExecute
          ? '执行只读测试，不接受会员身份，不执行写操作。'
          : '当前为能力预览，不调用模型。'
      "
      :closable="false"
    />
    <el-switch
      v-model="sandboxExecute"
      active-text="执行只读模型测试"
      class="mt-12px"
      v-hasPermi="['ai:skill:manage']"
    />
    <el-input
      v-model="sandboxInput"
      type="textarea"
      :rows="4"
      class="my-12px"
      placeholder="输入测试内容"
    /><el-button :loading="saving" @click="runSandbox">{{
      sandboxExecute ? '执行只读测试' : '生成只读预览'
    }}</el-button>
    <pre
      v-if="sandboxOutput"
      class="whitespace-pre-wrap break-all"
      data-testid="skill-sandbox-output"
      >{{ sandboxOutput }}</pre
    >
    <pre class="whitespace-pre-wrap break-all">{{ sandboxResult }}</pre>
  </Dialog>
</template>
<script setup lang="ts">
import { SkillApi, type SkillVO, type SkillVersionVO, type SkillBindingVO } from '@/api/ai/skill'
defineOptions({ name: 'AiSkillCenter' })
const message = useMessage()
const risks = ['READ_ONLY', 'ATTRIBUTION_WRITE', 'ASSET_WRITE', 'ADMIN']
const bindingTypes = ['LOCAL_TOOL', 'MCP_TOOL', 'KNOWLEDGE']
const query = reactive({ pageNo: 1, pageSize: 10, name: '' })
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const list = ref<SkillVO[]>([])
const versions = ref<SkillVersionVO[]>([])
const selectedSkill = ref<SkillVO>()
const skill = ref<SkillVO>({ name: '' })
const version = ref<SkillVersionVO>({})
const bindingVersion = ref<SkillVersionVO>()
const sandboxVersion = ref<SkillVersionVO>()
const bindings = ref<SkillBindingVO[]>([])
const skillVisible = ref(false)
const versionsVisible = ref(false)
const compareVisible = ref(false)
const comparedIds = ref<(number | undefined)[]>([undefined, undefined])
const comparedVersion = (id?: number) => {
  const item = versions.value.find((v) => v.id === id)
  return item
    ? JSON.stringify(
        {
          systemPrompt: item.systemPrompt,
          inputSchema: item.inputSchema,
          outputTemplate: item.outputTemplate,
          confirmationPolicy: item.confirmationPolicy,
          changeLog: item.changeLog
        },
        null,
        2
      )
    : ''
}
const archive = async (row: SkillVersionVO) => {
  if (!row.id) return
  await message.confirm('归档此版本后将不能用于新会话。')
  await SkillApi.archiveVersion(row.id)
  await refreshVersions()
}
const versionVisible = ref(false)
const bindingsVisible = ref(false)
const sandboxVisible = ref(false)
const sandboxInput = ref('')
const sandboxResult = ref('')
const sandboxExecute = ref(false)
const sandboxOutput = ref('')
const versionReadonly = computed(
  () => !!version.value.id && version.value.publishStatus !== 'DRAFT'
)
const bindingReadonly = computed(() => bindingVersion.value?.publishStatus !== 'DRAFT')
const loadList = async () => {
  loading.value = true
  try {
    const page = await SkillApi.getPage(query)
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const openSkill = (row?: SkillVO) => {
  skill.value = row ? { ...row } : { name: '', code: '', riskLevel: 'READ_ONLY', status: 0 }
  skillVisible.value = true
}
const saveSkill = async () => {
  if (!skill.value.code?.trim() || !skill.value.name.trim())
    return message.warning('请填写编码和名称')
  saving.value = true
  try {
    if (skill.value.id) await SkillApi.update(skill.value)
    else await SkillApi.create(skill.value)
    skillVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}
const refreshVersions = async () => {
  if (selectedSkill.value?.id) versions.value = await SkillApi.getVersions(selectedSkill.value.id)
}
const openVersions = async (row: SkillVO) => {
  selectedSkill.value = row
  await refreshVersions()
  versionsVisible.value = true
}
const editVersion = (row?: SkillVersionVO) => {
  version.value = row
    ? { ...row }
    : {
        skillId: selectedSkill.value?.id,
        version: Math.max(0, ...versions.value.map((v) => v.version || 0)) + 1,
        systemPrompt: '',
        inputSchema: '{"type":"object"}',
        confirmationPolicy: 'NONE',
        publishStatus: 'DRAFT'
      }
  versionVisible.value = true
}
const saveVersion = async () => {
  if (!version.value.systemPrompt?.trim()) return message.warning('请填写系统提示词')
  try {
    JSON.parse(version.value.inputSchema || '{}')
  } catch {
    return message.warning('输入契约必须是有效 JSON')
  }
  saving.value = true
  try {
    if (version.value.id)
      await SkillApi.updateVersion({ ...version.value, expectedRevision: version.value.revision })
    else await SkillApi.createVersion(version.value)
    versionVisible.value = false
    await refreshVersions()
  } finally {
    saving.value = false
  }
}
const review = async (row: SkillVersionVO) => {
  if (row.id) {
    await SkillApi.reviewVersion(row.id)
    await refreshVersions()
  }
}
const publish = async (row: SkillVersionVO) => {
  if (!row.id) return
  await message.confirm(`发布版本 ${row.version}？新会话将使用此版本。`)
  await SkillApi.publishVersion(row.id)
  if (selectedSkill.value) selectedSkill.value.currentVersion = row.version
  await refreshVersions()
  await loadList()
}
const rollback = async (row: SkillVersionVO) => {
  if (!selectedSkill.value?.id || !row.version) return
  await message.confirm(`将新会话使用的版本回滚到 ${row.version}？历史会话保持原能力快照。`)
  await SkillApi.rollbackVersion(selectedSkill.value.id, row.version)
  selectedSkill.value.currentVersion = row.version
  await refreshVersions()
  await loadList()
}
const openBindings = async (row: SkillVersionVO) => {
  if (!row.id) return
  bindingVersion.value = row
  bindings.value = await SkillApi.getBindings({ skillVersionId: row.id })
  bindingsVisible.value = true
}
const saveBindings = async () => {
  if (!bindingVersion.value?.id || bindingVersion.value.revision === undefined) return
  saving.value = true
  try {
    await SkillApi.saveBindings(
      bindingVersion.value.id,
      bindingVersion.value.revision,
      bindings.value
    )
    bindingsVisible.value = false
    await refreshVersions()
  } finally {
    saving.value = false
  }
}
const openSandbox = (row: SkillVersionVO) => {
  sandboxVersion.value = row
  sandboxInput.value = ''
  sandboxResult.value = ''
  sandboxExecute.value = false
  sandboxOutput.value = ''
  sandboxVisible.value = true
}
const runSandbox = async () => {
  if (!sandboxVersion.value?.id) return
  if (sandboxExecute.value && !sandboxInput.value.trim()) return message.warning('请输入测试内容')
  saving.value = true
  try {
    const { output, ...preview } = await SkillApi.test({
      skillVersionId: sandboxVersion.value.id,
      input: sandboxInput.value,
      executeModel: sandboxExecute.value
    })
    sandboxOutput.value = output || ''
    sandboxResult.value = JSON.stringify(preview, null, 2)
  } finally {
    saving.value = false
  }
}
onMounted(loadList)
</script>
