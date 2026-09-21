<template>
  <div v-if="traces?.length" class="message-debug" data-testid="message-debug-trace">
    <div class="message-debug__header" @click="expanded = !expanded">
      <div class="flex items-center gap-6px">
        <Icon icon="ep:cpu" class="text-[var(--el-color-primary)]" />
        <span>模型调试</span>
        <el-tag size="small" effect="plain">{{ traces.length }} 次调用</el-tag>
      </div>
      <Icon :icon="expanded ? 'ep:arrow-up' : 'ep:arrow-down'" />
    </div>
    <div v-show="expanded" class="message-debug__body">
      <el-collapse accordion>
        <el-collapse-item v-for="trace in traces" :key="trace.traceId" :name="trace.traceId">
          <template #title>
            <div class="flex w-full items-center justify-between pr-8px text-13px">
              <span>第 {{ trace.round || 1 }} 次模型调用</span>
              <span class="text-[var(--el-text-color-secondary)]">
                {{ trace.model || '当前模型' }} · {{ formatTokens(trace.usage?.totalTokens) }} tokens
              </span>
            </div>
          </template>
          <div class="message-debug__meta">
            <el-tag v-if="trace.status" size="small" :type="trace.status === 'FAILED' ? 'danger' : 'info'">
              {{ trace.status === 'FAILED' ? '失败' : '完成' }}
            </el-tag>
            <span v-if="trace.durationMs != null">耗时 {{ trace.durationMs }} ms</span>
            <span v-if="trace.usage">输入 {{ formatTokens(trace.usage.promptTokens) }} · 输出 {{ formatTokens(trace.usage.completionTokens) }}</span>
          </div>
          <div class="message-debug__section">
            <div class="message-debug__label">输入</div>
            <div v-if="trace.input?.length" class="message-debug__messages">
              <div v-for="(message, index) in trace.input" :key="`${trace.traceId}-input-${index}`" class="message-debug__message">
                <span class="message-debug__role">{{ roleLabel(message.role) }}</span>
                <pre>{{ message.content }}</pre>
              </div>
            </div>
            <pre v-else class="message-debug__empty">未返回输入内容</pre>
          </div>
          <div class="message-debug__section">
            <div class="message-debug__label">输出</div>
            <pre>{{ trace.output || '未返回输出内容' }}</pre>
          </div>
          <div v-if="trace.reasoning" class="message-debug__section">
            <div class="message-debug__label">推理</div>
            <pre>{{ trace.reasoning }}</pre>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatDebugTrace } from '@/api/ai/chat/message'

defineProps<{ traces?: ChatDebugTrace[] }>()

const expanded = ref(false)

const formatTokens = (value?: number | null) => (value == null ? '-' : value.toLocaleString())
const roleLabel = (role?: string) => {
  if (role === 'system') return '系统'
  if (role === 'assistant') return '助手'
  if (role === 'tool') return '工具'
  return '用户'
}
</script>

<style scoped>
.message-debug {
  margin: 8px 0 10px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.message-debug__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
}

.message-debug__body {
  padding: 0 10px 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.message-debug__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.message-debug__section {
  margin-top: 8px;
}

.message-debug__label {
  margin-bottom: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
}

.message-debug pre {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  padding: 8px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font: 12px/1.55 var(--el-font-family);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-debug__messages {
  display: grid;
  gap: 6px;
}

.message-debug__message {
  position: relative;
}

.message-debug__message pre {
  padding-top: 22px;
}

.message-debug__role {
  position: absolute;
  z-index: 1;
  top: 5px;
  left: 8px;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 600;
}

.message-debug__empty {
  color: var(--el-text-color-placeholder);
}
</style>
