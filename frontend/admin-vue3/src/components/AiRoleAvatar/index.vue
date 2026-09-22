<template>
  <el-avatar :src="avatarSrc" :alt="name || '系统角色头像'" @error="handleImageError">
    {{ name?.slice(0, 1) || 'AI' }}
  </el-avatar>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string | null
    presetCode?: string | null
    name?: string | null
  }>(),
  {
    src: '',
    presetCode: '',
    name: ''
  }
)

const roleAvatarMap: Record<string, string> = {
  'cps-assistant': '/static/img/ai-role/cps-assistant.svg',
  'traffic-product-selector': '/static/img/ai-role/traffic-product-selector.svg',
  'merchant-ad-operator': '/static/img/ai-role/merchant-ad-operator.svg',
  'consumer-rebate-query': '/static/img/ai-role/consumer-rebate-query.svg',
  'wechat-video-commerce': '/static/img/ai-role/wechat-video-commerce.svg',
  'douyin-commerce': '/static/img/ai-role/douyin-commerce.svg'
}

const defaultRoleAvatar = roleAvatarMap['cps-assistant']
const failedSrc = ref<string>()

const presetAvatar = computed(() => {
  const presetCode = props.presetCode?.trim().toLowerCase() || ''
  return roleAvatarMap[presetCode] || defaultRoleAvatar
})
const avatarSrc = computed(() => {
  if (failedSrc.value === props.src) return presetAvatar.value
  return props.src || presetAvatar.value
})

const handleImageError = () => {
  if (props.src && failedSrc.value !== props.src) {
    failedSrc.value = props.src
  }
}

watch(
  () => [props.src, props.presetCode],
  () => {
    failedSrc.value = undefined
  }
)
</script>
