<template>
  <div v-if="usage" class="message-usage" data-testid="message-token-usage">
    <Icon icon="ep:data-line" />
    <span>输入 {{ format(usage.promptTokens) }}</span>
    <span>输出 {{ format(usage.completionTokens) }}</span>
    <span class="font-medium">合计 {{ format(usage.totalTokens) }} tokens</span>
  </div>
</template>

<script setup lang="ts">
import type { ChatTokenUsage } from '@/api/ai/chat/message'

defineProps<{ usage?: ChatTokenUsage }>()
const format = (value?: number | null) => (value == null ? '-' : value.toLocaleString())
</script>

<style scoped>
.message-usage {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
