<template>
  <el-container class="absolute flex-1 top-0 left-0 h-full w-full">
    <!-- 左侧：对话列表 -->
    <ConversationList
      :active-id="activeConversationId?.toString() || ''"
      :active-member-id="activeConversation?.memberId"
      ref="conversationListRef"
      @on-conversation-create="handleConversationCreateSuccess"
      @on-conversation-click="handleConversationClick"
      @on-conversation-clear="handleConversationClear"
      @on-conversation-delete="handlerConversationDelete"
      @on-member-change="handleActiveMemberChange"
    />
    <!-- 右侧：对话详情 -->
    <el-container class="bg-[var(--el-bg-color)]">
      <el-header
        class="flex flex-row items-center justify-between bg-[var(--el-bg-color-page)] shadow-[0_0_0_0_var(--el-border-color-light)]"
      >
        <div class="text-18px font-bold">
          {{ activeConversation?.title ? activeConversation?.title : '对话' }}
          <span v-if="activeMessageList.length">({{ activeMessageList.length }})</span>
        </div>
        <div class="flex w-420px flex-row items-center justify-end gap-8px" v-if="activeConversation">
          <el-tooltip content="显示模型每次调用的输入、输出和 Token 用量" placement="bottom">
            <el-switch
              v-model="debugMode"
              inline-prompt
              active-text="调试"
              inactive-text="普通"
              data-testid="chat-debug-mode"
            />
          </el-tooltip>
          <el-button type="primary" bg plain size="small" @click="openChatConversationUpdateForm">
            <span v-html="activeConversation?.modelName"></span>
            <Icon icon="ep:setting" class="ml-10px" />
          </el-button>
          <el-button size="small" class="p-10px" @click="handlerMessageClear">
            <Icon
              icon="heroicons-outline:archive-box-x-mark"
              color="var(--el-text-color-placeholder)"
            />
          </el-button>
          <el-button size="small" class="p-10px">
            <Icon icon="ep:download" color="var(--el-text-color-placeholder)" />
          </el-button>
          <el-button size="small" class="p-10px" @click="handleGoTopMessage">
            <Icon icon="ep:top" color="var(--el-text-color-placeholder)" />
          </el-button>
        </div>
      </el-header>

      <div
        v-if="capabilitySummary"
        class="px-20px py-8px flex flex-wrap items-center gap-8px"
        data-testid="conversation-capabilities"
      >
        <el-tag>{{ capabilitySummary.roleCode }} · v{{ capabilitySummary.roleVersion }}</el-tag>
        <el-tag v-for="skill in capabilitySummary.skills" :key="`${skill.code}-${skill.version}`" type="success">
          Skill · {{ skill.code }} v{{ skill.version }}
        </el-tag>
        <el-tag v-for="mcp in capabilitySummary.mcps" :key="mcp.code" type="warning" effect="plain">
          MCP · {{ mcp.code }} ({{ mcp.toolCount }})
        </el-tag>
        <el-tag type="info"
          >{{ capabilitySummary.serviceCount }} 个 MCP 服务 ·
          {{ capabilitySummary.toolCount }} 项能力</el-tag
        >
        <el-tag type="warning">{{ capabilitySummary.riskLevel }}</el-tag>
        <el-tag
          type="success"
          effect="light"
          v-if="activeConversation?.memberId"
          data-testid="active-member-tag"
        >
          运行会员 · {{ activeMemberName }}
        </el-tag>
        <el-button
          link
          type="primary"
          :disabled="conversationInProgress"
          @click="conversationListRef.createWithAnotherRole()"
          >基于此会话新建其他角色会话</el-button
        >
      </div>
      <CpsSelectionWorkbench
        v-if="activeConversation"
        v-model:mode="workbenchMode"
        :has-results="activeMessageList.length > 0"
        :disabled="conversationInProgress"
        :task-progress="activeTaskProgress"
        :result-summary="workbenchResultSummary"
        :current-prompt="currentWorkbenchPrompt"
        :current-tool-intent="currentWorkbenchToolIntent"
        @prompt="handleRecommendedPrompt"
        @retry="retryWorkbenchTask"
      />

      <!-- main：消息列表 -->
      <el-main class="m-0 p-0 relative h-full w-full">
        <div>
          <div class="absolute top-0 bottom-0 left-0 right-0 overflow-y-hidden p-0 m-0">
            <!-- 情况一：消息加载中 -->
            <MessageLoading v-if="activeMessageListLoading" />
            <!-- 情况二：无聊天对话时 -->
            <MessageNewConversation
              v-if="!activeConversation"
              @on-new-conversation="handleConversationCreate"
            />
            <!-- 情况三：消息列表为空 -->
            <MessageListEmpty
              v-if="!activeMessageListLoading && messageList.length === 0 && activeConversation"
              @on-prompt="handleRecommendedPrompt"
            />
            <!-- 情况四：消息列表不为空 -->
            <div
              v-if="!activeMessageListLoading && messageList.length > 0 && activeConversation"
              class="h-full"
              data-testid="cps-analysis-results"
            >
              <MessageList
                ref="messageRef"
                :conversation="activeConversation"
                :list="messageList"
                :debug-mode="debugMode"
                @on-delete-success="handleMessageDelete"
                @on-edit="handleMessageEdit"
                @on-refresh="handleMessageRefresh"
              />
            </div>
          </div>
        </div>
      </el-main>

      <!-- 底部 -->
      <el-footer class="flex flex-col !h-auto !p-0">
        <ToolConfirmationCards
          ref="confirmationCardsRef"
          :conversation-id="activeConversationId || undefined"
          :active="!!activeConversation?.effectiveCapabilitySnapshot"
          @processed="handleConfirmationProcessed"
        />
        <CpsToolActionBar
          :actions="toolActions"
          :disabled="conversationInProgress"
          @submit="handleToolActionSubmit"
        />
        <!-- TODO @芋艿：这块要想办法迁移下！ -->
        <form
          class="mt-10px mx-20px mb-16px py-10px px-12px flex flex-col h-auto rounded-12px bg-[var(--el-bg-color)] border border-solid border-[var(--el-border-color)] shadow-sm focus-within:border-[var(--el-color-primary)] transition-all"
        >
          <div class="mb-8px flex items-center justify-between" data-testid="cps-workbench-input-mode">
            <div class="flex items-center gap-8px">
              <el-button
                size="small"
                plain
                type="primary"
                :disabled="conversationInProgress"
                data-testid="chat-role-picker"
                @click="chooseChatRole"
              >
                <Icon icon="ep:user" class="mr-4px" />
                {{ activeConversation?.roleId ? '选择聊天角色' : '选择聊天角色后开始' }}
              </el-button>
              <el-tag effect="plain" type="info" size="small">
                {{ workbenchMode === 'SELECTION' ? '选品分析' : '订单分析' }}
              </el-tag>
              <el-tag
                v-if="selectedToolAction"
                closable
                size="small"
                type="primary"
                @close="clearSelectedToolAction"
              >
                优先使用：{{ friendlyToolText(selectedToolAction.label, '已选业务能力') }}
              </el-tag>
            </div>
            <span v-if="!selectedToolAction" class="text-11px text-[var(--el-text-color-placeholder)]">
              自动识别业务意图
            </span>
          </div>
          <textarea
            class="h-80px border-none box-border resize-none py-4px px-4px text-14px leading-22px overflow-auto focus:outline-none bg-transparent"
            v-model="prompt"
            @keydown="handleSendByKeydown"
            @input="handlePromptInput"
            @compositionstart="onCompositionstart"
            @compositionend="onCompositionend"
            :placeholder="workbenchPlaceholder"
          >
          </textarea>
          <div class="flex items-center justify-between pb-0 pt-6px border-t border-[var(--el-border-color-lighter)] mt-4px">
            <div class="flex items-center">
              <MessageFileUpload v-model="uploadFiles" :limit="5" :max-size="10" class="mr-12px" />
              <el-switch v-model="enableContext" size="small" />
              <span class="ml-4px mr-14px text-12px text-[var(--el-text-color-secondary)]">上下文</span>
              <el-switch v-model="enableWebSearch" size="small" />
              <span class="ml-4px text-12px text-[var(--el-text-color-secondary)]">联网搜索</span>
            </div>
            <div class="flex items-center gap-8px">
              <span class="text-11px text-[var(--el-text-color-placeholder)]">Enter 发送 / Shift+Enter 换行</span>
              <el-button
                type="primary"
                size="default"
                @click="handleSendByButton"
                :loading="conversationInProgress"
                v-if="conversationInProgress == false"
              >
                {{ conversationInProgress ? '进行中' : '发送' }}
              </el-button>
              <el-button
                type="danger"
                size="default"
                @click="stopStream()"
                v-if="conversationInProgress == true"
              >
                停止
              </el-button>
            </div>
          </div>
        </form>
      </el-footer>
    </el-container>

    <!-- 更新对话 Form -->
    <ConversationUpdateForm
      ref="conversationUpdateFormRef"
      @success="handleConversationUpdateSuccess"
    />
  </el-container>
</template>

<script setup lang="ts">
import {
  ChatMessageApi,
  type ChatMessageStreamData,
  type ChatMessageVO,
  type ChatTaskProgress,
  type ChatTaskStep,
  type ToolExecutionVO
} from '@/api/ai/chat/message'
import { ChatConversationApi, ChatConversationVO } from '@/api/ai/chat/conversation'
import { getUser } from '@/api/member/user'
import { useUserStore } from '@/store/modules/user'
import { ChatToolActionApi, type ToolActionVO } from '@/api/ai/chat/toolAction'
import ConversationList from './components/conversation/ConversationList.vue'
import ConversationUpdateForm from './components/conversation/ConversationUpdateForm.vue'
import MessageList from './components/message/MessageList.vue'
import MessageListEmpty from './components/message/MessageListEmpty.vue'
import MessageLoading from './components/message/MessageLoading.vue'
import MessageNewConversation from './components/message/MessageNewConversation.vue'
import MessageFileUpload from './components/message/MessageFileUpload.vue'
import CpsToolActionBar from './components/tool/CpsToolActionBar.vue'
import ToolConfirmationCards from './components/tool/ToolConfirmationCards.vue'
import { readCapabilitySummary } from './capabilities'
import CpsSelectionWorkbench, {
  type CpsWorkbenchMode
} from './components/workbench/CpsSelectionWorkbench.vue'
import {
  createIntentRequestId,
  type RecommendedPrompt,
  toFriendlyToolText,
  upsertToolExecution
} from './toolActions'

/** AI 聊天对话 列表 */
defineOptions({ name: 'AiChat' })

const route = useRoute() // 路由
const message = useMessage() // 消息弹窗

// 聊天对话
const conversationListRef = ref()
const activeConversationId = ref<string | null>(null) // 选中的对话编号
const activeConversation = ref<ChatConversationVO | null>(null) // 选中的 Conversation
const confirmationCardsRef = ref<InstanceType<typeof ToolConfirmationCards>>()
const capabilitySummary = computed(() =>
  readCapabilitySummary(activeConversation.value?.effectiveCapabilitySnapshot, {
    skillSnapshot: activeConversation.value?.skillSnapshot,
    mcpClientName: activeConversation.value?.mcpClientName,
    roleCode: activeConversation.value?.rolePresetCode,
    roleVersion: activeConversation.value?.roleVersion
  })
)
const handleConfirmationProcessed = async (conversationId: string) => {
  if (conversationId !== activeConversationId.value) return
  for (const item of activeMessageList.value) {
    for (const execution of item.toolExecutions || []) {
      if (execution.status === 'WAITING_CONFIRMATION') {
        execution.status = 'PROCESSED'
        execution.message = '已处理，请核对结果'
      }
    }
    if (item.taskProgress?.status === 'WAITING_CONFIRMATION') {
      item.taskProgress.status = 'PARTIAL_SUCCESS'
      item.taskProgress.currentStep = '已处理，请核对结果'
    }
  }
  if (!conversationInProgress.value) await getMessageList()
}
const conversationInProgress = ref(false) // 对话是否正在进行中。目前只有【发送】消息时，会更新为 true，避免切换对话、删除对话等操作
const debugMode = ref(false)
const toolActions = ref<ToolActionVO[]>([]) // 当前角色实际可用的业务工具入口
const selectedToolAction = ref<ToolActionVO>() // 输入框携带的隐藏路由意图
const selectedIntentRequestId = ref<string>() // 写操作重试所需的稳定请求编号
const workbenchMode = ref<CpsWorkbenchMode>('SELECTION') // CPS 工作台当前分析模式
const lastWorkbenchRequest = ref<Pick<ChatMessageVO, 'content' | 'toolIntent'>>()

// 消息列表
const messageRef = ref()
const activeMessageList = ref<ChatMessageVO[]>([]) // 选中对话的消息列表
const activeMessageListLoading = ref<boolean>(false) // activeMessageList 是否正在加载中
const activeMessageListLoadingTimer = ref<any>() // activeMessageListLoading Timer 定时器。如果加载速度很快，就不进入加载中
// 消息滚动
const textSpeed = ref<number>(50) // Typing speed in milliseconds
const textRoleRunning = ref<boolean>(false) // Typing speed in milliseconds

// 发送消息输入框
const isComposing = ref(false) // 判断用户是否在输入
const conversationInAbortController = ref<any>() // 对话进行中 abort 控制器(控制 stream 对话)
const inputTimeout = ref<any>() // 处理输入中回车的定时器
const prompt = ref<string>() // prompt
const enableContext = ref<boolean>(true) // 是否开启上下文
const enableWebSearch = ref<boolean>(false) // 是否开启联网搜索
const uploadFiles = ref<string[]>([]) // 上传的文件 URL 列表
const workbenchPlaceholder = computed(() =>
  workbenchMode.value === 'SELECTION'
    ? '描述品类、价格、佣金、销量、榜单或目标人群…（Enter 发送）'
    : '描述订单范围、成交品类、价格带或收益分析需求…（Enter 发送）'
)
const currentWorkbenchPrompt = computed(
  () => prompt.value?.trim() || lastWorkbenchRequest.value?.content || ''
)
const currentWorkbenchToolIntent = computed(
  () => selectedToolAction.value?.intent || lastWorkbenchRequest.value?.toolIntent
)
const activeTaskProgress = computed(
  () =>
    [...activeMessageList.value].reverse().find((item) => item.type === 'assistant')?.taskProgress
)
const workbenchResultSummary = computed(() => {
  const latestAssistant = [...activeMessageList.value]
    .reverse()
    .find((item) => item.type === 'assistant')
  const block = latestAssistant?.blocks?.find(
    (item) =>
      item.type === 'SELECTION_REPORT' ||
      item.type === 'ALTERNATIVES_REPORT' ||
      item.type === 'GOODS_ANALYSIS' ||
      item.type === 'ORDER_PROFILE' ||
      item.type === 'ORDER_TREND' ||
      item.type === 'PRODUCT_RECOMMEND' ||
      item.type === 'PRODUCT_COMPARE' ||
      item.type === 'REBATE_SUMMARY'
  )
  return block?.summary || latestAssistant?.taskProgress?.summary || ''
})
// 接收 Stream 消息
const receiveMessageFullText = ref('')
const receiveMessageDisplayedText = ref('')
let textRollTimer: ReturnType<typeof setTimeout> | undefined

// =========== 【聊天对话】相关 ===========

/** 加载当前会话实际绑定的快捷能力；失败时保持普通聊天体验。 */
const loadToolActions = async (conversationId: string | null) => {
  toolActions.value = []
  selectedToolAction.value = undefined
  selectedIntentRequestId.value = undefined
  if (!conversationId) return
  try {
    const actions = await ChatToolActionApi.getToolActions(conversationId)
    if (activeConversationId.value === conversationId) {
      toolActions.value = Array.isArray(actions) ? actions : []
    }
  } catch {
    // 能力接口是渐进增强，失败时不打断普通聊天。
    toolActions.value = []
  }
}

/** 获取对话信息 */
const getConversation = async (id: string | null) => {
  if (!id) {
    return
  }
  const conversation: ChatConversationVO = await ChatConversationApi.getChatConversationMy(id)
  if (!conversation) {
    return
  }
  activeConversation.value = conversation
  activeConversationId.value = conversation.id
  await loadToolActions(conversation.id)
}

/**
 * 点击某个对话
 *
 * @param conversation 选中的对话
 * @return 是否切换成功
 */
const handleConversationClick = async (conversation: ChatConversationVO) => {
  // 对话进行中，不允许切换
  if (conversationInProgress.value) {
    message.alert('对话中，不允许切换!')
    return false
  }

  // 更新选中的对话 id
  activeConversationId.value = conversation.id
  activeConversation.value = conversation
  await loadToolActions(conversation.id)
  // 刷新 message 列表
  await getMessageList()
  // 滚动底部
  scrollToBottom(true)
  // 清空输入框
  prompt.value = ''
  lastWorkbenchRequest.value = undefined
  // 清空文件列表
  uploadFiles.value = []
  return true
}

/** 删除某个对话*/
const handlerConversationDelete = async (delConversation: ChatConversationVO) => {
  // 删除的对话如果是当前选中的，那么就重置
  if (activeConversationId.value === delConversation.id) {
    await handleConversationClear()
  }
}
/** 清空选中的对话 */
const handleConversationClear = async () => {
  // 对话进行中，不允许切换
  if (conversationInProgress.value) {
    message.alert('对话中，不允许切换!')
    return false
  }
  activeConversationId.value = null
  activeConversation.value = null
  activeMessageList.value = []
  toolActions.value = []
  selectedToolAction.value = undefined
  selectedIntentRequestId.value = undefined
  lastWorkbenchRequest.value = undefined
}

/** 修改聊天对话 */
const conversationUpdateFormRef = ref()
const openChatConversationUpdateForm = async () => {
  conversationUpdateFormRef.value.open(activeConversationId.value)
}
const handleConversationUpdateSuccess = async () => {
  // 对话更新成功，刷新最新信息
  await getConversation(activeConversationId.value)
}

/** 处理聊天对话的创建成功 */
const handleConversationCreate = async () => {
  // 创建对话
  await conversationListRef.value.createConversation()
}
/** 在聊天输入框内选择角色。角色能力按快照固定，因此选择后创建并切换到新会话。 */
const chooseChatRole = async () => {
  if (conversationInProgress.value) return
  if (activeConversationId.value) {
    await conversationListRef.value?.createWithAnotherRole()
  } else {
    await conversationListRef.value?.createConversation()
  }
}
/** 处理聊天对话的创建成功 */
const handleConversationCreateSuccess = async () => {
  // 创建新的对话，清空输入框
  prompt.value = ''
  // 清空文件列表
  uploadFiles.value = []
  selectedToolAction.value = undefined
  selectedIntentRequestId.value = undefined
  lastWorkbenchRequest.value = undefined
}

// =========== 【会员身份】相关 ===========
const userStore = useUserStore()
const activeMemberName = ref<string>('')

watch(
  () => activeConversation.value?.memberId,
  async (memberId) => {
    if (!memberId) {
      activeMemberName.value = ''
      return
    }
    if (memberId === userStore.getUser.id) {
      activeMemberName.value = `${userStore.getUser.nickname || '当前用户'} (ID: ${memberId})`
      return
    }
    try {
      const user = await getUser(memberId)
      activeMemberName.value = `${user?.nickname || user?.name || '会员'} (ID: ${memberId})`
    } catch {
      activeMemberName.value = `会员 (ID: ${memberId})`
    }
  },
  { immediate: true }
)

const handleActiveMemberChange = async (memberId: number) => {
  if (!activeConversation.value) return
  if (activeConversation.value.memberId === memberId) return
  try {
    await ChatConversationApi.updateChatConversationMy({
      id: activeConversation.value.id,
      memberId
    })
    activeConversation.value.memberId = memberId
    message.success('已切换本会话的运行会员')
    await conversationListRef.value?.getChatConversationList()
  } catch (error) {
    message.error('切换运行会员失败')
  }
}

// =========== 【消息列表】相关 ===========

/** 获取消息 message 列表 */
const getMessageList = async () => {
  try {
    if (activeConversationId.value === null) {
      return
    }
    // Timer 定时器，如果加载速度很快，就不进入加载中
    activeMessageListLoadingTimer.value = setTimeout(() => {
      activeMessageListLoading.value = true
    }, 60)

    // 获取消息列表
    activeMessageList.value = await ChatMessageApi.getChatMessageListByConversationId(
      activeConversationId.value
    )

    // 滚动到最下面
    await nextTick()
    await scrollToBottom()
  } finally {
    // time 定时器，如果加载速度很快，就不进入加载中
    if (activeMessageListLoadingTimer.value) {
      clearTimeout(activeMessageListLoadingTimer.value)
    }
    // 加载结束
    activeMessageListLoading.value = false
  }
}

/**
 * 消息列表
 *
 * 和 {@link #getMessageList()} 的差异是，把 systemMessage 考虑进去
 */
const messageList = computed(() => {
  if (activeMessageList.value.length > 0) {
    return activeMessageList.value
  }
  // 没有消息时，如果有 systemMessage 则展示它
  if (activeConversation.value?.systemMessage) {
    return [
      {
        id: 0,
        conversationId: activeConversation.value.id,
        type: 'system',
        userId: '',
        roleId: '',
        model: 0,
        modelId: 0,
        content: activeConversation.value.systemMessage,
        tokens: 0,
        createTime: new Date(),
        roleAvatar: '',
        userAvatar: ''
      } as ChatMessageVO
    ]
  }
  return []
})

/** 处理删除 message 消息 */
const handleMessageDelete = () => {
  if (conversationInProgress.value) {
    message.alert('回答中，不能删除!')
    return
  }
  // 刷新 message 列表
  getMessageList()
}

/** 处理 message 清空 */
const handlerMessageClear = async () => {
  if (!activeConversationId.value) {
    return
  }
  try {
    // 确认提示
    await message.delConfirm('确认清空对话消息？')
    // 清空对话
    await ChatMessageApi.deleteByConversationId(activeConversationId.value)
    // 刷新 message 列表
    activeMessageList.value = []
  } catch {}
}

/** 回到 message 列表的顶部 */
const handleGoTopMessage = () => {
  messageRef.value.handlerGoTop()
}

// =========== 【发送消息】相关 ===========

const friendlyToolText = (value?: string, fallback?: string) => toFriendlyToolText(value, fallback)

const clearSelectedToolAction = () => {
  selectedToolAction.value = undefined
  selectedIntentRequestId.value = undefined
}

/** 在工作台中重新执行最近一次失败的只读分析。 */
const retryWorkbenchTask = async () => {
  if (conversationInProgress.value || !lastWorkbenchRequest.value?.content) return
  selectedToolAction.value = toolActions.value.find(
    (action) => action.intent === lastWorkbenchRequest.value?.toolIntent
  )
  selectedIntentRequestId.value = selectedToolAction.value ? createIntentRequestId() : undefined
  await doSendMessage(lastWorkbenchRequest.value.content)
}

/** 推荐问题只预填输入框，不自动发送。 */
const handleRecommendedPrompt = (recommendedPrompt: RecommendedPrompt) => {
  prompt.value = recommendedPrompt.prompt
  selectedToolAction.value = toolActions.value.find(
    (action) => action.intent === recommendedPrompt.toolIntent
  )
  selectedIntentRequestId.value = selectedToolAction.value ? createIntentRequestId() : undefined
}

/** 处理快捷入口生成的自然语言；表单只读操作先回填，直接/写操作按风险确认后发送。 */
const handleToolActionSubmit = async ({
  action,
  prompt: generatedPrompt,
  intentRequestId,
  autoSend
}: {
  action: ToolActionVO
  prompt: string
  intentRequestId: string
  autoSend: boolean
}) => {
  selectedToolAction.value = action
  selectedIntentRequestId.value = intentRequestId
  prompt.value = generatedPrompt
  if (autoSend) {
    await doSendMessage(generatedPrompt)
  }
}

/** 处理来自 keydown 的发送消息 */
const handleSendByKeydown = async (event) => {
  // 判断用户是否在输入
  if (isComposing.value) {
    return
  }
  // 进行中不允许发送
  if (conversationInProgress.value) {
    return
  }
  const content = prompt.value?.trim() as string
  if (event.key === 'Enter') {
    if (event.shiftKey) {
      // 插入换行
      prompt.value += '\r\n'
      event.preventDefault() // 防止默认的换行行为
    } else {
      // 发送消息
      await doSendMessage(content)
      event.preventDefault() // 防止默认的提交行为
    }
  }
}

/** 处理来自【发送】按钮的发送消息 */
const handleSendByButton = () => {
  doSendMessage(prompt.value?.trim() as string)
}

/** 处理 prompt 输入变化 */
const handlePromptInput = (event) => {
  // 非输入法 输入设置为 true
  if (!isComposing.value) {
    // 回车 event data 是 null
    if (event.data == null) {
      return
    }
    isComposing.value = true
  }
  // 清理定时器
  if (inputTimeout.value) {
    clearTimeout(inputTimeout.value)
  }
  // 重置定时器
  inputTimeout.value = setTimeout(() => {
    isComposing.value = false
  }, 400)
}
// TODO @芋艿：是不是可以通过 @keydown.enter、@keydown.shift.enter 来实现，回车发送、shift+回车换行；主要看看，是不是可以简化 isComposing 相关的逻辑
const onCompositionstart = () => {
  isComposing.value = true
}
const onCompositionend = () => {
  // console.log('输入结束...')
  setTimeout(() => {
    isComposing.value = false
  }, 200)
}

/** 真正执行【发送】消息操作 */
const doSendMessage = async (content: string) => {
  // 校验
  if (content.length < 1) {
    message.error('发送失败，原因：内容为空！')
    return
  }
  if (activeConversationId.value == null) {
    message.error('还没创建对话，不能发送!')
    return
  }

  // 准备附件 URL 数组
  const attachmentUrls = [...uploadFiles.value]
  const toolIntent = selectedToolAction.value?.intent
  const intentRequestId = selectedIntentRequestId.value
  if (toolIntent) {
    lastWorkbenchRequest.value = { content, toolIntent }
  }

  // 清空输入框和文件列表
  prompt.value = ''
  uploadFiles.value = []
  clearSelectedToolAction()

  // 执行发送
  await doSendMessageStream({
    conversationId: activeConversationId.value,
    content: content,
    attachmentUrls: attachmentUrls,
    toolIntent,
    intentRequestId
  } as ChatMessageVO)
}

const updateToolExecution = (
  execution: ToolExecutionVO,
  eventType: Exclude<NonNullable<ChatMessageStreamData['eventType']>, 'MESSAGE_DELTA'>
) => {
  const assistantMessage = [...activeMessageList.value]
    .reverse()
    .find((item) => item.type === 'assistant')
  if (!assistantMessage) return
  const statusByEvent = {
    TOOL_STARTED: 'RUNNING',
    TOOL_SUCCEEDED: 'SUCCEEDED',
    TOOL_FAILED: 'FAILED',
    TOOL_CONFIRM_REQUIRED: 'WAITING_CONFIRMATION'
  } as const
  const fallbackByEvent = {
    TOOL_STARTED: `正在${friendlyToolText(execution.label, '处理业务请求')}`,
    TOOL_SUCCEEDED: `${friendlyToolText(execution.label, '业务处理')}已完成`,
    TOOL_FAILED: `${friendlyToolText(execution.label, '业务处理')}未完成，请稍后重试`,
    TOOL_CONFIRM_REQUIRED: '等待用户确认'
  } as const
  assistantMessage.toolExecutions = upsertToolExecution(assistantMessage.toolExecutions || [], {
    ...execution,
    status: execution.status || statusByEvent[eventType],
    message: friendlyToolText(execution.message, fallbackByEvent[eventType])
  })
  if (assistantMessage.content === '正在理解需求…') {
    assistantMessage.content = ''
  }
  assistantMessage.taskProgress = updateTaskProgress(
    assistantMessage.taskProgress,
    execution,
    eventType
  )
}

const mergeUsage = (current: ChatMessageVO['usage'], next: ChatMessageVO['usage']) => ({
  ...(current || {}),
  ...(next || {})
})

const upsertDebugTrace = (
  current: NonNullable<ChatMessageVO['debugTraces']>,
  next: NonNullable<ChatMessageStreamData['modelTrace']>
) => {
  const index = current.findIndex((item) => item.traceId === next.traceId)
  if (index < 0) return [...current, next]
  const result = [...current]
  result[index] = { ...result[index], ...next, usage: mergeUsage(result[index].usage, next.usage) }
  return result
}

const applyModelTrace = (data: ChatMessageStreamData) => {
  const assistant = getCurrentAssistantMessage()
  if (!assistant || !data.modelTrace) return
  assistant.debugTraces = upsertDebugTrace(assistant.debugTraces || [], data.modelTrace)
  if (data.usage) assistant.usage = mergeUsage(assistant.usage, data.usage)
}

/** Apply server task lifecycle events while keeping tool events backwards compatible. */
const updateTaskLifecycle = (data: ChatMessageStreamData) => {
  const assistant = getCurrentAssistantMessage()
  if (!assistant) return
  const eventType = data.eventType
  if (!eventType || eventType === 'MESSAGE_DELTA' || eventType.startsWith('TOOL_')) return
  if (
    assistant.toolExecutions?.some((item) => item.status === 'WAITING_CONFIRMATION') &&
    eventType !== 'TASK_FAILED'
  )
    return
  const previous = assistant.taskProgress || createTaskProgress()
  const payload = data.taskProgress || {}
  const status = (payload.status ||
    data.taskStatus ||
    (eventType === 'TASK_ACCEPTED'
      ? 'QUEUED'
      : eventType === 'TASK_FAILED'
        ? 'FAILED'
        : eventType === 'TASK_COMPLETE'
          ? 'SUCCEEDED'
          : 'RUNNING')) as ChatTaskProgress['status']
  const percent =
    typeof payload.percent === 'number'
      ? payload.percent
      : typeof data.percent === 'number'
        ? data.percent
        : status === 'SUCCEEDED' || status === 'FAILED'
          ? 100
          : previous.percent
  const completedItems = payload.completedItems ?? data.completedItems
  const totalItems = payload.totalItems ?? data.totalItems
  const stage = payload.stage || data.stage
  const eventMessage = payload.currentStep || data.message
  const friendlyMessage = toFriendlyToolText(
    eventMessage,
    status === 'FAILED'
      ? '任务未完成，可重试'
      : status === 'SUCCEEDED'
        ? '任务已完成'
        : '正在处理您的请求'
  )
  assistant.taskProgress = {
    ...previous,
    ...payload,
    status,
    percent: Math.max(0, Math.min(100, percent)),
    taskId: payload.taskId || data.taskId || previous.taskId,
    stage,
    completedItems,
    totalItems,
    elapsedMs: payload.elapsedMs ?? data.elapsedMs ?? previous.elapsedMs,
    degraded: Boolean(payload.degraded ?? data.degraded ?? previous.degraded),
    currentStep: friendlyMessage,
    error: status === 'FAILED' ? friendlyMessage : undefined,
    retryable: status === 'FAILED' ? Boolean(lastWorkbenchRequest.value?.toolIntent) : false,
    summary: status === 'SUCCEEDED' ? friendlyMessage : previous.summary,
    steps: previous.steps
  }
  if (assistant.content === '正在理解需求…') assistant.content = ''
}

const createTaskProgress = (): ChatTaskProgress => ({
  status: 'QUEUED',
  percent: 5,
  currentStep: '正在理解分析需求',
  steps: [
    { id: 'understand', label: '理解任务与筛选条件', status: 'RUNNING' },
    { id: 'retrieve', label: '获取商品或成交数据', status: 'PENDING' },
    { id: 'analyze', label: '分析证据与风险', status: 'PENDING' },
    { id: 'report', label: '生成结构化结果', status: 'PENDING' }
  ]
})

const updateTaskProgress = (
  current: ChatTaskProgress | undefined,
  execution: ToolExecutionVO,
  eventType: Exclude<NonNullable<ChatMessageStreamData['eventType']>, 'MESSAGE_DELTA'>
): ChatTaskProgress => {
  const progress = current || createTaskProgress()
  const steps: ChatTaskStep[] = progress.steps.map((step) => ({ ...step }))
  const retrieve = steps.find((step) => step.id === 'retrieve')!
  const analyze = steps.find((step) => step.id === 'analyze')!
  const report = steps.find((step) => step.id === 'report')!
  const understand = steps.find((step) => step.id === 'understand')!
  if (eventType === 'TOOL_STARTED') {
    understand.status = 'SUCCEEDED'
    retrieve.status = 'RUNNING'
    retrieve.message = friendlyToolText(execution.label, '正在获取数据')
    return {
      ...progress,
      status: 'RUNNING',
      percent: 45,
      currentStep: retrieve.message,
      error: undefined,
      retryable: false,
      steps
    }
  }
  if (eventType === 'TOOL_CONFIRM_REQUIRED') {
    understand.status = 'SUCCEEDED'
    retrieve.status = 'PENDING'
    retrieve.message = '等待用户确认'
    void confirmationCardsRef.value?.refresh()
    return {
      ...progress,
      status: 'WAITING_CONFIRMATION',
      percent: 45,
      currentStep: '等待用户确认',
      error: undefined,
      retryable: false,
      steps
    }
  }
  if (eventType === 'TOOL_SUCCEEDED') {
    understand.status = 'SUCCEEDED'
    retrieve.status = 'SUCCEEDED'
    retrieve.message = friendlyToolText(execution.label, '数据已获取')
    analyze.status = 'RUNNING'
    return {
      ...progress,
      status: 'RUNNING',
      percent: 75,
      currentStep: '正在整理数据证据与风险提示',
      error: undefined,
      retryable: false,
      steps
    }
  }
  understand.status = 'SUCCEEDED'
  retrieve.status = 'FAILED'
  retrieve.message = friendlyToolText(execution.label, '数据获取失败')
  analyze.status = 'SKIPPED'
  report.status = 'SKIPPED'
  return {
    ...progress,
    status: 'FAILED',
    percent: 100,
    currentStep: '任务未完成，可重试',
    error: friendlyToolText(execution.message, '数据获取失败，请稍后重试'),
    retryable: true,
    steps
  }
}

const completeTaskProgress = (assistantMessage?: ChatMessageVO) => {
  if (
    !assistantMessage?.taskProgress ||
    assistantMessage.taskProgress.status === 'FAILED' ||
    assistantMessage.toolExecutions?.some((item) => item.status === 'WAITING_CONFIRMATION')
  )
    return

  const hasBlocks = Boolean(assistantMessage.blocks?.length)
  const hasToolExecutions = Boolean(assistantMessage.toolExecutions?.length)

  // 如果既没有工具执行，也没有生成任何结果块，说明是普通文字回复，不需要展示结构化任务卡片
  if (!hasBlocks && !hasToolExecutions) {
    assistantMessage.taskProgress = undefined
    return
  }

  const structuredSummary = assistantMessage.blocks?.find(
    (block) =>
      block.type === 'SELECTION_REPORT' ||
      block.type === 'ALTERNATIVES_REPORT' ||
      block.type === 'GOODS_ANALYSIS' ||
      block.type === 'ORDER_PROFILE' ||
      block.type === 'ORDER_TREND' ||
      block.type === 'PRODUCT_RECOMMEND' ||
      block.type === 'PRODUCT_COMPARE' ||
      block.type === 'REBATE_SUMMARY'
  )?.summary

  assistantMessage.taskProgress = {
    ...assistantMessage.taskProgress,
    status: 'SUCCEEDED',
    percent: 100,
    currentStep: '结构化结果已生成',
    summary: structuredSummary || '分析已完成，可查看数据证据、推荐理由和风险提示。',
    retryable: false,
    steps: assistantMessage.taskProgress.steps.map((step) => ({
      ...step,
      status: step.status === 'PENDING' || step.status === 'RUNNING' ? 'SUCCEEDED' : step.status
    }))
  }
}

/** 真正执行【发送】消息操作 */
const doSendMessageStream = async (userMessage: ChatMessageVO) => {
  // 创建 AbortController 实例，以便中止请求
  conversationInAbortController.value = new AbortController()
  // 标记对话进行中
  conversationInProgress.value = true
  // 设置为空
  receiveMessageFullText.value = ''

  try {
    // 1.1 先添加两个假数据，等 stream 返回再替换
    activeMessageList.value.push({
      id: -1,
      conversationId: activeConversationId.value!,
      type: 'user',
      userId: '',
      roleId: '',
      model: 0,
      modelId: 0,
      content: userMessage.content,
      attachmentUrls: userMessage.attachmentUrls || [],
      tokens: 0,
      createTime: new Date(),
      roleAvatar: '',
      userAvatar: ''
    })
    activeMessageList.value.push({
      id: -2,
      conversationId: activeConversationId.value!,
      type: 'assistant',
      userId: '',
      roleId: '',
      model: 0,
      modelId: 0,
      content: '正在理解需求…',
      reasoningContent: '',
      toolExecutions: [],
      taskProgress: userMessage.toolIntent ? createTaskProgress() : undefined,
      tokens: 0,
      createTime: new Date(),
      roleAvatar: '',
      userAvatar: ''
    })
    // 1.2 滚动到最下面
    await nextTick()
    await scrollToBottom() // 底部
    // 1.3 开始滚动
    textRoll()

    // 2. 发送 event stream
    let isFirstChunk = true // 是否是第一个 chunk 消息段
    let streamCompleted = false
    let streamFailed = false
    const handleStreamFailure = (reason = '分析请求异常中断，请重试') => {
      if (streamFailed) return
      streamFailed = true
      failCurrentTask(reason)
      stopStream('FAILED')
    }
    await ChatMessageApi.sendChatMessageStream(
      userMessage.conversationId,
      userMessage.content,
      conversationInAbortController.value,
      enableContext.value,
      enableWebSearch.value,
      debugMode.value,
      async (res) => {
        let payload: { code: number; data: ChatMessageStreamData; msg?: string }
        try {
          payload = JSON.parse(res.data)
        } catch {
          handleStreamFailure('服务端返回了无法解析的响应，请重试')
          return
        }
        const { code, data, msg } = payload
        if (code !== 0) {
          handleStreamFailure(friendlyToolText(msg, '分析请求未完成，请稍后重试'))
          return
        }

        const eventType = data.eventType || 'MESSAGE_DELTA'
        if (eventType === 'MODEL_TRACE') {
          applyModelTrace(data)
          await scrollToBottom()
          return
        }
        if (eventType.startsWith('TASK_')) {
          if (eventType === 'TASK_COMPLETE') streamCompleted = true
          updateTaskLifecycle(data)
          await scrollToBottom()
          return
        }
        if (eventType === 'MESSAGE_COMPLETE') {
          streamCompleted = true
          if (data.send && data.receive) {
            if (isFirstChunk) {
              isFirstChunk = false
              const pendingAssistant = getCurrentAssistantMessage()
              const pendingDebugTraces = pendingAssistant?.debugTraces || []
              const pendingUsage = pendingAssistant?.usage
              activeMessageList.value.pop()
              activeMessageList.value.pop()
              activeMessageList.value.push(data.send)
              data.send.attachmentUrls = userMessage.attachmentUrls
              data.receive.debugTraces = data.modelTrace
                ? upsertDebugTrace(pendingDebugTraces, data.modelTrace)
                : pendingDebugTraces
              data.receive.toolExecutions = pendingAssistant?.toolExecutions || []
              data.receive.taskProgress = pendingAssistant?.taskProgress
              if (!data.receive.blocks?.length && pendingAssistant?.blocks?.length) {
                data.receive.blocks = pendingAssistant.blocks
              }
              data.receive.usage = mergeUsage(pendingUsage, data.receive.usage)
              activeMessageList.value.push(data.receive)
            } else {
              const lastMessage = getCurrentAssistantMessage()
              if (lastMessage) {
                const previousBlocks = lastMessage.blocks
                const previousToolExecutions = lastMessage.toolExecutions
                const previousTaskProgress = lastMessage.taskProgress
                Object.assign(lastMessage, data.receive)
                if (!data.receive.blocks?.length && previousBlocks?.length) {
                  lastMessage.blocks = previousBlocks
                }
                if (!data.receive.toolExecutions?.length && previousToolExecutions?.length) {
                  lastMessage.toolExecutions = previousToolExecutions
                }
                if (!data.receive.taskProgress && previousTaskProgress) {
                  lastMessage.taskProgress = previousTaskProgress
                }
                if (data.modelTrace) {
                  lastMessage.debugTraces = upsertDebugTrace(
                    lastMessage.debugTraces || [],
                    data.modelTrace
                  )
                }
              }
            }
            const completedText = data.receive.content || ''
            if (!completedText.startsWith(receiveMessageDisplayedText.value)) {
              receiveMessageDisplayedText.value = ''
            }
            receiveMessageFullText.value = completedText
            if (data.usage) {
              const lastMessage = getCurrentAssistantMessage()
              if (lastMessage) lastMessage.usage = mergeUsage(lastMessage.usage, data.usage)
            }
          }
          await scrollToBottom()
          return
        }
        if (eventType !== 'MESSAGE_DELTA' && data.toolExecution) {
          updateToolExecution(data.toolExecution, eventType)
          await scrollToBottom()
          return
        }

        if (!data.send || !data.receive) {
          return
        }

        // 如果内容为空，就不处理。
        if (
          data.receive.content === '' &&
          !data.receive.reasoningContent &&
          !data.receive.blocks?.length
        ) {
          return
        }

        // 首次返回需要添加一个 message 到页面，后面的都是更新
        if (isFirstChunk) {
          isFirstChunk = false
          const pendingAssistant = activeMessageList.value[activeMessageList.value.length - 1]
          const toolExecutions = pendingAssistant?.toolExecutions || []
          const taskProgress = pendingAssistant?.taskProgress
          const debugTraces = pendingAssistant?.debugTraces || []
          const usage = pendingAssistant?.usage
          // 弹出两个假数据
          activeMessageList.value.pop()
          activeMessageList.value.pop()
          // 更新返回的数据
          activeMessageList.value.push(data.send)
          data.send.attachmentUrls = userMessage.attachmentUrls
          data.receive.toolExecutions = toolExecutions
          data.receive.taskProgress = taskProgress
          data.receive.debugTraces = debugTraces
          data.receive.usage = mergeUsage(usage, data.receive.usage)
          activeMessageList.value.push(data.receive)
        }

        // 处理 reasoningContent
        if (data.receive.reasoningContent) {
          const lastMessage = activeMessageList.value[activeMessageList.value.length - 1]
          lastMessage.reasoningContent =
            lastMessage.reasoningContent + data.receive.reasoningContent
        }

        if (data.receive.blocks?.length) {
          const lastMessage = activeMessageList.value[activeMessageList.value.length - 1]
          lastMessage.blocks = data.receive.blocks
        }

        if (data.usage) {
          const lastMessage = getCurrentAssistantMessage()
          if (lastMessage) lastMessage.usage = mergeUsage(lastMessage.usage, data.usage)
        }

        // 处理正常内容
        if (data.receive.content !== '') {
          receiveMessageFullText.value = receiveMessageFullText.value + data.receive.content
        }
        // 滚动到最下面
        await scrollToBottom()
      },
      (error: any) => {
        // 异常提示，并停止流
        handleStreamFailure('分析请求异常中断，请重试')
        // 需要抛出异常，禁止重试
        throw error
      },
      () => {
        // 人工停止或异常中止也会触发 onclose，不能把已停止/失败任务覆盖成成功。
        if (!conversationInProgress.value) return
        if (!streamCompleted) {
          handleStreamFailure('响应流意外中断，请重试')
          return
        }
        completeTaskProgress(getCurrentAssistantMessage())
        stopStream('SUCCEEDED')
      },
      userMessage.attachmentUrls,
      userMessage.toolIntent,
      userMessage.intentRequestId
    )
  } catch {}
}

/** 停止 stream 流式调用 */
const getCurrentAssistantMessage = () =>
  [...activeMessageList.value].reverse().find((item) => item.type === 'assistant')

const failCurrentTask = (error: string) => {
  const assistantMessage = getCurrentAssistantMessage()
  if (!assistantMessage) return
  if (!assistantMessage.taskProgress) {
    if (assistantMessage.content === '正在理解需求…') assistantMessage.content = error
    return
  }
  assistantMessage.taskProgress = {
    ...assistantMessage.taskProgress,
    status: 'FAILED',
    percent: 100,
    currentStep: '任务未完成，可重试',
    error,
    retryable: Boolean(lastWorkbenchRequest.value?.toolIntent),
    steps: assistantMessage.taskProgress.steps.map((step) => ({
      ...step,
      status: step.status === 'RUNNING' ? 'FAILED' : step.status
    }))
  }
}

const cancelCurrentTask = () => {
  const assistantMessage = getCurrentAssistantMessage()
  if (
    !assistantMessage?.taskProgress ||
    assistantMessage.taskProgress.status === 'FAILED' ||
    assistantMessage.toolExecutions?.some((item) => item.status === 'WAITING_CONFIRMATION')
  )
    return
  assistantMessage.taskProgress = {
    ...assistantMessage.taskProgress,
    status: 'CANCELLED',
    currentStep: '任务已停止',
    summary: '本次分析已停止，已有数据可能不完整。',
    retryable: Boolean(lastWorkbenchRequest.value?.toolIntent),
    steps: assistantMessage.taskProgress.steps.map((step) => ({
      ...step,
      status: step.status === 'RUNNING' ? 'SKIPPED' : step.status
    }))
  }
}

/** 停止 stream 流式调用。正常关闭会先完成任务状态，人工停止则保留可重试入口。 */
const stopStream = async (result: 'SUCCEEDED' | 'FAILED' | 'CANCELLED' = 'CANCELLED') => {
  // tip：如果 stream 进行中的 message，就需要调用 controller 结束
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort()
  }
  if (result === 'CANCELLED') cancelCurrentTask()
  if (result === 'FAILED') failCurrentTask('分析请求异常中断，请重试')
  // 设置为 false
  conversationInProgress.value = false
  void confirmationCardsRef.value?.refresh()
}

/** 编辑 message：设置为 prompt，可以再次编辑 */
const handleMessageEdit = (message: ChatMessageVO) => {
  prompt.value = message.content
}

/** 刷新 message：基于指定消息，再次发起对话 */
const handleMessageRefresh = (message: ChatMessageVO) => {
  doSendMessage(message.content)
}

// ============== 【消息滚动】相关 =============

/** 滚动到 message 底部 */
const scrollToBottom = async (isIgnore?: boolean) => {
  await nextTick()
  if (messageRef.value) {
    messageRef.value.scrollToBottom(isIgnore)
  }
}

/** 自提滚动效果 */
const textRoll = async () => {
  try {
    // 只能执行一次
    if (textRoleRunning.value) {
      return
    }
    // 设置状态
    textRoleRunning.value = true
    receiveMessageDisplayedText.value = ''
    const task = async () => {
      // 调整速度
      const diff =
        (receiveMessageFullText.value.length - receiveMessageDisplayedText.value.length) / 10
      if (diff > 5) {
        textSpeed.value = 10
      } else if (diff > 2) {
        textSpeed.value = 30
      } else if (diff > 1.5) {
        textSpeed.value = 50
      } else {
        textSpeed.value = 100
      }
      // 对话结束，就按 30 的速度
      if (!conversationInProgress.value) {
        textSpeed.value = 10
      }

      if (receiveMessageDisplayedText.value.length < receiveMessageFullText.value.length) {
        const index = receiveMessageDisplayedText.value.length
        receiveMessageDisplayedText.value += receiveMessageFullText.value[index]

        // 更新 message
        const lastMessage = activeMessageList.value[activeMessageList.value.length - 1]
        lastMessage.content = receiveMessageDisplayedText.value
        // 滚动到住下面
        await scrollToBottom()
        // 重新设置任务
        textRollTimer = setTimeout(task, textSpeed.value)
      } else {
        // 不是对话中可以结束
        if (!conversationInProgress.value) {
          textRoleRunning.value = false
          clearTimeout(textRollTimer)
        } else {
          // 重新设置任务
          textRollTimer = setTimeout(task, textSpeed.value)
        }
      }
    }
    textRollTimer = setTimeout(task, textSpeed.value)
  } catch {}
}

onBeforeUnmount(() => {
  clearTimeout(textRollTimer)
  conversationInAbortController.value?.abort()
})

/** 初始化 **/
onMounted(async () => {
  // 如果有 conversationId 参数，则默认选中
  if (route.query.conversationId) {
    const id = Array.isArray(route.query.conversationId)
      ? route.query.conversationId[0]
      : route.query.conversationId
    if (!id) return
    activeConversationId.value = id
    await getConversation(id)
  }

  // 获取列表数据
  activeMessageListLoading.value = true
  await getMessageList()
})
</script>
