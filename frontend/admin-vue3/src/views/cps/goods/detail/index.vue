<template>
  <ContentWrap class="detail-shell">
    <div class="detail-toolbar">
      <el-button @click="goBack">
        <Icon icon="ep:arrow-left" class="mr-5px" /> 返回广场
      </el-button>
      <div class="toolbar-actions">
        <el-button :disabled="!goods?.itemLink" @click="openOriginalLink(goods?.itemLink)">
          <Icon icon="ep:link" class="mr-5px" /> 原始链接
        </el-button>
        <el-button type="primary" :disabled="!goods" @click="openLinkDialog">
          <Icon icon="ep:connection" class="mr-5px" /> 推广赚{{ formatMoney(goods?.commissionAmount) }}
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !goods" description="未找到商品详情" />

    <div v-else v-loading="loading" class="detail-layout">
      <section class="overview-panel">
        <div class="media-column">
          <el-image v-if="goods?.mainPic" class="main-image" :src="goods.mainPic" fit="cover" />
          <div v-else class="main-image image-placeholder">{{ platformLabel(goods?.platformCode) }}</div>

          <div class="shop-strip">
            <span>{{ goods?.shopName || goods?.brandName || '未知店铺' }}</span>
            <el-tag size="small" effect="plain">{{ platformLabel(goods?.platformCode) }}</el-tag>
          </div>
        </div>

        <div class="info-column">
          <div class="title-row">
            <el-tag type="warning" effect="dark">{{ vendorLabel(effectiveVendorCode) }}</el-tag>
            <h1>{{ goods?.title || '商品详情' }}</h1>
          </div>

          <div v-if="goods?.sellingPoint" class="selling-point">{{ goods.sellingPoint }}</div>

          <div class="tag-row">
            <el-tag v-if="goods?.activityTag" type="success" effect="plain">{{ goods.activityTag }}</el-tag>
            <el-tag v-if="goods?.rankTag" type="danger" effect="plain">{{ goods.rankTag }}</el-tag>
            <el-tag v-if="goods?.categoryName" effect="plain">{{ goods.categoryName }}</el-tag>
            <el-tag v-if="goods?.source" effect="plain">{{ goods.source }}</el-tag>
          </div>

          <div class="price-matrix">
            <div>
              <span>券后价</span>
              <b class="danger">{{ formatMoney(goods?.actualPrice) }}</b>
            </div>
            <div>
              <span>单价</span>
              <b>{{ formatMoney(goods?.originalPrice) }}</b>
            </div>
            <div>
              <span>佣金率</span>
              <b class="danger">{{ formatPercent(goods?.commissionRate) }}</b>
            </div>
            <div>
              <span>预估佣金</span>
              <b class="warning">{{ formatMoney(goods?.commissionAmount) }}</b>
            </div>
            <div>
              <span>优惠券</span>
              <b class="coupon">{{ formatMoney(goods?.couponPrice) }}</b>
            </div>
            <div>
              <span>月销量</span>
              <b>{{ formatCount(goods?.monthSales) }}</b>
            </div>
          </div>

          <div class="coupon-line">
            <Icon icon="ep:ticket" />
            <span>{{ couponText }}</span>
          </div>

          <div class="trend-panel">
            <div class="section-title">热卖趋势</div>
            <div class="trend-grid">
              <div v-for="item in trendItems" :key="item.label" class="trend-item">
                <span>{{ item.label }}</span>
                <b>{{ item.value }}</b>
                <div class="trend-bar"><i :style="{ width: item.percent }"></i></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 价格走势与跟踪面板 -->
      <section class="price-trend-panel">
        <div class="panel-header">
          <div class="header-left">
            <div class="section-title">价格走势与跟踪</div>
            <span class="header-tip">开启后系统将定期抓取最新券后价，沉淀走势数据</span>
          </div>
          <div class="header-actions">
            <el-switch
              v-model="priceTrackSwitch"
              :active-value="1"
              :inactive-value="0"
              :loading="priceTrackLoading"
              active-text="价格跟踪开启"
              inactive-text="未开启"
              @change="handleTogglePriceTrack"
            />
            <el-button
              size="small"
              type="primary"
              plain
              :loading="pricePulling"
              :disabled="priceTrackSwitch !== 1"
              @click="handleManualPullPrice"
            >
              <Icon icon="ep:refresh" class="mr-4px" /> 立即抓取最新价格
            </el-button>
          </div>
        </div>

        <!-- 价格指标看板 -->
        <div v-if="priceTrend" class="trend-metrics-grid">
          <div class="metric-card">
            <div class="metric-label">当前券后价</div>
            <div class="metric-value current">{{ formatMoney(priceTrend.currentPrice) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">历史最低券后价</div>
            <div class="metric-value low">{{ formatMoney(priceTrend.minPrice) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">历史最高券后价</div>
            <div class="metric-value high">{{ formatMoney(priceTrend.maxPrice) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">历史平均券后价</div>
            <div class="metric-value avg">{{ formatMoney(priceTrend.avgPrice) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">价格波动</div>
            <div class="metric-value change" :class="priceChangeClass">
              {{ priceChangeText }}
            </div>
          </div>
        </div>

        <!-- 折线图与历史列表 -->
        <div v-if="priceTrend && priceTrend.historyList && priceTrend.historyList.length > 0" class="trend-body">
          <div class="chart-wrapper">
            <Echart :options="trendChartOptions" height="300px" />
          </div>

          <div class="history-table-wrapper">
            <div class="history-header">
              <span class="history-title">历史价格变动明细（共 {{ priceTrend.historyList.length }} 个价格区间）</span>
              <span v-if="priceTrend.lastTrackTime" class="last-time">
                最近抓取：{{ formatDateTime(priceTrend.lastTrackTime) }}
              </span>
            </div>
            <el-table :data="priceTrend.historyList" size="small" border stripe max-height="220">
              <el-table-column label="价格维持时间段" min-width="200">
                <template #default="{ row }">
                  <span>{{ formatDateTime(row.snapshotTime) }}</span>
                  <span v-if="row.endTime && row.endTime !== row.snapshotTime" class="text-gray-400"> ~ {{ formatDateTime(row.endTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="券后价" width="110">
                <template #default="{ row }">
                  <span class="text-red-500 font-bold">{{ formatMoney(row.actualPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="原价" width="100">
                <template #default="{ row }">
                  {{ formatMoney(row.originalPrice) }}
                </template>
              </el-table-column>
              <el-table-column label="优惠券" width="100">
                <template #default="{ row }">
                  {{ formatMoney(row.couponPrice) }}
                </template>
              </el-table-column>
              <el-table-column label="佣金率" width="100">
                <template #default="{ row }">
                  {{ formatPercent(row.commissionRate) }}
                </template>
              </el-table-column>
              <el-table-column label="月销量" prop="monthSales" width="90" />
            </el-table>
          </div>
        </div>

        <!-- 未开启或暂无走势数据 -->
        <div v-else class="trend-empty">
          <el-empty
            :description="priceTrackSwitch === 1 ? '已开启价格跟踪，正在等待首次定时拉取或点击上方立即抓取' : '尚未开启价格趋势跟踪。开启后系统定时任务将定期抓取商品价格，沉淀走势图表。'"
            :image-size="80"
          >
            <el-button v-if="priceTrackSwitch !== 1" type="primary" size="small" @click="handleTogglePriceTrack(1)">
              立即开启价格趋势跟踪
            </el-button>
          </el-empty>
        </div>
      </section>

      <section class="promotion-panel">
        <div class="section-title">推广模板</div>
        <el-tabs v-model="activeTemplate">
          <el-tab-pane label="社群推广" name="community">
            <div class="template-box">{{ communityCopy }}</div>
          </el-tab-pane>
          <el-tab-pane label="朋友圈推广" name="moments">
            <div class="template-box">{{ momentsCopy }}</div>
          </el-tab-pane>
          <el-tab-pane label="长图推广" name="poster">
            <div class="template-box">{{ posterCopy }}</div>
          </el-tab-pane>
        </el-tabs>
        <div class="template-actions">
          <el-button @click="copyTemplate">
            <Icon icon="ep:document-copy" class="mr-5px" /> 复制文案
          </el-button>
          <el-button type="primary" :disabled="!goods" @click="openLinkDialog">
            <Icon icon="ep:connection" class="mr-5px" /> 生成推广内容
          </el-button>
        </div>
      </section>

      <section class="content-grid">
        <div class="material-panel">
          <div class="section-title">图片素材</div>
          <div class="material-grid">
            <button
              v-for="item in imageMaterials"
              :key="item.label"
              type="button"
              class="material-item"
              @click="previewImage(item.url)"
            >
              <el-image v-if="item.url" :src="item.url" fit="cover" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <div class="similar-panel">
          <div class="section-title">相似推荐</div>
          <el-empty v-if="!similarLoading && similarGoods.length === 0" description="暂无相似商品" />
          <div v-else v-loading="similarLoading" class="similar-list">
            <button
              v-for="item in similarGoods"
              :key="`${item.platformCode}:${item.goodsId}`"
              type="button"
              class="similar-item"
              @click="switchGoods(item)"
            >
              <el-image v-if="item.mainPic" :src="item.mainPic" fit="cover" />
              <div>
                <b>{{ item.title }}</b>
                <span>{{ formatMoney(item.actualPrice) }} · 佣金 {{ formatPercent(item.commissionRate) }}</span>
              </div>
            </button>
          </div>
        </div>
      </section>
    </div>
  </ContentWrap>

  <el-dialog v-model="linkVisible" title="生成推广内容" width="720px">
    <el-form ref="linkFormRef" :model="linkForm" :rules="linkFormRules" label-width="100px">
      <el-form-item label="商品">
        <div class="min-w-0">
          <div class="font-600">{{ goods?.title || '-' }}</div>
          <div class="mt-4px text-12px text-gray-500">
            {{ platformLabel(goods?.platformCode) }} · {{ goods?.goodsId }} · {{ vendorLabel(linkForm.vendorCode) }}
          </div>
        </div>
      </el-form-item>
      <el-form-item label="会员" prop="memberId">
        <el-select
          v-model="linkForm.memberId"
          placeholder="搜索手机号、昵称或姓名"
          clearable
          filterable
          remote
          reserve-keyword
          class="w-full"
          :remote-method="searchMemberOptions"
          :loading="memberLoading"
          @visible-change="handleMemberDropdownVisible"
        >
          <el-option
            v-for="item in memberOptions"
            :key="item.id"
            :label="formatMemberLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="推广位">
        <el-select
          v-model="linkForm.adzoneId"
          placeholder="默认推广位"
          clearable
          filterable
          class="w-full"
          :loading="adzoneLoading"
        >
          <el-option
            v-for="item in adzoneOptions"
            :key="item.adzoneId"
            :label="formatAdzoneLabel(item)"
            :value="item.adzoneId"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="linkResult"
      class="mb-12px"
      :type="linkResult.linkStatus === 'SUCCESS' ? 'success' : 'warning'"
      :title="linkResult.linkMessage || '已生成'"
      show-icon
      :closable="false"
    />
    <div v-if="linkResult" class="link-result-list">
      <div v-for="row in linkRows" :key="row.label" class="link-result-row">
        <span>{{ row.label }}</span>
        <el-input :model-value="row.value" readonly>
          <template #append>
            <el-button :disabled="!row.value" @click="handleCopy(row.value)">复制</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <template #footer>
      <el-button @click="linkVisible = false">关闭</el-button>
      <el-button type="primary" :loading="linkLoading" @click="handleGenerateLink">生成</el-button>
    </template>
  </el-dialog>

  <el-image-viewer
    v-if="previewVisible && previewUrl"
    :url-list="[previewUrl]"
    @close="previewVisible = false"
  />
</template>

<script setup lang="ts">
import { useClipboard } from '@vueuse/core'
import type { FormInstance, FormRules } from 'element-plus'
import { CpsAdzoneApi, type CpsAdzoneVO } from '@/api/cps/adzone'
import { VENDOR_CODE_OPTIONS } from '@/api/cps/apiVendor'
import {
  CpsGoodsSquareApi,
  type CpsGoodsSquareGoodsVO,
  type CpsGoodsSquareLinkReqVO,
  type CpsGoodsSquareLinkRespVO
} from '@/api/cps/goodsSquare'
import { CpsPlatformApi, type CpsPlatformVO } from '@/api/cps/platform'
import { getUserPage, type UserVO } from '@/api/member/user/index'
import { Echart } from '@/components/Echart'
import type { EChartsOption } from 'echarts'
import {
  CpsSelectionThemeApi,
  type CpsGoodsPriceTrendVO,
  type CpsGoodsPricePointVO
} from '@/api/cps/selectionTheme'

type DateTimeValue = string | number | Date | [number, number, number, number?, number?, number?]

defineOptions({ name: 'CpsGoodsSquareDetail' })

const message = useMessage()
const route = useRoute()
const router = useRouter()
const { copy } = useClipboard()
const goodsSquareDetailCacheKey = 'cps:goods-square:detail'

const loading = ref(false)
const similarLoading = ref(false)
const adzoneLoading = ref(false)
const memberLoading = ref(false)
const linkVisible = ref(false)
const linkLoading = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const activeTemplate = ref('community')
const goods = ref<CpsGoodsSquareGoodsVO>()
const similarGoods = ref<CpsGoodsSquareGoodsVO[]>([])
const platformOptions = ref<CpsPlatformVO[]>([])
const adzoneOptions = ref<CpsAdzoneVO[]>([])
const memberOptions = ref<UserVO[]>([])
const linkResult = ref<CpsGoodsSquareLinkRespVO>()
const priceTrend = ref<CpsGoodsPriceTrendVO | null>(null)
const priceTrackSwitch = ref(0)
const priceTrackLoading = ref(false)
const pricePulling = ref(false)
const routeSelectionItemId = computed(() => {
  const raw = routeText(route.query.selectionItemId)
  return raw ? Number(raw) : undefined
})

const padDatePart = (value: number) => String(value).padStart(2, '0')

const formatDateTime = (value?: DateTimeValue) => {
  if (value == null || value === '') return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0] = value
    if (!year || !month || !day) return '-'
    return `${year}-${padDatePart(month)}-${padDatePart(day)} ${padDatePart(hour)}:${padDatePart(minute)}`
  }
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return '-'
    return `${value.getFullYear()}-${padDatePart(value.getMonth() + 1)}-${padDatePart(value.getDate())} ${padDatePart(value.getHours())}:${padDatePart(value.getMinutes())}`
  }
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '-' : formatDateTime(date)
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

const priceChangeText = computed(() => {
  const change = Number(priceTrend.value?.priceChange || 0)
  if (change < 0) return `降 ¥${Math.abs(change).toFixed(2)}`
  if (change > 0) return `涨 ¥${change.toFixed(2)}`
  return '持平'
})

const priceChangeClass = computed(() => {
  const change = Number(priceTrend.value?.priceChange || 0)
  if (change < 0) return 'text-green-600'
  if (change > 0) return 'text-red-500'
  return 'text-gray-500'
})

const loadPriceTrend = async () => {
  const goodsId = routeGoodsId.value || goods.value?.goodsId
  if (!goodsId) return
  try {
    const data = await CpsSelectionThemeApi.getPriceTrend({
      selectionItemId: routeSelectionItemId.value || priceTrend.value?.selectionItemId,
      platformCode: goods.value?.platformCode || routePlatformCode.value,
      goodsId
    })
    priceTrend.value = data
    priceTrackSwitch.value = data?.trackStatus || 0
  } catch {
    // ignore
  }
}

const handleTogglePriceTrack = async (val?: unknown) => {
  const targetStatus = typeof val === 'number' ? val : priceTrackSwitch.value
  priceTrackLoading.value = true
  try {
    await CpsSelectionThemeApi.updateItemPriceTrackStatus({
      id: routeSelectionItemId.value || priceTrend.value?.selectionItemId,
      platformCode: goods.value?.platformCode || routePlatformCode.value,
      goodsId: goods.value?.goodsId || routeGoodsId.value,
      vendorCode: effectiveVendorCode.value,
      title: goods.value?.title,
      mainPic: goods.value?.mainPic,
      actualPrice: goods.value?.actualPrice,
      originalPrice: goods.value?.originalPrice,
      couponPrice: goods.value?.couponPrice,
      priceTrackStatus: targetStatus
    })
    priceTrackSwitch.value = targetStatus
    message.success(targetStatus === 1 ? '已开启价格趋势跟踪' : '已关闭价格趋势跟踪')
    await loadPriceTrend()
  } catch {
    priceTrackSwitch.value = targetStatus === 1 ? 0 : 1
  } finally {
    priceTrackLoading.value = false
  }
}

const handleManualPullPrice = async () => {
  const itemId = routeSelectionItemId.value || priceTrend.value?.selectionItemId
  if (!itemId) {
    message.warning('请先开启价格跟踪')
    return
  }
  pricePulling.value = true
  try {
    await CpsSelectionThemeApi.pullItemPrice(itemId)
    message.success('已抓取最新价格并记录快照')
    await loadPriceTrend()
    if (priceTrend.value?.currentPrice !== undefined && goods.value) {
      goods.value.actualPrice = priceTrend.value.currentPrice
    }
  } finally {
    pricePulling.value = false
  }
}

const trendChartOptions = computed<EChartsOption>(() => {
  if (!priceTrend.value || !priceTrend.value.historyList || priceTrend.value.historyList.length === 0) {
    return {}
  }
  const chartPoints: Array<{ time: string; actualPrice: number; originalPrice: number; point: CpsGoodsPricePointVO }> = []
  priceTrend.value.historyList.forEach((h) => {
    chartPoints.push({
      time: formatDateTime(h.snapshotTime),
      actualPrice: h.actualPrice,
      originalPrice: h.originalPrice || h.actualPrice,
      point: h
    })
    if (h.endTime && h.endTime !== h.snapshotTime) {
      chartPoints.push({
        time: formatDateTime(h.endTime),
        actualPrice: h.actualPrice,
        originalPrice: h.originalPrice || h.actualPrice,
        point: h
      })
    }
  })
  const dates = chartPoints.map((p) => p.time)
  const actualPrices = chartPoints.map((p) => p.actualPrice)
  const originalPrices = chartPoints.map((p) => p.originalPrice)

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        const date = params[0].name
        const item = chartPoints[params[0].dataIndex]?.point
        let header = `<div style="font-weight: 600; margin-bottom: 4px;">${date}</div>`
        if (item?.endTime && item.endTime !== item.snapshotTime) {
          header = `<div style="font-weight: 600; margin-bottom: 4px;">价格维持区间: ${formatDateTime(item.snapshotTime)} ~ ${formatDateTime(item.endTime)}</div>`
        }
        let html = header
        params.forEach((p: any) => {
          html += `<div>${p.marker} ${p.seriesName}: ¥${p.value}</div>`
        })
        if (item?.couponPrice) {
          html += `<div style="color: #f56c6c;">优惠券: ¥${item.couponPrice}</div>`
        }
        if (item?.monthSales) {
          html += `<div style="color: #909399;">月销: ${item.monthSales}</div>`
        }
        return html
      }
    },
    legend: {
      data: ['券后价', '原价']
    },
    grid: {
      left: 16,
      right: 24,
      top: 36,
      bottom: 20,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '¥{value}'
      }
    },
    series: [
      {
        name: '券后价',
        type: 'line',
        smooth: true,
        data: actualPrices,
        itemStyle: { color: '#ff4d4f' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(255, 77, 79, 0.35)' },
              { offset: 1, color: 'rgba(255, 77, 79, 0.02)' }
            ]
          }
        }
      },
      {
        name: '原价',
        type: 'line',
        smooth: true,
        data: originalPrices,
        itemStyle: { color: '#909399' },
        lineStyle: { type: 'dashed' }
      }
    ]
  }
})
const linkFormRef = ref<FormInstance>()
const linkForm = reactive<CpsGoodsSquareLinkReqVO>({
  platformCode: '',
  goodsId: '',
  goodsSign: undefined,
  memberId: undefined as unknown as number,
  adzoneId: undefined,
  vendorCode: undefined,
  title: '',
  originalContent: ''
})
const linkFormRules = reactive<FormRules>({
  memberId: [{ required: true, message: '请选择会员', trigger: 'change' }]
})

const routeText = (value: unknown) => {
  if (Array.isArray(value)) {
    return value[0] as string | undefined
  }
  return value as string | undefined
}

const routeGoodsId = computed(() => routeText(route.query.goodsId))
const routePlatformCode = computed(() => routeText(route.query.platformCode) || 'taobao')
const routeVendorCode = computed(() => routeText(route.query.vendorCode))
const routeGoodsSign = computed(() => routeText(route.query.goodsSign))
const effectiveVendorCode = computed(() => goods.value?.vendorCode || routeVendorCode.value)
const couponText = computed(() => {
  if (!goods.value?.couponPrice) return '当前商品暂无可展示优惠券'
  const time = goods.value.couponEndTime ? `，有效期至 ${goods.value.couponEndTime}` : ''
  return `优惠券 ${formatMoney(goods.value.couponPrice)}${time}`
})
const trendItems = computed(() => {
  const sales = Number(goods.value?.monthSales || 0)
  const commission = Number(goods.value?.commissionRate || 0)
  const coupon = Number(goods.value?.couponPrice || 0)
  return [
    { label: '热销量级', value: formatCount(sales), percent: `${Math.min(100, Math.max(8, sales / 1000))}%` },
    { label: '佣金吸引力', value: formatPercent(commission), percent: `${Math.min(100, Math.max(8, commission * 4))}%` },
    { label: '券额力度', value: formatMoney(coupon), percent: `${Math.min(100, Math.max(8, coupon * 4))}%` }
  ]
})
const imageMaterials = computed(() => {
  const imageUrl = goods.value?.mainPic || ''
  return [
    { label: '商品主图', url: imageUrl },
    { label: '社群素材', url: imageUrl },
    { label: '朋友圈素材', url: imageUrl },
    { label: '长图封面', url: imageUrl }
  ].filter((item) => item.url)
})
const communityCopy = computed(() => {
  if (!goods.value) return ''
  return `【${goods.value.title}】券后 ${formatMoney(goods.value.actualPrice)}，券 ${formatMoney(goods.value.couponPrice)}，预估佣金 ${formatMoney(goods.value.commissionAmount)}。${goods.value.sellingPoint || '适合社群快速推荐。'}`
})
const momentsCopy = computed(() => {
  if (!goods.value) return ''
  return `${goods.value.title}\n券后价 ${formatMoney(goods.value.actualPrice)}｜月销 ${formatCount(goods.value.monthSales)}｜${couponText.value}`
})
const posterCopy = computed(() => {
  if (!goods.value) return ''
  return `${goods.value.title}\n到手价 ${formatMoney(goods.value.actualPrice)}\n佣金率 ${formatPercent(goods.value.commissionRate)}`
})
const activeCopy = computed(() => {
  const copyMap: Record<string, string> = {
    community: communityCopy.value,
    moments: momentsCopy.value,
    poster: posterCopy.value
  }
  return copyMap[activeTemplate.value] || communityCopy.value
})
const linkRows = computed(() => [
  { label: '短链', value: linkResult.value?.shortUrl || '' },
  { label: '长链', value: linkResult.value?.longUrl || '' },
  { label: '口令', value: linkResult.value?.tpwd || '' },
  { label: '移动端链接', value: linkResult.value?.mobileUrl || '' },
  { label: '推广文案', value: linkResult.value?.promotionContent || '' }
])

const loadGoodsFromCache = () => {
  const raw = sessionStorage.getItem(goodsSquareDetailCacheKey)
  if (!raw) return false
  try {
    const cached = JSON.parse(raw) as CpsGoodsSquareGoodsVO
    if (cached.goodsId && cached.goodsId === routeGoodsId.value) {
      goods.value = cached
      return true
    }
  } catch {
    sessionStorage.removeItem(goodsSquareDetailCacheKey)
  }
  return false
}

const loadGoods = async () => {
  if (loadGoodsFromCache()) return
  if (!routeGoodsId.value) return
  loading.value = true
  try {
    const data = await CpsGoodsSquareApi.searchGoods({
      keyword: routeGoodsId.value,
      searchField: 'goods_id',
      platformCode: routePlatformCode.value,
      vendorCode: routeVendorCode.value,
      pageNo: 1,
      pageSize: 1,
      goodsSign: routeGoodsSign.value
    })
    goods.value = data.list?.[0]
  } finally {
    loading.value = false
  }
}

const loadSimilarGoods = async () => {
  if (!goods.value) return
  similarLoading.value = true
  try {
    const keyword = goods.value.categoryName || goods.value.title?.slice(0, 12) || '今日精选'
    const data = await CpsGoodsSquareApi.searchGoods({
      keyword,
      platformCode: goods.value.platformCode,
      vendorCode: effectiveVendorCode.value,
      pageNo: 1,
      pageSize: 6,
      sortType: 0
    })
    similarGoods.value = (data.list || []).filter((item) => item.goodsId !== goods.value?.goodsId).slice(0, 5)
  } finally {
    similarLoading.value = false
  }
}

const switchGoods = async (item: CpsGoodsSquareGoodsVO) => {
  sessionStorage.setItem(goodsSquareDetailCacheKey, JSON.stringify(item))
  goods.value = item
  linkResult.value = undefined
  await router.replace({
    name: 'CpsGoodsSquareDetail',
    query: {
      platformCode: item.platformCode,
      vendorCode: item.vendorCode,
      goodsId: item.goodsId,
      goodsSign: item.goodsSign
    }
  })
  await loadSimilarGoods()
  await loadPriceTrend()
}

const openLinkDialog = async () => {
  if (!goods.value) return
  linkResult.value = undefined
  Object.assign(linkForm, {
    platformCode: goods.value.platformCode,
    goodsId: goods.value.goodsId,
    goodsSign: goods.value.goodsSign,
    memberId: undefined,
    adzoneId: undefined,
    vendorCode: effectiveVendorCode.value,
    title: goods.value.title,
    originalContent: goods.value.itemLink || goods.value.goodsId
  })
  linkVisible.value = true
  await loadAdzoneOptions(goods.value.platformCode)
  nextTick(() => linkFormRef.value?.clearValidate())
}

const handleGenerateLink = async () => {
  await linkFormRef.value?.validate()
  linkLoading.value = true
  try {
    linkResult.value = await CpsGoodsSquareApi.generateLink(linkForm)
    if (linkResult.value.linkStatus === 'SUCCESS') {
      message.success('转链成功')
    } else {
      message.warning(linkResult.value.linkMessage || '转链失败')
    }
  } finally {
    linkLoading.value = false
  }
}

const loadPlatformOptions = async () => {
  try {
    platformOptions.value = await CpsPlatformApi.getEnabledPlatformList()
  } catch {
    platformOptions.value = []
  }
}

const loadAdzoneOptions = async (platformCode: string) => {
  adzoneLoading.value = true
  try {
    adzoneOptions.value = await CpsAdzoneApi.getAdzoneListByPlatform(platformCode)
  } finally {
    adzoneLoading.value = false
  }
}

const searchMemberOptions = async (keyword: string) => {
  memberLoading.value = true
  try {
    const data = await getUserPage({ pageNo: 1, pageSize: 20, keyword })
    memberOptions.value = data?.list || []
  } finally {
    memberLoading.value = false
  }
}

const handleMemberDropdownVisible = (visible: boolean) => {
  if (visible && memberOptions.value.length === 0) {
    searchMemberOptions('')
  }
}

const copyTemplate = async () => {
  if (!activeCopy.value) return
  await copy(activeCopy.value)
  message.success('复制成功')
}

const handleCopy = async (text?: string) => {
  if (!text) return
  await copy(text)
  message.success('复制成功')
}

const previewImage = (url?: string) => {
  if (!url) return
  previewUrl.value = url
  previewVisible.value = true
}

const openOriginalLink = (url?: string) => {
  if (url) window.open(url, '_blank')
}

const goBack = () => {
  router.push({ name: 'CpsGoodsSquare' })
}

const platformLabel = (platformCode?: string) => {
  const option = platformOptions.value.find((item) => item.platformCode === platformCode)
  if (option?.platformName) return option.platformName
  const map: Record<string, string> = {
    taobao: '淘宝',
    jd: '京东',
    pdd: '拼多多',
    douyin: '抖音',
    vip: '唯品会',
    meituan: '美团'
  }
  return platformCode ? map[platformCode] || platformCode : '-'
}

const vendorLabel = (vendorCode?: string) => {
  if (!vendorCode) return '默认'
  return VENDOR_CODE_OPTIONS.find((item) => item.value === vendorCode)?.label || vendorCode
}

const formatAdzoneLabel = (item: CpsAdzoneVO) => {
  return `${item.adzoneName || item.adzoneId}${item.isDefault === 1 ? ' · 默认' : ''}`
}

const formatMemberLabel = (item: UserVO) => {
  return `${item.nickname || item.name || item.mobile || item.id}（ID: ${item.id}）`
}

const formatMoney = (value?: number) => {
  if (value === undefined || value === null) return '-'
  return `¥${Number(value).toFixed(2)}`
}

const formatPercent = (value?: number) => {
  if (value === undefined || value === null) return '-'
  return `${Number(value).toFixed(2)}%`
}

const formatCount = (value?: number) => {
  if (!value) return '0'
  return value >= 10000 ? `${(value / 10000).toFixed(1)}万` : `${value}`
}

onMounted(async () => {
  await loadPlatformOptions()
  await loadGoods()
  await loadSimilarGoods()
  await loadPriceTrend()
})
</script>

<style scoped>
.detail-shell {
  padding: 22px 26px 30px;
  background: linear-gradient(135deg, #effaff 0%, #f3f7ff 52%, #eef3ff 100%);
}

.detail-toolbar,
.detail-layout {
  max-width: 1480px;
  margin-right: auto;
  margin-left: auto;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.detail-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-panel,
.promotion-panel,
.material-panel,
.similar-panel {
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.overview-panel {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 22px;
  padding: 18px;
}

.main-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: #f5f7fa;
  border-radius: 8px;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 22px;
  font-weight: 700;
}

.shop-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.info-column {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.title-row h1 {
  margin: 0;
  color: #1f2a44;
  font-size: 22px;
  font-weight: 800;
  line-height: 31px;
}

.selling-point {
  margin-top: 10px;
  color: #64748b;
  line-height: 22px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.price-matrix {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.price-matrix div {
  min-width: 0;
  padding: 12px 14px;
  background: #f7f9fc;
  border-radius: 8px;
}

.price-matrix span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.price-matrix b {
  display: block;
  margin-top: 4px;
  color: #1f2a44;
  font-size: 22px;
  line-height: 28px;
}

.price-matrix .danger {
  color: #ff4d39;
}

.price-matrix .warning,
.price-matrix .coupon {
  color: #ff8a00;
}

.coupon-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  color: #667085;
}

.trend-panel {
  margin-top: 18px;
  padding: 14px;
  background: #f7f9ff;
  border-radius: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f2a44;
  font-size: 16px;
  font-weight: 800;
}

.section-title::before {
  width: 4px;
  height: 18px;
  background: #1d6dff;
  border-radius: 4px;
  content: '';
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 12px;
}

.trend-item {
  min-width: 0;
}

.trend-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-item b {
  display: block;
  margin-top: 3px;
  color: #1f2a44;
  font-size: 17px;
}

.trend-bar {
  height: 6px;
  margin-top: 7px;
  overflow: hidden;
  background: #e7ecf5;
  border-radius: 999px;
}

.trend-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #1d6dff 0%, #ff8a00 100%);
  border-radius: inherit;
}


.price-trend-panel {
  padding: 18px 20px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.price-trend-panel .panel-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.price-trend-panel .header-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.price-trend-panel .header-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.price-trend-panel .header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trend-metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.metric-card {
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  text-align: center;
}

.metric-card .metric-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.metric-card .metric-value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
}

.metric-card .metric-value.current {
  color: #ff4d39;
}

.metric-card .metric-value.low {
  color: #10b981;
}

.metric-card .metric-value.high,
.metric-card .metric-value.avg {
  color: #1f2a44;
}

.trend-body {
  margin-top: 16px;
}

.chart-wrapper {
  padding: 10px 6px;
  background: #fafcff;
  border: 1px solid #edf2f7;
  border-radius: 8px;
}

.history-table-wrapper {
  margin-top: 14px;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.history-title {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.last-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trend-empty {
  padding: 24px 0 16px;
}

.promotion-panel {
  padding: 16px 18px;
}

.template-box {
  min-height: 96px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  color: #334155;
  line-height: 24px;
  white-space: pre-wrap;
}

.template-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 16px;
}

.material-panel,
.similar-panel {
  padding: 16px 18px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.material-item,
.similar-item,
.detail-entry {
  cursor: pointer;
}

.material-item {
  padding: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  text-align: left;
}

.material-item :deep(.el-image) {
  width: 100%;
  aspect-ratio: 1 / 1;
}

.material-item span {
  display: block;
  padding: 8px 10px;
  color: #475569;
  font-size: 12px;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.similar-item {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 8px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  text-align: left;
}

.similar-item :deep(.el-image) {
  width: 72px;
  height: 72px;
  border-radius: 6px;
}

.similar-item b {
  display: -webkit-box;
  overflow: hidden;
  color: #1f2a44;
  font-size: 13px;
  line-height: 19px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.similar-item span {
  display: block;
  margin-top: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.link-result-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.link-result-row {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.link-result-row span {
  color: var(--el-text-color-secondary);
}

@media (width <= 960px) {
  .overview-panel,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .price-matrix,
  .trend-grid,
  .trend-metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (width <= 640px) {
  .detail-shell {
    padding: 14px;
  }

  .detail-toolbar,
  .toolbar-actions,
  .template-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .price-matrix,
  .trend-grid,
  .trend-metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
