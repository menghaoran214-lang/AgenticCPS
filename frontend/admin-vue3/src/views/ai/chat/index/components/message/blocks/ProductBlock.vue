<template>
  <div v-if="items.length" class="product-list">
    <article
      v-for="(item, index) in items"
      :key="productKey(item, index)"
      class="product-card"
      :class="{ 'product-card--best': compare && isBestPrice(item, index) }"
    >
      <div class="product-card__thumb-wrapper">
        <el-image v-if="item.mainPic" :src="item.mainPic" fit="cover" class="product-card__image">
          <template #error><div class="product-card__image-placeholder">暂无图片</div></template>
        </el-image>
        <div v-else class="product-card__image product-card__image-placeholder">暂无图片</div>
        <div v-if="compare && isBestPrice(item, index)" class="product-card__best-badge">
          全网最优
        </div>
      </div>

      <div class="product-card__body">
        <div class="product-card__title-row">
          <el-tag
            :type="platformTagType(item.platformCode)"
            size="small"
            effect="dark"
            class="product-card__platform-tag"
          >
            {{ platformLabel(item) }}
          </el-tag>
          <strong class="product-card__title" :title="item.title">{{ item.title || '未命名商品' }}</strong>
        </div>

        <!-- 价格与返利核心展示区 -->
        <div class="product-card__price-row">
          <div class="product-card__main-price">
            <span class="product-card__price-label">券后</span>
            <span class="product-card__price-val">¥{{ formatNumber(actualPrice(item)) }}</span>
            <del v-if="item.originalPrice" class="product-card__original-price">
              ¥{{ formatNumber(item.originalPrice) }}
            </del>
          </div>

          <div class="product-card__badges">
            <el-tag v-if="item.couponPrice" size="small" type="danger" effect="plain">
              -¥{{ formatNumber(item.couponPrice) }} 券
            </el-tag>
            <el-tag v-if="item.commissionAmount" size="small" type="success" effect="plain">
              返利 ¥{{ formatNumber(item.commissionAmount) }}
              <span v-if="item.commissionRate"> ({{ formatPercent(item.commissionRate) }})</span>
            </el-tag>
            <el-tag v-if="item.netPrice" size="small" type="warning" effect="plain">
              净到手 ¥{{ formatNumber(item.netPrice) }}
            </el-tag>
          </div>
        </div>

        <div class="product-card__minor-row">
          <span v-if="item.shopName" class="product-card__shop">
            <Icon icon="ep:shop" class="mr-2px text-11px" />{{ item.shopName }}
          </span>
          <span v-if="item.monthSales !== undefined">月销 {{ item.monthSales }}</span>
          <span v-if="item.couponConditions">{{ item.couponConditions }}</span>
          <span v-if="compare" class="product-card__source">来源：{{ item.vendorCode || '平台商品库' }}</span>
        </div>

        <div v-if="item.actions?.length || detailUrl(item)" class="product-card__actions">
          <el-button
            v-if="detailUrl(item)"
            size="small"
            type="primary"
            plain
            class="product-card__btn"
            @click="openLink(detailUrl(item)!)"
          >
            <Icon icon="ep:top-right" class="mr-3px" />查看详情
          </el-button>
          <el-button
            v-if="detailUrl(item)"
            size="small"
            plain
            class="product-card__btn"
            @click="copyLink(detailUrl(item)!)"
          >
            <Icon icon="ep:copy-document" class="mr-3px" />复制链接
          </el-button>
          <template v-for="action in item.actions || []" :key="`${action.type}-${action.label}`">
            <el-tag v-if="action.type !== 'OPEN_DETAIL'" size="small" effect="plain">
              {{ action.label }}
            </el-tag>
          </template>
        </div>
      </div>
    </article>
  </div>
  <el-empty v-else description="暂无商品结果" :image-size="64" />
</template>

<script setup lang="ts">
import type { ChatMessageProductItem } from '@/api/ai/chat/message'
import { useClipboard } from '@vueuse/core'

const props = defineProps<{
  items: ChatMessageProductItem[]
  compare?: boolean
}>()

const message = useMessage()
const { copy } = useClipboard({ legacy: true })

const productKey = (item: ChatMessageProductItem, index: number) =>
  `${item.platformCode || 'unknown'}-${item.goodsId || item.goodsSign || index}`

const actualPrice = (item: ChatMessageProductItem) =>
  item.actualPrice ?? item.couponPrice ?? item.netPrice ?? item.originalPrice

const isBestPrice = (item: ChatMessageProductItem, index: number) => {
  if (!props.compare || !props.items.length) return false
  const getVal = (i: ChatMessageProductItem) => Number(i.netPrice ?? i.actualPrice ?? 999999)
  const minVal = Math.min(...props.items.map(getVal))
  return getVal(item) === minVal && props.items.findIndex((i) => getVal(i) === minVal) === index
}

const platformLabel = (item: ChatMessageProductItem) => {
  if (item.platformName) return item.platformName
  const code = (item.platformCode || '').toLowerCase()
  switch (code) {
    case 'taobao':
    case 'tb':
      return '淘宝'
    case 'jd':
      return '京东'
    case 'pdd':
      return '拼多多'
    case 'douyin':
    case 'dy':
      return '抖音'
    case 'vip':
    case 'wph':
      return '唯品会'
    default:
      return item.platformCode || '全网'
  }
}

const platformTagType = (code?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  const c = (code || '').toLowerCase()
  if (c.includes('tb') || c.includes('taobao')) return 'warning'
  if (c.includes('jd')) return 'danger'
  if (c.includes('pdd')) return 'danger'
  if (c.includes('douyin')) return 'info'
  return 'primary'
}

const formatNumber = (value: unknown) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric.toFixed(2).replace(/\.00$/, '') : String(value ?? '-')
}

const formatPercent = (value: unknown) => {
  const text = String(value ?? '-')
  return text.endsWith('%') ? text : `${text}%`
}

const safeUrl = (value: unknown) => {
  if (typeof value !== 'string') return undefined
  return /^https?:\/\//i.test(value) ? value : undefined
}

const detailUrl = (item: ChatMessageProductItem) => safeUrl(item.promotionUrl) || safeUrl(item.itemLink)

const openLink = (url: string) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}

const copyLink = async (url: string) => {
  await copy(url)
  message.success('商品链接已复制到剪贴板')
}
</script>

<style scoped>
.product-list {
  display: grid;
  gap: 12px;
  padding: 4px 14px 14px;
}

.product-card {
  display: flex;
  gap: 14px;
  padding: 12px;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease;
}

.product-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.product-card--best {
  border-color: #f56c6c;
  background: linear-gradient(to right, #fff9f9, var(--el-bg-color));
}

.product-card__thumb-wrapper {
  position: relative;
  flex: 0 0 96px;
  width: 96px;
  height: 96px;
}

.product-card__image {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}

.product-card__image-placeholder {
  display: grid;
  place-items: center;
  background: var(--el-fill-color);
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  width: 100%;
  height: 100%;
  border-radius: 8px;
}

.product-card__best-badge {
  position: absolute;
  top: -4px;
  left: -4px;
  background: linear-gradient(135deg, #f56c6c, #e6a23c);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px 0 8px 0;
  box-shadow: 0 2px 4px rgba(245, 108, 108, 0.4);
}

.product-card__body {
  min-width: 0;
  flex: 1;
}

.product-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.product-card__title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.product-card__price-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
}

.product-card__main-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.product-card__price-label {
  font-size: 11px;
  color: var(--el-color-danger);
}

.product-card__price-val {
  font-size: 18px;
  font-weight: 800;
  color: var(--el-color-danger);
}

.product-card__original-price {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-left: 4px;
}

.product-card__badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.product-card__minor-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.product-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
</style>
