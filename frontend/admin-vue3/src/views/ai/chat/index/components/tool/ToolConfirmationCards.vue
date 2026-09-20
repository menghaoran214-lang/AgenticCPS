<template>
  <div v-if="pending.length || result" class="mx-20px mt-8px" data-testid="tool-confirmations">
    <el-alert
      v-for="item in pending"
      :key="item.id"
      type="warning"
      :closable="false"
      class="mb-8px"
    >
      <template #title>{{ item.toolLabel }}需要确认</template>
      <p>{{ item.summary || '此操作会产生业务变更，请核对当前请求后再确认。' }}</p>
      <p>有效期至 {{ item.expiresAt }}（上海时间）</p>
      <el-button
        type="warning"
        :disabled="!!submitting || isConfirmationExpired(item.expiresAt)"
        :loading="submitting === item.id"
        @click="confirm(item.id)"
        >确认执行一次</el-button
      >
    </el-alert>
    <el-alert v-if="result" :title="result" type="info" show-icon @close="result = ''" />
  </div>
</template>
<script setup lang="ts">
import { ChatConversationApi, type ToolConfirmationVO } from '@/api/ai/chat/conversation'
import { isConfirmationExpired } from '../../capabilities'
const props = defineProps<{ conversationId?: string; active?: boolean }>()
const emit = defineEmits<{ processed: [conversationId: string] }>()
const pending = ref<ToolConfirmationVO[]>([])
const submitting = ref('')
const result = ref('')
const refresh = async () => {
  const id = props.conversationId
  pending.value = []
  if (!id || !props.active) return
  try {
    const items = await ChatConversationApi.getToolConfirmations(id)
    if (props.conversationId === id) pending.value = items
  } catch {
    /* 通用对话保持可用，用户可在本次请求结束后重试。 */
  }
}
const confirm = async (id: string) => {
  if (submitting.value) return
  const conversationId = props.conversationId
  submitting.value = id
  try {
    const response = await ChatConversationApi.confirmTool(id)
    if (props.conversationId === conversationId) {
      result.value = `已处理：${response}`
      if (conversationId) emit('processed', conversationId)
    }
  } finally {
    submitting.value = ''
    await refresh()
  }
}
watch(() => [props.conversationId, props.active], refresh, { immediate: true })
watch(
  () => props.conversationId,
  () => {
    result.value = ''
  }
)
defineExpose({ refresh })
</script>
