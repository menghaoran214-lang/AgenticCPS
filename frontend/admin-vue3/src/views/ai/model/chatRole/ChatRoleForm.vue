<template>
  <Dialog
    :title="dialogTitle"
    v-model="dialogVisible"
    width="860px"
    :scroll="true"
    max-height="620px"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="formLoading"
    >
      <el-tabs v-model="activeTab" class="chat-role-tabs">
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane name="basic">
          <template #label>
            <span class="flex items-center gap-1">
              <Icon icon="ep:info-filled" />
              <span>基础信息</span>
            </span>
          </template>

          <el-row :gutter="20">
            <el-col :span="14">
              <el-form-item label="角色名称" prop="name">
                <el-input v-model="formData.name" placeholder="请输入角色名称，如：CPS 联盟助手" clearable />
              </el-form-item>

              <el-form-item label="角色类别" prop="category" v-if="!isUser">
                <el-input v-model="formData.category" placeholder="请输入角色类别" clearable />
                <div class="flex items-center gap-1.5 mt-1.5 flex-wrap">
                  <span class="text-12px text-gray-400">推荐分类：</span>
                  <el-tag
                    v-for="cat in presetCategories"
                    :key="cat"
                    size="small"
                    class="cursor-pointer hover:opacity-80"
                    effect="plain"
                    @click="formData.category = cat"
                  >
                    {{ cat }}
                  </el-tag>
                </div>
              </el-form-item>

              <el-form-item label="角色排序" prop="sort" v-if="!isUser">
                <el-input-number
                  v-model="formData.sort"
                  :min="0"
                  :max="9999"
                  placeholder="数值越小越靠前"
                  class="!w-full"
                />
              </el-form-item>

              <el-form-item label="开启状态" prop="status" v-if="!isUser">
                <el-radio-group v-model="formData.status">
                  <el-radio
                    v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
                    :key="String(dict.value)"
                    :value="dict.value"
                  >
                    {{ dict.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>

            <el-col :span="10">
              <el-form-item label="角色头像" prop="avatar">
                <div class="flex flex-col items-start gap-1">
                  <UploadImg v-model="formData.avatar" height="70px" width="70px" />
                  <span class="text-12px text-gray-400">建议上传 1:1 正方形图标</span>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="角色描述" prop="description">
            <el-input
              type="textarea"
              :rows="3"
              v-model="formData.description"
              placeholder="简要介绍该角色的核心职责、擅长业务与服务场景（建议 15~100 字）"
            />
          </el-form-item>
        </el-tab-pane>

        <!-- Tab 2: 人设与模型 -->
        <el-tab-pane name="prompt">
          <template #label>
            <span class="flex items-center gap-1">
              <Icon icon="ep:cpu" />
              <span>人设与模型</span>
            </span>
          </template>

          <el-alert
            title="角色人设（System Prompt）配置指引"
            type="info"
            :closable="false"
            show-icon
            class="!mb-4"
          >
            <template #default>
              <div class="text-12px leading-relaxed text-gray-500 dark:text-gray-400">
                • <b>明确角色身份</b>：设定角色称谓、服务定位与回复口吻（如严谨、热情、简洁）。<br />
                • <b>明确业务边界</b>：声明主要任务（如商品检索、导购转链、订单查询），约束不可越权或捏造未核实的数据。<br />
                • <b>自主工具规划</b>：模型会依据用户意图自主决策何时调用关联的工具或知识库，无需用户手动输入工具名。
              </div>
            </template>
          </el-alert>

          <el-form-item label="绑定模型" prop="modelId" v-if="!isUser">
            <el-select
              v-model="formData.modelId"
              placeholder="请选择模型（留空则默认继承系统全局默认模型）"
              clearable
              class="!w-full"
            >
              <el-option
                v-for="model in models"
                :key="model.id"
                :label="model.name"
                :value="model.id"
              />
            </el-select>
            <div class="text-12px text-gray-400 mt-1">
              留空时将自动使用系统后台配置的全局默认对话大语言模型。
            </div>
          </el-form-item>

          <el-form-item label="角色设定" prop="systemMessage">
            <el-input
              type="textarea"
              :rows="8"
              v-model="formData.systemMessage"
              placeholder="请输入角色系统设定提示词（System Prompt），例如：你是 CPS 联盟助手。用户以自然语言提出购物、比价、订单、返利需求时..."
            />
          </el-form-item>
        </el-tab-pane>

        <!-- Tab 3: 能力扩展 -->
        <el-tab-pane name="capability">
          <template #label>
            <span class="flex items-center gap-1">
              <Icon icon="ep:connection" />
              <span>能力编排</span>
            </span>
          </template>

          <!-- 本地工具与知识库 -->
          <div class="mb-4">
            <div class="text-13px font-bold text-gray-700 dark:text-gray-300 mb-2 flex items-center gap-1">
              <Icon icon="ep:files" class="text-primary" />
              <span>本地知识与内置工具</span>
            </div>

            <el-form-item label="引用知识库" prop="knowledgeIds">
              <el-select
                v-model="formData.knowledgeIds"
                placeholder="请选择知识库（支持多选）"
                clearable
                multiple
                collapse-tags
                collapse-tags-tooltip
                :max-collapse-tags="3"
                class="!w-full"
              >
                <el-option
                  v-for="item in knowledgeList"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
              <div class="text-12px text-gray-400 mt-1">
                挂载专属向量知识库，对话时通过 RAG 智能检索领域常识或业务规则。
              </div>
            </el-form-item>

            <el-form-item label="引用工具" prop="toolIds">
              <el-select
                v-model="formData.toolIds"
                placeholder="请选择工具（支持多选）"
                clearable
                multiple
                collapse-tags
                collapse-tags-tooltip
                :max-collapse-tags="3"
                class="!w-full"
              >
                <el-option
                  v-for="item in toolList"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id!"
                />
              </el-select>
              <div class="text-12px text-gray-400 mt-1">
                系统原生注册的 Java 服务工具（如商品搜索、比价、推广转链、订单查询），模型按需自主调用。
              </div>
            </el-form-item>
          </div>

          <!-- Skills 技能与 MCP 网关治理 -->
          <div v-if="!isUser" class="p-3 border border-dashed rounded-md bg-gray-50/50 dark:bg-gray-800/40">
            <div class="text-13px font-bold text-gray-700 dark:text-gray-300 mb-2 flex items-center justify-between">
              <span class="flex items-center gap-1">
                <Icon icon="ep:magic-stick" class="text-orange-500" />
                <span>Skills 技能编排 & MCP 协议网关</span>
              </span>
              <el-tag size="small" type="success" effect="plain">协同治理</el-tag>
            </div>

            <el-form-item label="绑定 Skills" prop="skillIds">
              <el-select
                v-model="formData.skillIds"
                placeholder="请选择已发布 Skill（支持多选）"
                clearable
                multiple
                collapse-tags
                collapse-tags-tooltip
                :max-collapse-tags="3"
                class="!w-full"
              >
                <el-option
                  v-for="item in skillList"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id!"
                />
              </el-select>
              <div class="text-12px text-gray-400 mt-1">
                绑定已发布的复合业务技能工作流，为角色赋予高阶任务执行能力。
              </div>
            </el-form-item>

            <el-form-item label="MCP 策略" prop="mcpPolicyId">
              <el-select
                v-model="formData.mcpPolicyId"
                placeholder="请选择 MCP 治理策略"
                clearable
                class="!w-full"
              >
                <el-option
                  v-for="policy in mcpPolicies"
                  :key="policy.id"
                  :label="policy.name || policy.code || `策略 ${policy.id}`"
                  :value="policy.id"
                />
              </el-select>
              <div class="text-12px text-gray-400 mt-1">
                对跨服务的 MCP 客户端调用进行统一权限边界、白名单过滤与审计治理。
              </div>
              <div
                v-if="formData.skillIds && formData.skillIds.length > 0 && formData.mcpClientNames && formData.mcpClientNames.length > 0"
                class="mt-1 text-12px text-orange-500 font-medium"
              >
                ⚠️ 提示：已引用旧版 MCP 且绑定了 Skills，必须配置 MCP 策略以进行安全治理（或清空下方“引用 MCP”）。
              </div>
            </el-form-item>

            <el-form-item label="引用 MCP" prop="mcpClientNames">
              <el-select
                v-model="formData.mcpClientNames"
                placeholder="请选择 MCP 客户端（旧版直连模式）"
                clearable
                multiple
                collapse-tags
                collapse-tags-tooltip
                :max-collapse-tags="3"
                class="!w-full"
              >
                <el-option
                  v-for="dict in getStrDictOptions(DICT_TYPE.AI_MCP_CLIENT_NAME)"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
              <div class="mt-1 text-12px text-gray-400">
                注：旧版直连模式。若绑定了上方 Skills，建议清空此项并改选【MCP 策略】统一托管。
              </div>
            </el-form-item>
          </div>
        </el-tab-pane>

        <!-- Tab 4: 分发与安全 (仅后台管理员) -->
        <el-tab-pane name="governance" v-if="!isUser">
          <template #label>
            <span class="flex items-center gap-1">
              <Icon icon="ep:lock" />
              <span>分发与安全</span>
            </span>
          </template>

          <!-- 区域 1：发布范围与受众渠道 -->
          <div class="mb-4">
            <div class="text-13px font-bold text-gray-700 dark:text-gray-300 mb-2 flex items-center gap-1">
              <Icon icon="ep:share" class="text-blue-500" />
              <span>发布范围与受众渠道</span>
            </div>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="角色类型" prop="roleType">
                  <el-select v-model="formData.roleType" class="!w-full">
                    <el-option label="自定义角色" value="CUSTOM" />
                    <el-option label="系统预设" value="PRESET" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="预设编码" prop="presetCode">
                  <el-input v-model="formData.presetCode" placeholder="如 cps-assistant" clearable />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="text-12px text-gray-400 -mt-2 mb-3 pl-110px">
              {{
                formData.roleType === 'PRESET'
                  ? '⚠️ 系统预设角色需指定唯一编码（如 cps-assistant），系统业务代码依赖此编码进行寻址绑定。'
                  : '自定义角色无需硬编码绑定，可留空或输入便于分类的自定义英文字符串。'
              }}
            </div>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="发布状态" prop="publishStatus">
                  <el-select v-model="formData.publishStatus" class="!w-full">
                    <el-option label="草稿 (DRAFT)" value="DRAFT" />
                    <el-option label="已发布 (PUBLISHED)" value="PUBLISHED" />
                    <el-option label="已下线 (ARCHIVED)" value="ARCHIVED" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="是否公开" prop="publicStatus">
                  <el-radio-group v-model="formData.publicStatus">
                    <el-radio
                      v-for="dict in getBoolDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING)"
                      :key="String(dict.value)"
                      :value="dict.value"
                    >
                      {{ dict.label }}
                    </el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="会员可用" prop="memberEnabled">
                  <el-radio-group v-model="formData.memberEnabled">
                    <el-radio
                      v-for="dict in getBoolDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING)"
                      :key="String(dict.value)"
                      :value="dict.value"
                    >
                      {{ dict.label }}
                    </el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="适用人群" prop="audience">
              <el-input v-model="formData.audience" placeholder="例如：ADMIN,MEMBER" clearable />
              <div class="flex items-center gap-1.5 mt-1.5 flex-wrap">
                <span class="text-12px text-gray-400">快捷填充：</span>
                <el-tag
                  v-for="aud in presetAudiences"
                  :key="aud.value"
                  size="small"
                  class="cursor-pointer hover:opacity-80"
                  effect="plain"
                  @click="formData.audience = aud.value"
                >
                  {{ aud.label }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item label="适用渠道" prop="channelScope">
              <el-input v-model="formData.channelScope" placeholder="例如：ALL 或 MALL,VIDEO" clearable />
              <div class="flex items-center gap-1.5 mt-1.5 flex-wrap">
                <span class="text-12px text-gray-400">快捷填充：</span>
                <el-tag
                  v-for="ch in presetChannels"
                  :key="ch.value"
                  size="small"
                  class="cursor-pointer hover:opacity-80"
                  effect="plain"
                  @click="formData.channelScope = ch.value"
                >
                  {{ ch.label }}
                </el-tag>
              </div>
            </el-form-item>
          </div>

          <!-- 区域 2：安全风控与操作控制 -->
          <div class="p-3 border border-dashed rounded-md bg-gray-50/50 dark:bg-gray-800/40">
            <div class="text-13px font-bold text-gray-700 dark:text-gray-300 mb-2 flex items-center justify-between">
              <span class="flex items-center gap-1">
                <Icon icon="ep:shield" class="text-green-500" />
                <span>安全风控与操作权限</span>
              </span>
              <el-tag size="small" type="warning" effect="plain">安全基线</el-tag>
            </div>

            <el-row :gutter="20">
              <el-col :span="14">
                <el-form-item label="风险等级" prop="riskLevel">
                  <el-select v-model="formData.riskLevel" class="!w-full">
                    <el-option label="只读 (READ_ONLY) - 仅查询检索" value="READ_ONLY" />
                    <el-option label="归因写入 (ATTRIBUTION_WRITE) - 允许转链跟单" value="ATTRIBUTION_WRITE" />
                    <el-option label="资产写入 (ASSET_WRITE) - 涉及返利冻结扣减" value="ASSET_WRITE" />
                    <el-option label="管理员 (ADMIN) - 最高操作权限" value="ADMIN" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="10">
                <el-form-item label="允许写操作" prop="allowMutation">
                  <el-switch v-model="formData.allowMutation" active-text="允许" inactive-text="禁止" />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="text-12px text-gray-400 pl-110px">
              • <b>只读</b>：严禁任何数据变更；<br />
              • <b>归因写入</b>：允许模型创建转链、绑定推广位与归因流水；<br />
              • <b>资产写入</b>：涉及返利资金变动与 Token 扣减，需严格审计；<br />
              • <b>管理员</b>：拥有全部系统操作与治理权限。
            </div>
            <div
              v-if="formData.allowMutation && formData.riskLevel === 'READ_ONLY'"
              class="mt-2 text-12px text-orange-500 font-medium pl-110px"
            >
              ⚠️ 提示：已开启写操作，但当前风险等级为“只读”，请确认是否需要调整为“归因写入”或“资产写入”。
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { getIntDictOptions, getBoolDictOptions, DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { ChatRoleApi, ChatRoleVO } from '@/api/ai/model/chatRole'
import { CommonStatusEnum } from '@/utils/constants'
import { ModelApi, ModelVO } from '@/api/ai/model/model'
import { FormRules } from 'element-plus'
import { AiModelTypeEnum } from '@/views/ai/utils/constants'
import { KnowledgeApi, KnowledgeVO } from '@/api/ai/knowledge/knowledge'
import { ToolApi, ToolVO } from '@/api/ai/model/tool'
import { SkillApi, SkillVO } from '@/api/ai/skill'
import { McpApi, McpPolicyVO } from '@/api/ai/mcp'

/** AI 聊天角色 表单 */
defineOptions({ name: 'ChatRoleForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const activeTab = ref('basic') // 当前激活的 Tab

// 常用推荐分类与快捷标签
const presetCategories = ['CPS 联盟', '选品导购', '本地生活', '内容创作', '客服问答', '运营助手']
const presetAudiences = [
  { label: '全部人群 (ALL)', value: 'ALL' },
  { label: '后台运营 (ADMIN)', value: 'ADMIN' },
  { label: '商城会员 (MEMBER)', value: 'MEMBER' },
  { label: '运营+会员 (ADMIN,MEMBER)', value: 'ADMIN,MEMBER' },
  { label: '商家 (MERCHANT)', value: 'MERCHANT' }
]
const presetChannels = [
  { label: '全渠道 (ALL)', value: 'ALL' },
  { label: '商城终端 (MALL)', value: 'MALL' },
  { label: '视频号 (VIDEO)', value: 'VIDEO' },
  { label: '抖音 (DOUYIN)', value: 'DOUYIN' }
]

const formData = ref({
  id: undefined,
  modelId: undefined,
  name: undefined,
  avatar: undefined,
  category: undefined,
  sort: undefined,
  description: undefined,
  systemMessage: undefined,
  publicStatus: true,
  memberEnabled: false,
  status: CommonStatusEnum.ENABLE,
  knowledgeIds: [] as number[],
  toolIds: [] as number[],
  mcpClientNames: [] as string[],
  presetCode: undefined,
  roleType: 'CUSTOM',
  publishStatus: 'DRAFT',
  audience: undefined,
  channelScope: undefined,
  skillIds: [] as number[],
  mcpPolicyId: undefined,
  riskLevel: 'READ_ONLY',
  allowMutation: false
})

const formRef = ref() // 表单 Ref
const models = ref([] as ModelVO[]) // 聊天模型列表
const knowledgeList = ref([] as KnowledgeVO[]) // 知识库列表
const toolList = ref([] as ToolVO[]) // 工具列表
const skillList = ref([] as SkillVO[]) // 已发布 Skill 列表
const mcpPolicies = ref([] as McpPolicyVO[])

/** 是否【我】自己创建，私有角色 */
const isUser = computed(() => {
  return formType.value === 'my-create' || formType.value === 'my-update'
})

const formRules = reactive<FormRules>({
  name: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  avatar: [{ required: true, message: '角色头像不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '角色类别不能为空', trigger: 'blur' }],
  sort: [{ required: true, message: '角色排序不能为空', trigger: 'blur' }],
  description: [{ required: true, message: '角色描述不能为空', trigger: 'blur' }],
  systemMessage: [{ required: true, message: '角色设定不能为空', trigger: 'blur' }],
  publicStatus: [{ required: true, message: '是否公开不能为空', trigger: 'blur' }],
  memberEnabled: [{ required: true, message: '会员可用不能为空', trigger: 'blur' }],
  mcpPolicyId: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (
          formData.value.skillIds &&
          formData.value.skillIds.length > 0 &&
          formData.value.mcpClientNames &&
          formData.value.mcpClientNames.length > 0 &&
          !value
        ) {
          callback(new Error('角色引用了旧版 MCP 且绑定了 Skill，必须配置 MCP 策略（或清空上方“引用 MCP”）'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
})

/** 校验未通过时自动定位并切换至包含错误的 Tab */
const locateErrorTab = (errors: Record<string, any>) => {
  if (!errors) return
  const errorKeys = Object.keys(errors)
  if (errorKeys.length === 0) return

  const basicFields = ['name', 'avatar', 'category', 'sort', 'description', 'status']
  const promptFields = ['systemMessage', 'modelId']
  const capabilityFields = ['toolIds', 'knowledgeIds', 'skillIds', 'mcpPolicyId', 'mcpClientNames']
  const governanceFields = [
    'publicStatus',
    'memberEnabled',
    'roleType',
    'presetCode',
    'publishStatus',
    'audience',
    'channelScope',
    'riskLevel',
    'allowMutation'
  ]

  for (const key of errorKeys) {
    if (basicFields.includes(key)) {
      activeTab.value = 'basic'
      return
    }
    if (promptFields.includes(key)) {
      activeTab.value = 'prompt'
      return
    }
    if (capabilityFields.includes(key)) {
      activeTab.value = 'capability'
      return
    }
    if (governanceFields.includes(key) && !isUser.value) {
      activeTab.value = 'governance'
      return
    }
  }
}

/** 打开弹窗 */
const open = async (type: string, id?: number, title?: string) => {
  dialogVisible.value = true
  dialogTitle.value = title || t('action.' + type)
  formType.value = type
  activeTab.value = 'basic'
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ChatRoleApi.getChatRole(id)
    } finally {
      formLoading.value = false
    }
  }
  // 获得下拉数据
  models.value = await ModelApi.getModelSimpleList(AiModelTypeEnum.CHAT)
  // 获取知识库列表
  knowledgeList.value = await KnowledgeApi.getSimpleKnowledgeList()
  // 获取工具列表
  toolList.value = await ToolApi.getToolSimpleList()
  if (!isUser.value) {
    const skillPage = await SkillApi.getPage({ pageNo: 1, pageSize: 100, status: 0 })
    skillList.value = (skillPage.list || []).filter(
      (item: SkillVO) => (item.currentVersion || 0) > 0
    )
    const policyPage = await McpApi.getPolicyPage({ pageNo: 1, pageSize: 100, status: 0 })
    mcpPolicies.value = policyPage.list || []
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  if (!formRef.value) return
  // 校验表单
  try {
    await formRef.value.validate()
  } catch (errorFields: any) {
    locateErrorTab(errorFields)
    message.warning('表单存在未完善的必填项，已自动定位到对应分类，请检查')
    return
  }

  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as unknown as ChatRoleVO
    if (formType.value === 'my-create') {
      await ChatRoleApi.createMy(data)
      message.success(t('common.createSuccess'))
    } else if (formType.value === 'my-update') {
      await ChatRoleApi.updateMy(data)
      message.success(t('common.updateSuccess'))
    } else if (formType.value === 'create') {
      await ChatRoleApi.createChatRole(data)
      message.success(t('common.createSuccess'))
    } else {
      await ChatRoleApi.updateChatRole(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    modelId: undefined,
    name: undefined,
    avatar: undefined,
    category: undefined,
    sort: undefined,
    description: undefined,
    systemMessage: undefined,
    publicStatus: true,
    memberEnabled: false,
    status: CommonStatusEnum.ENABLE,
    knowledgeIds: [],
    toolIds: [],
    mcpClientNames: [],
    presetCode: undefined,
    roleType: 'CUSTOM',
    publishStatus: 'DRAFT',
    audience: undefined,
    channelScope: undefined,
    skillIds: [],
    mcpPolicyId: undefined,
    riskLevel: 'READ_ONLY',
    allowMutation: false
  }
  formRef.value?.resetFields()
}
</script>

<style scoped lang="scss">
.chat-role-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}
</style>
