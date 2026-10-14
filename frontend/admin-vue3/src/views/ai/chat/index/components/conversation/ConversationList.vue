<!--  AI 对话  -->
<template>
  <el-aside
    width="260px"
    class="h-100% relative flex flex-col justify-between px-2.5 pt-2.5 pb-0 overflow-hidden"
  >
    <!-- 左顶部：对话 -->
    <div class="h-100%">
      <el-button class="w-1/1 py-4.5" type="primary" @click="createConversation">
        <Icon icon="ep:plus" class="mr-5px" />
        新建对话
      </el-button>
      <!-- 会员选择下拉框：默认当前登录用户，管理员可选择其他会员来运行本会话 -->
      <div class="mt-2.5">
        <div class="mb-1 flex items-center justify-between px-0.5">
          <span class="text-12px font-500 text-[var(--el-text-color-secondary)]">运行会员</span>
          <el-tag size="small" :type="isAdmin ? 'success' : 'info'" effect="plain">
            {{ isAdmin ? '管理员可切换' : '当前用户' }}
          </el-tag>
        </div>
        <el-select
          v-model="selectedMemberId"
          filterable
          :remote="isAdmin"
          reserve-keyword
          :disabled="!isAdmin"
          :remote-method="searchMembers"
          :loading="memberLoading"
          class="w-full"
          placeholder="选择运行会员"
          data-testid="cps-member-select"
          @change="handleMemberChange"
        >
          <el-option
            v-for="item in memberOptions"
            :key="item.id"
            :label="formatMemberOptionLabel(item)"
            :value="item.id"
          />
        </el-select>
      </div>

      <!-- 左顶部：搜索对话 -->
      <el-input
        v-model="searchName"
        size="large"
        class="mt-5"
        placeholder="搜索历史记录"
        @keyup="searchConversation"
      >
        <template #prefix>
          <Icon icon="ep:search" />
        </template>
      </el-input>

      <!-- 左中间：对话列表 -->
      <div class="overflow-auto h-full">
        <!-- 情况一：加载中 -->
        <el-empty v-if="loading" description="." :v-loading="loading" />
        <!-- 情况二：按照 group 分组，展示聊天会话 list 列表 -->
        <div v-for="conversationKey in Object.keys(conversationMap)" :key="conversationKey">
          <div class="mt-1.25 pt-2.5" v-if="conversationMap[conversationKey].length">
            <el-text class="mx-1" size="small" tag="b">
              {{ conversationKey }}
            </el-text>
          </div>
          <div
            class="mt-1.25"
            v-for="conversation in conversationMap[conversationKey]"
            :key="conversation.id"
            @click="handleConversationClick(conversation.id)"
            @mouseover="hoverConversationId = conversation.id"
            @mouseout="hoverConversationId = ''"
          >
            <div
              class="flex flex-row justify-between flex-1 px-1.25 cursor-pointer rounded-1.25 items-center leading-7.5"
              :style="
                conversation.id === activeConversationId
                  ? 'background-color: var(--el-color-primary-light-9); border: 1px solid var(--el-color-primary-light-7);'
                  : ''
              "
            >
              <div class="flex flex-row items-center">
                <AiRoleAvatar
                  class="w-6.25 h-6.25 rounded-1.25 flex flex-row justify-center"
                  :src="conversation.roleAvatar"
                  :preset-code="conversation.rolePresetCode"
                  name="AI"
                />
                <span
                  class="py-0.5 px-2.5"
                  style="
                    max-width: 220px;
                    font-size: 14px;
                    font-weight: 400;
                    color: var(--el-text-color-regular);
                    overflow: hidden;
                    white-space: nowrap;
                    text-overflow: ellipsis;
                  "
                >
                  {{ conversation.title }}
                </span>
              </div>
              <div
                class="right-0.5 flex flex-row justify-center"
                style="color: var(--el-text-color-regular)"
                v-show="hoverConversationId === conversation.id"
              >
                <el-button class="m-0" link @click.stop="handleTop(conversation)">
                  <el-icon title="置顶" v-if="!conversation.pinned"><Top /></el-icon>
                  <el-icon title="置顶" v-if="conversation.pinned"><Bottom /></el-icon>
                </el-button>
                <el-button class="m-0" link @click.stop="updateConversationTitle(conversation)">
                  <el-icon title="编辑">
                    <Icon icon="ep:edit" />
                  </el-icon>
                </el-button>
                <el-button class="m-0" link @click.stop="deleteChatConversation(conversation)">
                  <el-icon title="删除对话">
                    <Icon icon="ep:delete" />
                  </el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部占位  -->
        <div class="h-160px w-100%"></div>
      </div>
    </div>

    <!-- 左底部：工具栏 -->
    <div
      class="absolute bottom-0 left-0 right-0 px-5 leading-8.75 flex justify-between items-center"
      style="
        background-color: var(--el-fill-color-extra-light);
        box-shadow: 0 0 1px 1px var(--el-border-color-lighter);
        color: var(--el-text-color);
      "
    >
      <div
        class="flex items-center p-0 m-0 cursor-pointer"
        style="color: var(--el-text-color-regular)"
        @click="handleRoleRepository"
      >
        <Icon icon="ep:user" />
        <el-text class="ml-1.25" size="small">角色仓库</el-text>
      </div>
      <div
        class="flex items-center p-0 m-0 cursor-pointer"
        style="color: var(--el-text-color-regular)"
        @click="handleClearConversation"
      >
        <Icon icon="ep:delete" />
        <el-text class="ml-1.25" size="small">清空未置顶对话</el-text>
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
import { getUser, getUserPage, type UserVO } from '@/api/member/user'
import { useUserStore } from '@/store/modules/user'
import { checkRole, checkPermi } from '@/utils/permission'

const message = useMessage() // 消息弹窗
const userStore = useUserStore()
const currentUser = computed(() => userStore.getUser)
const isAdmin = computed(() => checkRole(['super_admin', 'admin']) || checkPermi(['ai:chat:mcp-test']))

// 运行会员选择状态
const selectedMemberId = ref<number>()
const memberLoading = ref(false)
const memberOptions = ref<Array<{ id: number; nickname?: string; name?: string; mobile?: string }>>([])

// 构建当前登录用户默认选项
const buildCurrentUserOption = () => {
  const user = currentUser.value
  return {
    id: user.id || 1,
    nickname: user.nickname || '当前用户',
    mobile: ''
  }
}

// 格式化会员选项文案
const formatMemberOptionLabel = (item: { id: number; nickname?: string; name?: string; mobile?: string }) => {
  const isSelf = item.id === (currentUser.value.id || 1)
  const name = item.nickname || item.name || `会员${item.id}`
  if (isSelf) {
    return `${name}（当前登录用户 · ID: ${item.id}）`
  }
  return `${name}（${item.mobile ? item.mobile + ' · ' : ''}ID: ${item.id}）`
}

// 搜索会员
const searchMembers = async (keyword = '') => {
  if (!isAdmin.value) return
  memberLoading.value = true
  try {
    const query = keyword.trim()
    const page: any = await getUserPage({
      pageNo: 1,
      pageSize: 20,
      mobile: /^\d+$/.test(query) ? query : undefined,
      nickname: query && !/^\d+$/.test(query) ? query : undefined
    })
    const list = page?.list || []
    const selfOption = buildCurrentUserOption()
    const nextOptions = [selfOption, ...list.filter((m: any) => m.id !== selfOption.id)]

    if (selectedMemberId.value != null && !nextOptions.some((m) => m.id === selectedMemberId.value)) {
      try {
        const extra = await getUser(selectedMemberId.value)
        if (extra) {
          nextOptions.splice(1, 0, extra)
        }
      } catch {
        // ignore
      }
    }
    memberOptions.value = nextOptions
  } catch (e) {
    console.error('加载会员列表失败', e)
  } finally {
    memberLoading.value = false
  }
}

// 确保特定会员在选项中
const ensureMemberInOptions = async (memberId: number) => {
  if (memberOptions.value.some((m) => m.id === memberId)) return
  try {
    const member = await getUser(memberId)
    if (member && !memberOptions.value.some((m) => m.id === member.id)) {
      memberOptions.value.unshift(member)
    }
  } catch {
    // ignore
  }
}

// 会员切换回调
const handleMemberChange = (newMemberId: number) => {
  emits('onMemberChange', newMemberId)
}

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
  'onConversationDelete',
  'onMemberChange'
])

// 监听活跃会话绑定的会员变更
watch(
  () => props.activeMemberId,
  async (newMemberId) => {
    if (newMemberId != null) {
      selectedMemberId.value = newMemberId
      await ensureMemberInOptions(newMemberId)
    } else {
      selectedMemberId.value = currentUser.value.id || 1
    }
  },
  { immediate: true }
)

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

/** 新建对话 */
const createConversation = async () => {
  await chatRolePickerRef.value?.open(undefined, selectedMemberId.value)
}
const createWithAnotherRole = async () => {
  await chatRolePickerRef.value?.open(props.activeId || undefined, selectedMemberId.value)
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
  const selfOption = buildCurrentUserOption()
  memberOptions.value = [selfOption]
  selectedMemberId.value = props.activeMemberId || selfOption.id
  if (isAdmin.value) {
    searchMembers()
  }
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
