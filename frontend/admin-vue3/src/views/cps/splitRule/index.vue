<template>
  <ContentWrap>
    <!-- 搜索表单 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="规则名称" prop="ruleName">
        <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable class="!w-200px" />
      </el-form-item>
      <el-form-item label="电商平台" prop="platformCode">
        <el-select v-model="queryParams.platformCode" placeholder="请选择平台" clearable class="!w-150px">
          <el-option label="淘宝 (taobao)" value="taobao" />
          <el-option label="京东 (jd)" value="jd" />
          <el-option label="拼多多 (pdd)" value="pdd" />
          <el-option label="唯品会 (vip)" value="vip" />
          <el-option label="美团 (meituan)" value="meituan" />
          <el-option label="抖音 (douyin)" value="douyin" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['cps:split-rule:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增规则
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="70" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="160" show-overflow-tooltip />
      <el-table-column label="适用平台" align="center" prop="platformCode" width="110">
        <template #default="scope">
          <el-tag v-if="scope.row.platformCode" type="info">{{ scope.row.platformCode }}</el-tag>
          <el-tag v-else type="success">全平台通用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="会员等级" align="center" prop="memberLevelId" width="90">
        <template #default="scope">
          <span>{{ scope.row.memberLevelId != null ? 'VIP ' + scope.row.memberLevelId : '通用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="平台留存" align="center" prop="platformShareRate" width="100">
        <template #default="scope">
          <span class="font-bold text-gray-600">{{ formatPercent(scope.row.platformShareRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="自购比例" align="center" prop="selfRebateRate" width="100">
        <template #default="scope">
          <span class="text-green-600 font-semibold">{{ formatPercent(scope.row.selfRebateRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="一级提成" align="center" prop="parentRebateRate" width="100">
        <template #default="scope">
          <span class="text-orange-500 font-semibold">{{ formatPercent(scope.row.parentRebateRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="二级提成" align="center" prop="grandparentRebateRate" width="100">
        <template #default="scope">
          <span class="text-blue-500 font-semibold">{{ formatPercent(scope.row.grandparentRebateRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="冻结天数" align="center" prop="freezeDays" width="90">
        <template #default="scope">{{ scope.row.freezeDays }} 天</template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="80" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openForm('update', scope.row.id)" v-hasPermi="['cps:split-rule:update']">
            编辑
          </el-button>
          <el-button link type="danger" @click="handleDelete(scope.row.id)" v-hasPermi="['cps:split-rule:delete']">
            删除
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

  <!-- 表单弹窗 -->
  <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" append-to-body>
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
      <el-form-item label="规则名称" prop="ruleName">
        <el-input v-model="formData.ruleName" placeholder="如：淘宝默认80-50-30-20规则" />
      </el-form-item>
      <el-form-item label="适用平台" prop="platformCode">
        <el-select v-model="formData.platformCode" placeholder="留空表示全平台通用" clearable class="w-full">
          <el-option label="淘宝 (taobao)" value="taobao" />
          <el-option label="京东 (jd)" value="jd" />
          <el-option label="拼多多 (pdd)" value="pdd" />
          <el-option label="唯品会 (vip)" value="vip" />
          <el-option label="美团 (meituan)" value="meituan" />
          <el-option label="抖音 (douyin)" value="douyin" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用等级" prop="memberLevelId">
        <el-input-number v-model="formData.memberLevelId" placeholder="留空表示全等级通用" :min="1" class="w-full" />
      </el-form-item>

      <el-divider content-position="left">净佣金池瓜分比例配置（防超分校验）</el-divider>

      <el-form-item label="平台留存比例" prop="platformShareRate">
        <el-input-number v-model="formData.platformShareRate" :min="0" :max="1" :step="0.05" :precision="4" class="w-full" />
        <span class="text-gray-400 text-xs mt-1">例如 0.2000 表示扣除 20% 平台技术服务费，剩余 80% 组成净佣金池</span>
      </el-form-item>
      <el-form-item label="自购返利比例" prop="selfRebateRate">
        <el-input-number v-model="formData.selfRebateRate" :min="0" :max="1" :step="0.05" :precision="4" class="w-full" />
        <span class="text-gray-400 text-xs mt-1">占净佣金池的比例（例如 0.5000 表示瓜分净池的 50%）</span>
      </el-form-item>
      <el-form-item label="一级提成比例" prop="parentRebateRate">
        <el-input-number v-model="formData.parentRebateRate" :min="0" :max="1" :step="0.05" :precision="4" class="w-full" />
        <span class="text-gray-400 text-xs mt-1">一级直接推荐人提成比例</span>
      </el-form-item>
      <el-form-item label="二级提成比例" prop="grandparentRebateRate">
        <el-input-number v-model="formData.grandparentRebateRate" :min="0" :max="1" :step="0.05" :precision="4" class="w-full" />
        <span class="text-gray-400 text-xs mt-1">二级间接推荐人提成比例</span>
      </el-form-item>

      <el-alert
        :type="isOverSplit ? 'error' : 'success'"
        :closable="false"
        class="mb-4"
      >
        <template #title>
          <span>净佣金池瓜分合计: {{ currentSumPercent }}% </span>
          <span v-if="isOverSplit" class="font-bold ml-2">（警告：比例之和超过 100%，系统将拒绝提交！）</span>
          <span v-else class="text-gray-500 ml-2">（合规安全：缺位部分自动沉淀至平台留存）</span>
        </template>
      </el-alert>

      <el-form-item label="冻结天数" prop="freezeDays">
        <el-input-number v-model="formData.freezeDays" :min="0" :max="90" class="w-full" />
      </el-form-item>
      <el-form-item label="新客自动锁粉" prop="autoLockInviterOnFirstOrder">
        <el-radio-group v-model="formData.autoLockInviterOnFirstOrder">
          <el-radio :label="1">是（首单出单无上级时自动绑定转链人）</el-radio>
          <el-radio :label="0">否</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="归属冲突策略" prop="attributionPriority">
        <el-radio-group v-model="formData.attributionPriority">
          <el-radio :label="1">既有关系优先</el-radio>
          <el-radio :label="2">转链出单优先</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="规则状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :label="1">启用</el-radio>
          <el-radio :label="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-input-number v-model="formData.priority" :min="0" :max="999" class="w-full" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取 消</el-button>
      <el-button type="primary" :disabled="isOverSplit" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSplitRulePage,
  getSplitRule,
  createSplitRule,
  updateSplitRule,
  deleteSplitRule,
  CpsSplitRuleVO,
  CpsSplitRulePageReqVO
} from '@/api/cps/splitRule'

const loading = ref(false)
const list = ref<CpsSplitRuleVO[]>([])
const total = ref(0)
const queryParams = reactive<CpsSplitRulePageReqVO>({
  pageNo: 1,
  pageSize: 10,
  ruleName: undefined,
  platformCode: undefined,
  status: undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const formData = reactive<CpsSplitRuleVO>({
  id: undefined,
  ruleName: '',
  platformCode: undefined,
  memberLevelId: undefined,
  platformShareRate: 0.2000,
  selfRebateRate: 0.5000,
  parentRebateRate: 0.3000,
  grandparentRebateRate: 0.2000,
  partnerSpreadRate: 0,
  attributionPriority: 1,
  autoLockInviterOnFirstOrder: 1,
  freezeDays: 15,
  status: 1,
  priority: 10
})

const formRules = {
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  platformShareRate: [{ required: true, message: '平台留存比例不能为空', trigger: 'blur' }],
  selfRebateRate: [{ required: true, message: '自购返利比例不能为空', trigger: 'blur' }],
  parentRebateRate: [{ required: true, message: '一级提成比例不能为空', trigger: 'blur' }],
  grandparentRebateRate: [{ required: true, message: '二级提成比例不能为空', trigger: 'blur' }]
}

const currentSumPercent = computed(() => {
  const sum = (Number(formData.selfRebateRate || 0) + Number(formData.parentRebateRate || 0) + Number(formData.grandparentRebateRate || 0)) * 100
  return sum.toFixed(2)
})

const isOverSplit = computed(() => {
  return Number(currentSumPercent.value) > 100.001
})

function formatPercent(val?: number) {
  if (val == null) return '0.00%'
  return (Number(val) * 100).toFixed(2) + '%'
}

async function getList() {
  loading.value = true
  try {
    const data = await getSplitRulePage(queryParams)
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
  queryParams.ruleName = undefined
  queryParams.platformCode = undefined
  queryParams.status = undefined
  handleQuery()
}

async function openForm(type: 'create' | 'update', id?: number) {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增分账规则' : '修改分账规则'
  formType.value = type
  if (type === 'create') {
    Object.assign(formData, {
      id: undefined,
      ruleName: '',
      platformCode: undefined,
      memberLevelId: undefined,
      platformShareRate: 0.2000,
      selfRebateRate: 0.5000,
      parentRebateRate: 0.3000,
      grandparentRebateRate: 0.2000,
      partnerSpreadRate: 0,
      attributionPriority: 1,
      autoLockInviterOnFirstOrder: 1,
      freezeDays: 15,
      status: 1,
      priority: 10
    })
  } else if (id) {
    const data = await getSplitRule(id)
    Object.assign(formData, data)
  }
}

async function submitForm() {
  if (isOverSplit.value) {
    ElMessage.error('自购、一级、二级提成之和不能超过 100%！')
    return
  }
  await formRef.value.validate()
  if (formType.value === 'create') {
    await createSplitRule(formData)
    ElMessage.success('创建成功')
  } else {
    await updateSplitRule(formData)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  getList()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定要删除该分账规则吗？', '提示', { type: 'warning' })
  await deleteSplitRule(id)
  ElMessage.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
