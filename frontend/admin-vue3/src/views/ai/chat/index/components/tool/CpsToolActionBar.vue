<template>
  <div v-if="sortedActions.length" class="mx-20px pt-8px" data-testid="cps-tool-actions">
    <div class="flex items-center gap-8px overflow-x-auto pb-2px">
      <span class="shrink-0 text-12px font-medium text-[var(--el-text-color-secondary)]">快捷场景：</span>
      <el-button
        v-for="action in primaryActions"
        :key="action.intent"
        round
        size="small"
        class="!px-12px"
        :disabled="disabled"
        @click="handleActionClick(action)"
      >
        <Icon :icon="actionIcon(action.intent)" class="mr-4px text-[var(--el-color-primary)]" />
        {{ friendly(action.label, '业务操作') }}
      </el-button>

      <el-popover v-if="moreActions.length" v-model:visible="moreVisible" width="460" trigger="click">
        <template #reference>
          <el-button round plain size="small" :disabled="disabled">
            <Icon icon="ep:grid" class="mr-3px" />
            更多能力
            <Icon icon="ep:arrow-down" class="ml-3px text-11px" />
          </el-button>
        </template>
        <div class="max-h-420px overflow-y-auto p-4px" data-testid="cps-tool-actions-more">
          <section v-for="group in groupedMoreActions" :key="group.name" class="mb-12px last:mb-0">
            <div class="mb-6px text-12px font-medium text-[var(--el-text-color-secondary)]">
              {{ friendly(group.name, '其他能力') }}
            </div>
            <div class="grid grid-cols-2 gap-6px">
              <el-button
                v-for="action in group.actions"
                :key="action.intent"
                class="!ml-0 !justify-start"
                plain
                size="small"
                :disabled="disabled"
                @click="handleActionClick(action)"
              >
                <Icon :icon="actionIcon(action.intent)" class="mr-4px shrink-0 text-[var(--el-color-primary)]" />
                <span class="truncate">{{ friendly(action.label, '业务操作') }}</span>
              </el-button>
            </div>
          </section>
        </div>
      </el-popover>
    </div>
  </div>

  <el-dialog
    v-model="formVisible"
    :title="friendly(activeAction?.label, '完善请求信息')"
    width="500px"
    append-to-body
    destroy-on-close
  >
    <el-form v-if="activeAction" label-position="top" @submit.prevent>
      <el-form-item
        v-for="field in activeAction.fields || []"
        :key="field.name"
        :label="friendly(field.label, field.name)"
        :required="field.required"
      >
        <el-input-number
          v-if="fieldType(field.type) === 'NUMBER'"
          v-model="formValues[field.name]"
          class="!w-full"
          :min="0"
          controls-position="right"
        />
        <el-select
          v-else-if="fieldType(field.type) === 'SELECT'"
          v-model="formValues[field.name]"
          class="w-full"
          clearable
          :placeholder="field.placeholder"
        >
          <el-option
            v-for="option in field.options || []"
            :key="String(option.value)"
            :label="friendly(option.label, String(option.value))"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-else-if="fieldType(field.type) === 'DATE_RANGE'"
          v-model="formValues[field.name]"
          class="!w-full"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <el-date-picker
          v-else-if="fieldType(field.type) === 'DATE'"
          v-model="formValues[field.name]"
          class="!w-full"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="field.placeholder"
        />
        <el-input
          v-else
          v-model="formValues[field.name]"
          :type="fieldType(field.type) === 'TEXTAREA' ? 'textarea' : 'text'"
          :rows="fieldType(field.type) === 'TEXTAREA' ? 3 : undefined"
          :placeholder="field.placeholder"
          clearable
        />
      </el-form-item>
      <el-alert
        v-if="activeAction.riskLevel !== 'READ_ONLY'"
        :title="
          activeAction.riskLevel === 'ASSET_WRITE'
            ? '该操作会影响返利资产，提交前将进行两次确认。'
            : '该操作会生成归因或跟踪信息，提交前需要确认。'
        "
        type="warning"
        :closable="false"
        show-icon
      />
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <template v-if="activeAction?.riskLevel === 'READ_ONLY'">
        <el-button @click="submitForm(false)">生成到输入框</el-button>
        <el-button type="primary" @click="submitForm(true)">立即发送</el-button>
      </template>
      <el-button v-else type="primary" @click="submitForm(true)">
        确认并发送
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import type { ToolActionFieldVO, ToolActionVO } from '@/api/ai/chat/toolAction'
import {
  buildToolPrompt,
  PRIMARY_TOOL_INTENTS,
  createIntentRequestId,
  getDefaultFieldValues,
  sortToolActions,
  toFriendlyToolText
} from '../../toolActions'

defineOptions({ name: 'CpsToolActionBar' })

const props = defineProps<{
  actions: ToolActionVO[]
  disabled?: boolean
}>()

const emits = defineEmits<{
  (
    e: 'submit',
    value: { action: ToolActionVO; prompt: string; intentRequestId: string; autoSend: boolean }
  ): void
}>()

const message = useMessage()
const moreVisible = ref(false)
const formVisible = ref(false)
const activeAction = ref<ToolActionVO>()
const formValues = reactive<Record<string, any>>({})

const sortedActions = computed(() => sortToolActions(props.actions || []))
const primaryIntentSet = new Set<string>(PRIMARY_TOOL_INTENTS)

const primaryActions = computed(() => {
  const matches = sortedActions.value.filter((action) => primaryIntentSet.has(action.intent))
  // 如果当前可用列表中缺少主场景意图，退回到前 3 个常用意图
  return matches.length ? matches : sortedActions.value.slice(0, 3)
})

const moreActions = computed(() => {
  const primarySet = new Set(primaryActions.value.map((a) => a.intent))
  return sortedActions.value.filter((action) => !primarySet.has(action.intent))
})

const groupedMoreActions = computed(() => {
  const groups = new Map<string, ToolActionVO[]>()
  moreActions.value.forEach((action) => {
    let name = action.group || '其他能力'
    if (action.intent.includes('RESONANCE') || action.intent.includes('ALTERNATIVE') || action.intent.includes('THEME')) {
      name = '选品推荐'
    } else if (action.intent.includes('ORDER') || action.intent.includes('REBATE')) {
      name = '订单与资产'
    }
    groups.set(name, [...(groups.get(name) || []), action])
  })
  return [...groups.entries()].map(([name, actions]) => ({ name, actions }))
})

const actionIcon = (intent?: string) => {
  switch (intent) {
    case 'COMPARE_PRICES':
      return 'ep:scale-to-original'
    case 'SEARCH_GOODS':
      return 'ep:search'
    case 'GET_REBATE_SUMMARY':
      return 'ep:wallet'
    case 'QUERY_ORDERS':
      return 'ep:list'
    case 'GET_REBATE_BALANCE':
      return 'ep:coin'
    case 'FIND_RESONANCE_GOODS':
      return 'ep:histogram'
    case 'FIND_ALTERNATIVES':
      return 'ep:switch'
    case 'ANALYZE_GOODS_DETAIL':
      return 'ep:data-analysis'
    case 'ANALYZE_ORDER_PROFILE':
      return 'ep:user'
    case 'ANALYZE_ORDER_TREND':
      return 'ep:trend-charts'
    case 'GENERATE_LINK':
      return 'ep:link'
    case 'LIST_SELECTION_THEMES':
      return 'ep:collection'
    default:
      return 'ep:operation'
  }
}

const friendly = (value?: string, fallback?: string) => toFriendlyToolText(value, fallback)
const fieldType = (type?: string) => (type || 'TEXT').toUpperCase().replace('-', '_')

const resetFormValues = (fields: ToolActionFieldVO[]) => {
  Object.keys(formValues).forEach((key) => delete formValues[key])
  Object.assign(formValues, getDefaultFieldValues(fields || []))
}

const confirmRisk = async (action: ToolActionVO, intentRequestId: string, prompt: string) => {
  const promptSummary = friendly(prompt, '请核对本次业务请求').slice(0, 300)
  if (action.riskLevel === 'ATTRIBUTION_WRITE') {
    await ElMessageBox.confirm(
      `即将执行“${friendly(action.label, '归因操作')}”：${promptSummary}。确认继续吗？`,
      '确认业务操作',
      { type: 'warning', confirmButtonText: '确认执行' }
    )
  }
  if (action.riskLevel === 'ASSET_WRITE') {
    await ElMessageBox.confirm(
      `请核对“${friendly(action.label, '资产操作')}”：${promptSummary}。确认提交吗？`,
      '第一次确认',
      { type: 'warning', confirmButtonText: '内容无误' }
    )
    await ElMessageBox.confirm(
      `该操作会影响返利资产：${promptSummary}。请求编号 ${intentRequestId}，是否最终执行？`,
      '资产变更二次确认',
      { type: 'error', confirmButtonText: '最终确认执行' }
    )
  }
}

const emitAction = async (action: ToolActionVO, values: Record<string, unknown>, autoSendFlag?: boolean) => {
  const prompt = buildToolPrompt(action, values)
  if (!prompt) {
    message.error('无法生成请求，请补充必要信息')
    return
  }
  const intentRequestId = createIntentRequestId()
  await confirmRisk(action, intentRequestId, prompt)
  emits('submit', {
    action,
    prompt,
    intentRequestId,
    autoSend: autoSendFlag ?? (action.interactionType === 'DIRECT' || action.riskLevel !== 'READ_ONLY')
  })
}

const handleActionClick = async (action: ToolActionVO) => {
  moreVisible.value = false
  activeAction.value = action
  resetFormValues(action.fields || [])
  if (action.interactionType === 'DIRECT' && !action.fields?.length) {
    try {
      await emitAction(action, {})
    } catch {
      // 用户取消风险确认
    }
    return
  }
  formVisible.value = true
}

const submitForm = async (autoSend = false) => {
  const action = activeAction.value
  if (!action) return
  const missingField = (action.fields || []).find((field) => {
    const value = formValues[field.name]
    return field.required && (value === '' || value === undefined || value === null)
  })
  if (missingField) {
    message.warning(`请填写${friendly(missingField.label, missingField.name)}`)
    return
  }
  try {
    await emitAction(action, { ...formValues }, autoSend)
    formVisible.value = false
  } catch {
    // 用户取消风险确认
  }
}
</script>
