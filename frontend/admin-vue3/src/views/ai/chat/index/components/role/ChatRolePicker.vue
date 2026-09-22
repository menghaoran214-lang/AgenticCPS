<template>
  <Dialog v-model="visible" title="选择聊天角色" width="800px">
    <p class="mb-16px text-[var(--el-text-color-secondary)]">新会话会固定所选角色的模型、MCP 与 Skills 能力版本。</p>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-empty v-else-if="roles.length === 0" description="暂无可用的聊天角色" />
    <div class="grid grid-cols-1 gap-12px md:grid-cols-2">
      <el-card v-for="role in roles" :key="role.id" shadow="hover" data-testid="chat-role-card">
        <div class="flex items-center gap-8px">
          <AiRoleAvatar :src="role.avatar" :preset-code="role.presetCode" :name="role.name" />
          <strong>{{ role.name }}</strong>
        </div>
        <p>{{ role.description }}</p>
        <div class="flex flex-wrap gap-4px">
          <el-tag>{{ role.channelScope || '通用' }}</el-tag>
          <el-tag type="info">{{ role.audience || '后台运营' }}</el-tag>
          <el-tag type="warning">{{ role.riskLevel || 'READ_ONLY' }}</el-tag>
        </div>
        <el-button
          class="mt-12px"
          type="primary"
          :loading="creating === role.id"
          :disabled="creating !== undefined"
          @click="select(role)"
        >使用此角色 · v{{ role.version || 1 }}</el-button>
      </el-card>
    </div>
  </Dialog>
</template>
<script setup lang="ts">
import { ChatRoleApi, type ChatRoleVO } from '@/api/ai/model/chatRole'
import { ChatConversationApi } from '@/api/ai/chat/conversation'
import AiRoleAvatar from '@/components/AiRoleAvatar/index.vue'
const emit = defineEmits<{ success: [id: string] }>()
const visible = ref(false)
const loading = ref(false)
const creating = ref<number>()
const sourceId = ref<string>()
const roles = ref<ChatRoleVO[]>([])
const open = async (sourceConversationId?: string) => {
  sourceId.value = sourceConversationId
  visible.value = true
  loading.value = true
  try {
    roles.value = await ChatRoleApi.getPublishedList()
  } finally {
    loading.value = false
  }
}
const select = async (role: ChatRoleVO) => {
  if (!role.id || creating.value !== undefined) return
  creating.value = role.id
  try {
    const id = await ChatConversationApi.createChatConversationMy({
      roleId: role.id,
      sourceConversationId: sourceId.value
    })
    visible.value = false
    emit('success', id)
  } finally {
    creating.value = undefined
  }
}
defineExpose({ open })
</script>
