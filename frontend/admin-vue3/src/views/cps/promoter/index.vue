<template>
  <ContentWrap>
    <!-- 搜索表单 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="会员编号" prop="memberId">
        <el-input-number v-model="queryParams.memberId" placeholder="会员ID" :min="1" :controls="false" class="!w-160px" />
      </el-form-item>
      <el-form-item label="邀请码" prop="inviteCode">
        <el-input v-model="queryParams.inviteCode" placeholder="专属邀请码" clearable class="!w-160px" />
      </el-form-item>
      <el-form-item label="推广层级" prop="promoterLevel">
        <el-select v-model="queryParams.promoterLevel" placeholder="请选择层级" clearable class="!w-150px">
          <el-option label="普通会员" :value="0" />
          <el-option label="初级团长" :value="1" />
          <el-option label="中级合伙人" :value="2" />
          <el-option label="高级合伙人" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="资格状态" prop="promoterStatus">
        <el-select v-model="queryParams.promoterStatus" placeholder="请选择状态" clearable class="!w-120px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="会员ID" align="center" prop="memberId" width="90" />
      <el-table-column label="会员信息" align="center" min-width="140">
        <template #default="scope">
          <div>{{ scope.row.nickname || '-' }}</div>
          <div class="text-xs text-gray-400">{{ scope.row.mobile || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="专属邀请码" align="center" prop="inviteCode" width="120">
        <template #default="scope">
          <el-tag type="warning" class="font-mono font-bold">{{ scope.row.inviteCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上级推荐人" align="center" min-width="140">
        <template #default="scope">
          <div v-if="scope.row.parentMemberId">
            <span>{{ scope.row.parentNickname || '会员' + scope.row.parentMemberId }}</span>
            <span class="text-xs text-gray-400 ml-1">(ID:{{ scope.row.parentMemberId }})</span>
          </div>
          <span v-else class="text-gray-400">无上级</span>
        </template>
      </el-table-column>
      <el-table-column label="推广层级" align="center" prop="promoterLevel" width="110">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.promoterLevel)">
            {{ levelName(scope.row.promoterLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="直推/间推人数" align="center" width="130">
        <template #default="scope">
          <span>{{ scope.row.teamCountL1 || 0 }} / {{ scope.row.teamCountL2 || 0 }} 人</span>
        </template>
      </el-table-column>
      <el-table-column label="团队出单" align="center" prop="teamOrderCount" width="90">
        <template #default="scope">{{ scope.row.teamOrderCount || 0 }} 单</template>
      </el-table-column>
      <el-table-column label="累计推广提成" align="center" width="120">
        <template #default="scope">
          <span class="text-orange-500 font-bold">¥{{ (scope.row.totalSplitCent / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="自购返利" align="center" width="100">
        <template #default="scope">
          <span>¥{{ (scope.row.totalRebateCent / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="资格状态" align="center" prop="promoterStatus" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.promoterStatus === 1 ? 'success' : 'danger'">
            {{ scope.row.promoterStatus === 1 ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定时间" align="center" prop="bindTime" width="160">
        <template #default="scope">
          <span>{{ scope.row.bindTime ? parseTime(scope.row.bindTime) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openRebindDialog(scope.row)" v-hasPermi="['cps:promoter:update']">
            换绑上级
          </el-button>
          <el-button link type="warning" @click="openStatusDialog(scope.row)" v-hasPermi="['cps:promoter:update']">
            设置层级
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 人工换绑弹窗 -->
  <el-dialog title="人工换绑/解绑上级推荐人" v-model="rebindVisible" width="500px" append-to-body>
    <el-form ref="rebindFormRef" :model="rebindForm" label-width="120px">
      <el-form-item label="当前会员">
        <span>{{ currentMember?.nickname || '-' }} (ID: {{ rebindForm.memberId }})</span>
      </el-form-item>
      <el-form-item label="当前上级">
        <span v-if="currentMember?.parentMemberId">{{ currentMember.parentNickname || '-' }} (ID: {{ currentMember.parentMemberId }})</span>
        <span v-else class="text-gray-400">无上级</span>
      </el-form-item>
      <el-form-item label="新上级会员ID">
        <el-input-number v-model="rebindForm.newParentMemberId" placeholder="输入会员ID，留空表示解绑" :min="1" :controls="false" class="w-full" />
        <span class="text-gray-400 text-xs mt-1">留空时将直接解除该会员的上级关系。系统将自动进行循环绑定与防成环检测。</span>
      </el-form-item>
      <el-form-item label="换绑原因说明" required>
        <el-input v-model="rebindForm.reason" type="textarea" :rows="3" placeholder="请输入人工换绑审核原因（将记录安全审计日志）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rebindVisible = false">取 消</el-button>
      <el-button type="primary" @click="submitRebind">确 认 换 绑</el-button>
    </template>
  </el-dialog>

  <!-- 等级与状态弹窗 -->
  <el-dialog title="修改推广层级与资格状态" v-model="statusVisible" width="460px" append-to-body>
    <el-form ref="statusFormRef" :model="statusForm" label-width="100px">
      <el-form-item label="会员">
        <span>{{ currentMember?.nickname || '-' }} (ID: {{ statusForm.memberId }})</span>
      </el-form-item>
      <el-form-item label="推广层级">
        <el-select v-model="statusForm.promoterLevel" class="w-full">
          <el-option label="普通会员 (Level 0)" :value="0" />
          <el-option label="初级团长 (Level 1)" :value="1" />
          <el-option label="中级合伙人 (Level 2)" :value="2" />
          <el-option label="高级合伙人 (Level 3)" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="分销状态">
        <el-radio-group v-model="statusForm.promoterStatus">
          <el-radio :label="1">正常 (可获得提成)</el-radio>
          <el-radio :label="0">停用 (提成沉淀至平台)</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="statusVisible = false">取 消</el-button>
      <el-button type="primary" @click="submitStatus">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { parseTime } from '@/utils/formatTime'
import {
  getPromoterPage,
  rebindPromoter,
  updatePromoterStatus,
  CpsAdminPromoterVO,
  CpsAdminPromoterPageReqVO
} from '@/api/cps/promoter'

const loading = ref(false)
const list = ref<CpsAdminPromoterVO[]>([])
const total = ref(0)
const queryParams = reactive<CpsAdminPromoterPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  memberId: undefined,
  inviteCode: undefined,
  promoterLevel: undefined,
  promoterStatus: undefined
})

const currentMember = ref<CpsAdminPromoterVO | null>(null)

// 换绑弹窗
const rebindVisible = ref(false)
const rebindForm = reactive({
  memberId: 0,
  newParentMemberId: undefined as number | undefined,
  reason: ''
})

// 等级状态弹窗
const statusVisible = ref(false)
const statusForm = reactive({
  memberId: 0,
  promoterLevel: 0,
  promoterStatus: 1
})

function levelName(level: number) {
  const map: Record<number, string> = {
    0: '普通会员',
    1: '初级团长',
    2: '中级合伙人',
    3: '高级合伙人'
  }
  return map[level] || '普通会员'
}

function levelTagType(level: number): any {
  if (level === 1) return 'primary'
  if (level === 2) return 'warning'
  if (level === 3) return 'danger'
  return 'info'
}

async function getList() {
  loading.value = true
  try {
    const data = await getPromoterPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNo = 1
  getList()
}

function resetQuery() {
  queryParams.memberId = undefined
  queryParams.inviteCode = undefined
  queryParams.promoterLevel = undefined
  queryParams.promoterStatus = undefined
  handleQuery()
}

function openRebindDialog(row: CpsAdminPromoterVO) {
  currentMember.value = row
  rebindForm.memberId = row.memberId
  rebindForm.newParentMemberId = undefined
  rebindForm.reason = ''
  rebindVisible.value = true
}

async function submitRebind() {
  if (!rebindForm.reason || !rebindForm.reason.trim()) {
    ElMessage.error('请输入换绑原因说明')
    return
  }
  await rebindPromoter({
    memberId: rebindForm.memberId,
    newParentMemberId: rebindForm.newParentMemberId || null,
    reason: rebindForm.reason.trim()
  })
  ElMessage.success('换绑操作成功')
  rebindVisible.value = false
  getList()
}

function openStatusDialog(row: CpsAdminPromoterVO) {
  currentMember.value = row
  statusForm.memberId = row.memberId
  statusForm.promoterLevel = row.promoterLevel
  statusForm.promoterStatus = row.promoterStatus
  statusVisible.value = true
}

async function submitStatus() {
  await updatePromoterStatus({
    memberId: statusForm.memberId,
    promoterLevel: statusForm.promoterLevel,
    promoterStatus: statusForm.promoterStatus
  })
  ElMessage.success('层级与状态更新成功')
  statusVisible.value = false
  getList()
}

onMounted(() => {
  getList()
})
</script>
