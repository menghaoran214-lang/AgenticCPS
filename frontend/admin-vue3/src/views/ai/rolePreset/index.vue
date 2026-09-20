<template>
  <ContentWrap>
    <div class="mb-16px"
      ><div class="text-18px font-bold">角色中心</div
      ><div class="mt-4px text-13px text-[var(--el-text-color-secondary)]"
        >选择角色会创建独立会话，并固定该角色的能力快照</div
      ></div
    >
    <el-collapse class="mb-16px" v-if="roles.length">
      <el-collapse-item title="角色能力矩阵" name="matrix">
        <el-table :data="roles">
          <el-table-column prop="name" label="角色" />
          <el-table-column prop="channelScope" label="渠道" />
          <el-table-column label="Skills"
            ><template #default="{ row }">{{
              row.skillIds?.length || 0
            }}</template></el-table-column
          >
          <el-table-column label="本地工具"
            ><template #default="{ row }">{{ row.toolIds?.length || 0 }}</template></el-table-column
          >
          <el-table-column prop="mcpPolicyId" label="MCP 策略" />
          <el-table-column prop="riskLevel" label="风险上限" />
          <el-table-column label="写操作"
            ><template #default="{ row }">{{
              row.allowMutation ? '需要确认' : '禁止'
            }}</template></el-table-column
          >
          <el-table-column prop="version" label="版本" />
        </el-table>
      </el-collapse-item>
    </el-collapse>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="roles.length === 0" description="暂无已发布角色预设" />
    <div v-else class="grid grid-cols-1 gap-16px md:grid-cols-2 xl:grid-cols-3">
      <el-card v-for="role in roles" :key="role.id || role.presetCode" shadow="hover">
        <div class="flex items-start gap-12px"
          ><el-avatar :src="role.avatar">{{ role.name?.slice(0, 1) }}</el-avatar
          ><div class="min-w-0 flex-1"
            ><div class="truncate text-16px font-bold">{{ role.name }}</div
            ><div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">{{
              role.presetCode || role.category || '通用角色'
            }}</div></div
          ><el-tag size="small" effect="plain">{{ role.riskLevel || 'READ_ONLY' }}</el-tag></div
        >
        <div class="mt-12px h-42px text-13px text-[var(--el-text-color-regular)]">{{
          role.description || '为当前业务场景配置的 AI 能力角色'
        }}</div>
        <div class="mt-12px flex flex-wrap gap-4px"
          ><el-tag v-if="role.channelScope" size="small">{{ role.channelScope }}</el-tag
          ><el-tag v-if="role.audience" size="small" type="info">{{ role.audience }}</el-tag
          ><el-tag v-if="role.version" size="small" type="success">v{{ role.version }}</el-tag></div
        >
        <el-button class="mt-16px w-full" type="primary" @click="startConversation(role)"
          >使用此角色新建会话</el-button
        >
      </el-card>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import { RolePresetApi, type RolePresetVO } from '@/api/ai/rolePreset'
import { ChatConversationApi } from '@/api/ai/chat/conversation'
defineOptions({ name: 'AiRolePreset' })
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const roles = ref<RolePresetVO[]>([])
const loadRoles = async () => {
  loading.value = true
  try {
    const data = await RolePresetApi.getList({ publishStatus: 'PUBLISHED' })
    roles.value = data
  } finally {
    loading.value = false
  }
}
const startConversation = async (role: RolePresetVO) => {
  if (!role.id) return message.warning('角色缺少编号，暂时无法创建会话')
  const conversationId = await ChatConversationApi.createChatConversationMy({ roleId: role.id })
  await router.push({ name: 'AiChat', query: { conversationId } })
}
onMounted(loadRoles)
</script>
