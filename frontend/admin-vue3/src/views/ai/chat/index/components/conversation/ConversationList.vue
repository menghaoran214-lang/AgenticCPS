<!--  AI 对话  -->
<template>
  <el-aside
    width="260px"
    class="conversation-aside h-100% relative flex flex-col justify-between px-2.5 pt-2.5 pb-0 overflow-hidden bg-[var(--el-bg-color)] border-r border-[var(--el-border-color-light)]"
  >
    <!-- 左顶部：操作与搜索区 -->
    <div class="shrink-0">
      <el-button class="w-full !py-4.5" type="primary" @click="createConversation()">
        <Icon icon="ep:plus" class="mr-5px" />
        新建对话
      </el-button>

      <!-- 搜索历史记录 -->
      <el-input
        v-model="searchName"
        size="default"
        class="mt-2.5"
        placeholder="搜索历史记录"
        clearable
        @clear="searchConversation"
        @keyup="searchConversation"
      >
        <template #prefix>
          <Icon icon="ep:search" />
        </template>
      </el-input>
    </div>

    <!-- 左中间：对话列表（使用 el-scrollbar 优化滚动体验，杜绝原生滚动条遮挡按钮） -->
    <div class="flex-1 min-h-0 mt-2 relative">
      <el-scrollbar class="h-full" wrap-class="overflow-x-hidden pr-1.5" view-class="pb-2">
        <!-- 情况一：加载中 -->
        <el-empty v-if="loading" description="加载中..." :image-size="60" />
        <!-- 情况二：按照 group 分组，展示聊天会话 list 列表 -->
        <template v-else>
          <div v-for="conversationKey in Object.keys(conversationMap)" :key="conversationKey">
            <div class="pt-2 pb-1" v-if="conversationMap[conversationKey]?.length">
              <el-text class="px-1 text-12px font-bold text-[var(--el-text-color-secondary)]">
                {{ conversationKey }}
              </el-text>
            </div>
            <div
              class="mb-1"
              v-for="conversation in conversationMap[conversationKey]"
              :key="conversation.id"
              @click="handleConversationClick(conversation.id)"
              @mouseenter="hoverConversationId = conversation.id"
              @mouseleave="hoverConversationId = ''"
            >
              <div
                class="conversation-item flex flex-row items-center justify-between px-2 py-1.5 cursor-pointer rounded-lg border leading-6 transition-all"
                :class="[
                  conversation.id === activeConversationId
                    ? 'is-active bg-[var(--el-color-primary-light-9)] border-[var(--el-color-primary-light-7)]'
                    : 'border-transparent hover:bg-[var(--el-fill-color-light)]'
                ]"
              >
                <!-- 会话角色头像与标题（flex-1 + min-w-0 + truncate 保证超出时优雅省略，绝不挤压按钮） -->
                <div class="flex flex-row items-center flex-1 min-w-0 mr-1.5">
                  <AiRoleAvatar
                    class="w-6.5 h-6.5 rounded flex-shrink-0"
                    :src="conversation.roleAvatar"
                    :preset-code="conversation.rolePresetCode"
                    name="AI"
                  />
                  <span
                    class="ml-2 truncate text-13px"
                    :class="[
                      conversation.id === activeConversationId
                        ? 'text-[var(--el-color-primary)] font-medium'
                        : 'text-[var(--el-text-color-regular)]'
                    ]"
                    :title="conversation.title"
                  >
                    {{ conversation.title }}
                  </span>
                </div>

                <!-- 悬浮操作按钮组：紧凑且具有独立悬浮态与防遮挡边距 -->
                <div
                  class="conversation-actions flex-shrink-0 flex items-center gap-1"
                  v-show="hoverConversationId === conversation.id"
                >
                  <el-tooltip
                    :content="conversation.pinned ? '取消置顶' : '置顶对话'"
                    placement="top"
                    :show-after="300"
                  >
                    <button
                      type="button"
                      class="action-btn"
                      @click.stop="handleTop(conversation)"
                    >
                      <el-icon :size="13">
                        <Top v-if="!conversation.pinned" />
                        <Bottom v-else />
                      </el-icon>
                    </button>
                  </el-tooltip>

                  <el-tooltip content="重命名" placement="top" :show-after="300">
                    <button
                      type="button"
                      class="action-btn"
                      @click.stop="updateConversationTitle(conversation)"
                    >
                      <el-icon :size="13">
                        <Icon icon="ep:edit" />
                      </el-icon>
                    </button>
                  </el-tooltip>

                  <el-tooltip content="删除对话" placement="top" :show-after="300">
                    <button
                      type="button"
                      class="action-btn action-btn-danger"
                      @click.stop="deleteChatConversation(conversation)"
                    >
                      <el-icon :size="13">
                        <Icon icon="ep:delete" />
                      </el-icon>
                    </button>
                  </el-tooltip>
                </div>
              </div>
            </div>
          </div>
        </template>
      </el-scrollbar>
    </div>

    <!-- 左底部：工具栏（自然位于 flex 底部，避免 absolute 定位导致底部会话与滚动条重叠） -->
    <div
      class="shrink-0 h-9 px-4 flex justify-between items-center border-t border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-extra-light)] -mx-2.5 text-[var(--el-text-color-regular)]"
    >
      <div
        class="flex items-center cursor-pointer hover:text-[var(--el-color-primary)] transition-colors"
        @click="handleRoleRepository"
      >
        <Icon icon="ep:user" />
        <span class="ml-1 text-xs">角色仓库</span>
      </div>
      <div
        class="flex items-center cursor-pointer hover:text-[var(--el-color-danger)] transition-colors"
        @click="handleClearConversation"
      >
        <Icon icon="ep:delete" />
        <span class="ml-1 text-xs">清空未置顶对话</span>
      </div>
    </div>

    <!-- 角色仓库抽屉 -->
    <el-drawer v-model="roleRepositoryOpen" title="角色仓库" size="754px">
      <RoleRepository />
    </el-drawer>
    <ChatRolePicker ref="chatRolePickerRef" @success="handleMcpTestConversationCreate" />
  </el-aside>
</template>

<script setup lang="ts">
import { ChatConversationApi, ChatConversationVO } from '@/api/ai/chat/conversation'
import RoleRepository from '../role/RoleRepository.vue'
import ChatRolePicker from '../role/ChatRolePicker.vue'
import { Bottom, Top } from '@element-plus/icons-vue'
import AiRoleAvatar from '@/components/AiRoleAvatar/index.vue'
import { useUserStore } from '@/store/modules/user'

const message = useMessage() // 消息弹窗
const userStore = useUserStore()

// 定义属性
const searchName = ref<string>('') // 对话搜索
const activeConversationId = ref<string | null>(null) // 选中的对话，默认为 null
const hoverConversationId = ref<string | null>(null) // 悬浮上去的对话
const conversationList = ref([] as ChatConversationVO[]) // 对话列表
const conversationMap = ref<any>({}) // 对话分组 (置顶、今天、三天前、一星期前、一个月前)
const loading = ref<boolean>(false) // 加载中
const loadingTime = ref<any>() // 加载中定时器
const chatRolePickerRef = ref<InstanceType<typeof ChatRolePicker>>()

// 定义组件 props
const props = defineProps({
  activeId: {
    type: String || null,
    required: true
  },
  activeMemberId: {
    type: Number,
    default: undefined
  }
})

// 定义钩子
const emits = defineEmits([
  'onConversationCreate',
  'onConversationClick',
  'onConversationClear',
  'onConversationDelete'
])


/** 搜索对话 */
const searchConversation = async () => {
  // 恢复数据
  if (!searchName.value.trim().length) {
    conversationMap.value = await getConversationGroupByCreateTime(conversationList.value)
  } else {
    // 过滤
    const filterValues = conversationList.value.filter((item) => {
      return item.title.includes(searchName.value.trim())
    })
    conversationMap.value = await getConversationGroupByCreateTime(filterValues)
  }
}

/** 点击对话 */
const handleConversationClick = async (id: string) => {
  // 过滤出选中的对话
  const filterConversation = conversationList.value.filter((item) => {
    return item.id === id
  })
  // 回调 onConversationClick
  // noinspection JSVoidFunctionReturnValueUsed
  emits('onConversationClick', filterConversation[0])
}

/** 获取对话列表 */
const getChatConversationList = async () => {
  try {
    // 加载中
    loadingTime.value = setTimeout(() => {
      loading.value = true
    }, 50)

    // 1.1 获取 对话数据
    conversationList.value = await ChatConversationApi.getChatConversationMyList()
    // 1.2 排序
    conversationList.value.sort((a, b) => {
      return new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime()
    })
    // 1.3 没有任何对话情况
    if (conversationList.value.length === 0) {
      activeConversationId.value = null
      conversationMap.value = {}
      return
    }

    // 2. 对话根据时间分组(置顶、今天、一天前、三天前、七天前、30 天前)
    conversationMap.value = await getConversationGroupByCreateTime(conversationList.value)
  } finally {
    // 清理定时器
    if (loadingTime.value) {
      clearTimeout(loadingTime.value)
    }
    // 加载完成
    loading.value = false
  }
}

/** 按照 creteTime 创建时间，进行分组 */
const getConversationGroupByCreateTime = async (list: ChatConversationVO[]) => {
  // 排序、指定、时间分组(今天、一天前、三天前、七天前、30天前)
  // noinspection NonAsciiCharacters
  const groupMap = {
    置顶: [] as ChatConversationVO[],
    今天: [] as ChatConversationVO[],
    一天前: [] as ChatConversationVO[],
    三天前: [] as ChatConversationVO[],
    七天前: [] as ChatConversationVO[],
    三十天前: [] as ChatConversationVO[]
  }
  // 当前时间的时间戳
  const now = Date.now()
  // 定义时间间隔常量（单位：毫秒）
  const oneDay = 24 * 60 * 60 * 1000
  const threeDays = 3 * oneDay
  const sevenDays = 7 * oneDay
  const thirtyDays = 30 * oneDay
  for (const conversation of list) {
    // 置顶
    if (conversation.pinned) {
      groupMap['置顶'].push(conversation)
      continue
    }
    // 计算时间差（单位：毫秒）
    const diff = now - new Date(conversation.createTime || 0).getTime()
    // 根据时间间隔判断
    if (diff < oneDay) {
      groupMap['今天'].push(conversation)
    } else if (diff < threeDays) {
      groupMap['一天前'].push(conversation)
    } else if (diff < sevenDays) {
      groupMap['三天前'].push(conversation)
    } else if (diff < thirtyDays) {
      groupMap['七天前'].push(conversation)
    } else {
      groupMap['三十天前'].push(conversation)
    }
  }
  return groupMap
}

/** 解析目标会员 ID，防止传入原生 DOM 事件等非数值类型 */
const resolveTargetMemberId = (memberId?: unknown): number | undefined => {
  if (typeof memberId === 'number' && !Number.isNaN(memberId)) {
    return memberId
  }
  if (typeof props.activeMemberId === 'number' && !Number.isNaN(props.activeMemberId)) {
    return props.activeMemberId
  }
  const currentUserId = userStore.getUser?.id
  if (typeof currentUserId === 'number' && !Number.isNaN(currentUserId)) {
    return currentUserId
  }
  return 1
}

/** 新建对话 */
const createConversation = async (memberId?: unknown) => {
  const targetMember = resolveTargetMemberId(memberId)
  await chatRolePickerRef.value?.open(undefined, targetMember)
}
const createWithAnotherRole = async (memberId?: unknown) => {
  const targetMember = resolveTargetMemberId(memberId)
  await chatRolePickerRef.value?.open(props.activeId || undefined, targetMember)
}
const handleMcpTestConversationCreate = async (conversationId: string) => {
  await getChatConversationList()
  await handleConversationClick(conversationId)
  emits('onConversationCreate')
}

/** 修改对话的标题 */
const updateConversationTitle = async (conversation: ChatConversationVO) => {
  // 1. 二次确认
  const { value } = await ElMessageBox.prompt('修改标题', {
    inputPattern: /^[\s\S]*.*\S[\s\S]*$/, // 判断非空，且非空格
    inputErrorMessage: '标题不能为空',
    inputValue: conversation.title
  })
  // 2. 发起修改
  await ChatConversationApi.updateChatConversationMy({
    id: conversation.id,
    title: value
  } as ChatConversationVO)
  message.success('重命名成功')
  // 3. 刷新列表
  await getChatConversationList()
  // 4. 过滤当前切换的
  const filterConversationList = conversationList.value.filter((item) => {
    return item.id === conversation.id
  })
  if (filterConversationList.length > 0) {
    // tip：避免切换对话
    if (activeConversationId.value === filterConversationList[0].id) {
      emits('onConversationClick', filterConversationList[0])
    }
  }
}

/** 删除聊天对话 */
const deleteChatConversation = async (conversation: ChatConversationVO) => {
  try {
    // 删除的二次确认
    await message.delConfirm(`是否确认删除对话 - ${conversation.title}?`)
    // 发起删除
    await ChatConversationApi.deleteChatConversationMy(conversation.id)
    message.success('对话已删除')
    // 刷新列表
    await getChatConversationList()
    // 回调
    emits('onConversationDelete', conversation)
  } catch {}
}

/** 清空对话 */
const handleClearConversation = async () => {
  try {
    await message.confirm('确认后对话会全部清空，置顶的对话除外。')
    await ChatConversationApi.deleteChatConversationMyByUnpinned()
    ElMessage({
      message: '操作成功!',
      type: 'success'
    })
    // 清空 对话 和 对话内容
    activeConversationId.value = null
    // 获取 对话列表
    await getChatConversationList()
    // 回调 方法
    emits('onConversationClear')
  } catch {}
}

/** 对话置顶 */
const handleTop = async (conversation: ChatConversationVO) => {
  // 更新对话置顶
  conversation.pinned = !conversation.pinned
  await ChatConversationApi.updateChatConversationMy(conversation)
  // 刷新对话
  await getChatConversationList()
}

// ============ 角色仓库 ============

/** 角色仓库抽屉 */
const roleRepositoryOpen = ref<boolean>(false) // 角色仓库是否打开
const handleRoleRepository = async () => {
  roleRepositoryOpen.value = !roleRepositoryOpen.value
}

/** 监听选中的对话 */
const { activeId } = toRefs(props)
watch(activeId, async (newValue) => {
  activeConversationId.value = newValue
})

// 定义 public 方法
defineExpose({ createConversation, createWithAnotherRole, getChatConversationList })

/** 初始化 */
onMounted(async () => {
  // 获取 对话列表
  await getChatConversationList()
  // 默认选中
  if (props.activeId) {
    activeConversationId.value = props.activeId
  } else {
    // 首次默认选中第一个
    if (conversationList.value.length) {
      activeConversationId.value = conversationList.value[0].id
      // 回调 onConversationClick
      await emits('onConversationClick', conversationList.value[0])
    }
  }
})
</script>

<style scoped>
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  outline: none;
  transition: all 0.15s ease-in-out;
}

.action-btn:hover {
  background-color: var(--el-fill-color);
  color: var(--el-color-primary);
}

.action-btn-danger:hover {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
</style>
