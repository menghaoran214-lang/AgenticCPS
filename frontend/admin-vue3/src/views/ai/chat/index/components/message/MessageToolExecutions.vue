<template>
  <div v-if="executions?.length" class="mb-8px flex flex-col gap-6px" data-testid="tool-executions">
    <div
      v-for="execution in executions"
      :key="execution.executionId"
      class="flex items-center gap-6px rounded-6px bg-[var(--el-bg-color)] px-9px py-6px text-13px"
    >
      <Icon
        :icon="statusIcon(execution.status)"
        :class="statusClass(execution.status)"
        class="shrink-0"
      />
      <span>{{ friendly(execution.message || execution.label) }}</span>
      <span v-if="execution.source || execution.skillCode" class="ml-auto flex shrink-0 items-center gap-4px">
        <el-tag v-if="execution.source" size="small" effect="plain" type="info">
          {{ sourceLabel(execution.source) }}<template v-if="execution.mcpClientName"> · {{ execution.mcpClientName }}</template>
        </el-tag>
        <el-tag v-if="execution.skillCode" size="small" effect="plain" type="success">
          Skill · {{ execution.skillCode }}<template v-if="execution.skillVersion"> v{{ execution.skillVersion }}</template>
        </el-tag>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ToolExecutionVO } from '@/api/ai/chat/message'
import { toFriendlyToolText } from '../../toolActions'

defineProps<{
  executions?: ToolExecutionVO[]
}>()

const friendly = (value?: string) => toFriendlyToolText(value)

const statusIcon = (status: string) => {
  if (status === 'PROCESSED') return 'ep:info-filled'
  if (status === 'WAITING_CONFIRMATION') return 'ep:warning-filled'
  if (status === 'SUCCEEDED' || status === 'SUCCESS') return 'ep:circle-check-filled'
  if (status === 'FAILED' || status === 'FAILURE') return 'ep:circle-close-filled'
  return 'ep:loading'
}

const statusClass = (status: string) => {
  if (status === 'PROCESSED') return 'text-[var(--el-color-info)]'
  if (status === 'WAITING_CONFIRMATION') return 'text-[var(--el-color-warning)]'
  if (status === 'SUCCEEDED' || status === 'SUCCESS') return 'text-[var(--el-color-success)]'
  if (status === 'FAILED' || status === 'FAILURE') return 'text-[var(--el-color-danger)]'
  return 'animate-spin text-[var(--el-color-primary)]'
}

const sourceLabel = (source?: string) => {
  if (source === 'MCP') return 'MCP'
  if (source === 'SELF_MCP') return '自测 MCP'
  if (source === 'LOCAL') return '本地工具'
  return source || '工具'
}
</script>
