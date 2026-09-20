<template>
  <doc-alert title="AI 对话聊天" url="https://doc.iocoder.cn/ai/chat/" />

  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="角色名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入角色名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="角色类别" prop="category">
        <el-input
          v-model="queryParams.category"
          placeholder="请输入角色类别"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="是否公开" prop="publicStatus">
        <el-select
          v-model="queryParams.publicStatus"
          placeholder="请选择是否公开"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getBoolDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING)"
            :key="String(dict.value)"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['ai:chat-role:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="角色名称" align="center" prop="name" />
      <el-table-column label="绑定模型" align="center" prop="modelName" />
      <el-table-column label="角色头像" align="center" prop="avatar">
        <template #default="scope">
          <el-image :src="scope?.row.avatar" class="w-32px h-32px" />
        </template>
      </el-table-column>
      <el-table-column label="角色类别" align="center" prop="category" />
      <el-table-column label="预设编码" align="center" prop="presetCode" />
      <el-table-column label="适用渠道" align="center" prop="channelScope" />
      <el-table-column label="风险等级" align="center" prop="riskLevel" />
      <el-table-column label="角色描述" align="center" prop="description" />
      <el-table-column label="角色设定" align="center" prop="systemMessage" />
      <el-table-column label="知识库" align="center" prop="knowledgeIds">
        <template #default="scope">
          <span v-if="!scope.row.knowledgeIds || scope.row.knowledgeIds.length === 0">-</span>
          <span v-else>引用 {{ scope.row.knowledgeIds.length }} 个</span>
        </template>
      </el-table-column>
      <el-table-column label="工具" align="center" prop="toolIds">
        <template #default="scope">
          <span v-if="!scope.row.toolIds || scope.row.toolIds.length === 0">-</span>
          <span v-else>引用 {{ scope.row.toolIds.length }} 个</span>
        </template>
      </el-table-column>
      <el-table-column label="是否公开" align="center" prop="publicStatus">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.INFRA_BOOLEAN_STRING" :value="scope.row.publicStatus" />
        </template>
      </el-table-column>
      <el-table-column label="Skills" align="center" prop="skillIds">
        <template #default="scope">
          <span v-if="!scope.row.skillIds || scope.row.skillIds.length === 0">-</span>
          <span v-else>绑定 {{ scope.row.skillIds.length }} 个</span>
        </template>
      </el-table-column>
      <el-table-column label="会员可用" align="center" prop="memberEnabled">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.INFRA_BOOLEAN_STRING" :value="scope.row.memberEnabled" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="角色排序" align="center" prop="sort" />
      <el-table-column label="操作" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['ai:chat-role:update']"
          >
            编辑
          </el-button>
          <el-button link @click="openHistory(scope.row)" v-hasPermi="['ai:chat-role:query']"
            >版本历史</el-button
          >
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:chat-role:delete']"
          >
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

  <!-- 表单弹窗：添加/修改 -->
  <ChatRoleForm ref="formRef" @success="getList" />
  <Dialog v-model="historyVisible" :title="`${historyRoleName} · 版本历史`" width="900px">
    <el-table :data="historyVersions" v-loading="historyLoading">
      <el-table-column type="expand"
        ><template #default="{ row }">
          <pre class="whitespace-pre-wrap break-all p-16px">{{
            formatDefinition(row.definitionSnapshot)
          }}</pre>
        </template></el-table-column
      >
      <el-table-column prop="version" label="版本" /><el-table-column
        prop="publishStatus"
        label="发布状态"
      /><el-table-column prop="creator" label="操作人" /><el-table-column
        prop="createTime"
        label="保存时间"
      />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { getBoolDictOptions, DICT_TYPE } from '@/utils/dict'
import { ChatRoleApi, ChatRoleVO, type ChatRoleVersionVO } from '@/api/ai/model/chatRole'
import ChatRoleForm from './ChatRoleForm.vue'

/** AI 聊天角色 列表 */
defineOptions({ name: 'AiChatRole' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const list = ref<ChatRoleVO[]>([]) // 列表的数据
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRoleName = ref('')
const historyVersions = ref<ChatRoleVersionVO[]>([])
const openHistory = async (role: ChatRoleVO) => {
  historyRoleName.value = role.name
  historyVisible.value = true
  historyLoading.value = true
  historyVersions.value = []
  try {
    historyVersions.value = await ChatRoleApi.getVersions(role.id)
  } finally {
    historyLoading.value = false
  }
}
const formatDefinition = (value: string) => {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return '历史版本内容不可解析'
  }
}
const total = ref(0) // 列表的总页数
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  category: undefined,
  publicStatus: true
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await ChatRoleApi.getChatRolePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await ChatRoleApi.deleteChatRole(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
