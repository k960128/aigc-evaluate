<script setup lang="ts">
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, reactive, ref, shallowRef, useTemplateRef } from 'vue'

type RiskLevel = 1 | 2
type MatchType = 1 | 2
type SyncStatus = 0 | 1

interface FeatureGroup {
  id: string
  name: string
  description: string
}

interface RiskFeatureKeyword {
  id: number
  groupId: string
  keyword: string
  risk_details_id: number
  riskLevel: RiskLevel
  matchType: MatchType
  syncStatus: SyncStatus
  updateTime: string
}

const featureGroups: FeatureGroup[] = [
  {
    id: 'vulnerability_exploit',
    name: '漏洞利用特征',
    description: '覆盖 CVE、RCE、未授权访问、反序列化等高危漏洞利用词条。',
  },
  {
    id: 'injection_attack',
    name: '注入攻击特征',
    description: '覆盖 SQL 注入、XSS、命令注入、模板注入等输入型攻击特征。',
  },
  {
    id: 'violent_extremism',
    name: '暴恐极端特征',
    description: '覆盖暴恐极端、危险组织、攻击煽动与规避审查表达。',
  },
  {
    id: 'data_leakage',
    name: '数据泄露特征',
    description: '覆盖凭据、密钥、隐私数据、内部系统信息泄露风险。',
  },
  {
    id: 'gray_industry_tools',
    name: '黑灰产工具特征',
    description: '覆盖扫描、爆破、撞库、WebShell、代理池等黑灰产工具链。',
  },
]

const columns = [
  { title: 'ID', key: 'id', width: 110, fixed: 'left' as const },
  { title: '特征词', key: 'keyword', width: 240 },
  { title: '所属小类 ID', key: 'risk_details_id', width: 140 },
  { title: '风险等级', key: 'riskLevel', width: 140, align: 'center' as const },
  { title: '匹配模式', key: 'matchType', width: 120, align: 'center' as const },
  { title: '同步状态', key: 'syncStatus', width: 150, align: 'center' as const },
  { title: '操作', key: 'action', width: 150, align: 'center' as const, fixed: 'right' as const },
]

const pagination = {
  pageSize: 10,
  showTotal: (total: number) => `共 ${total} 条`,
  showSizeChanger: true,
}

const keywords = ref<RiskFeatureKeyword[]>([
  { id: 10001, groupId: 'vulnerability_exploit', keyword: 'CVE-2021-44228', risk_details_id: 4101, riskLevel: 1, matchType: 1, syncStatus: 0, updateTime: '2026-06-01 10:20' },
  { id: 10002, groupId: 'vulnerability_exploit', keyword: 'Log4Shell JNDI ldap', risk_details_id: 4101, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 10:24' },
  { id: 10003, groupId: 'vulnerability_exploit', keyword: 'Redis 未授权访问', risk_details_id: 4102, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 10:31' },
  { id: 10004, groupId: 'vulnerability_exploit', keyword: 'Shiro rememberMe 反序列化', risk_details_id: 4103, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 10:36' },
  { id: 10005, groupId: 'vulnerability_exploit', keyword: 'Spring4Shell RCE', risk_details_id: 4104, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 10:42' },
  { id: 10006, groupId: 'injection_attack', keyword: '\' or 1=1 --', risk_details_id: 4201, riskLevel: 1, matchType: 1, syncStatus: 0, updateTime: '2026-06-01 11:10' },
  { id: 10007, groupId: 'injection_attack', keyword: 'UNION SELECT password', risk_details_id: 4201, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 11:14' },
  { id: 10008, groupId: 'injection_attack', keyword: '<script>alert(1)<\/script>', risk_details_id: 4202, riskLevel: 2, matchType: 1, syncStatus: 0, updateTime: '2026-06-01 11:18' },
  { id: 10009, groupId: 'injection_attack', keyword: 'Runtime.getRuntime().exec', risk_details_id: 4203, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 11:26' },
  { id: 10010, groupId: 'injection_attack', keyword: '{{7*7}} 模板注入', risk_details_id: 4204, riskLevel: 2, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 11:34' },
  { id: 10011, groupId: 'violent_extremism', keyword: '暴恐袭击策划', risk_details_id: 4301, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 13:10' },
  { id: 10012, groupId: 'violent_extremism', keyword: '极端组织招募话术', risk_details_id: 4302, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 13:18' },
  { id: 10013, groupId: 'violent_extremism', keyword: '爆炸物制作步骤', risk_details_id: 4303, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 13:22' },
  { id: 10014, groupId: 'violent_extremism', keyword: '规避平台审查暗语', risk_details_id: 4304, riskLevel: 2, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 13:30' },
  { id: 10015, groupId: 'data_leakage', keyword: 'AKIA[0-9A-Z]{16}', risk_details_id: 4401, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 14:08' },
  { id: 10016, groupId: 'data_leakage', keyword: 'BEGIN RSA PRIVATE KEY', risk_details_id: 4401, riskLevel: 1, matchType: 1, syncStatus: 1, updateTime: '2026-06-01 14:13' },
  { id: 10017, groupId: 'data_leakage', keyword: '数据库连接串 jdbc:mysql', risk_details_id: 4402, riskLevel: 2, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 14:21' },
  { id: 10018, groupId: 'data_leakage', keyword: '身份证号 批量导出', risk_details_id: 4403, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 14:36' },
  { id: 10019, groupId: 'gray_industry_tools', keyword: 'nmap -p 6379 --open', risk_details_id: 4501, riskLevel: 2, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 15:02' },
  { id: 10020, groupId: 'gray_industry_tools', keyword: 'hydra -L users -P pass', risk_details_id: 4502, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 15:08' },
  { id: 10021, groupId: 'gray_industry_tools', keyword: '冰蝎 WebShell 连接', risk_details_id: 4503, riskLevel: 1, matchType: 2, syncStatus: 1, updateTime: '2026-06-01 15:16' },
  { id: 10022, groupId: 'gray_industry_tools', keyword: '撞库脚本 验证码绕过', risk_details_id: 4504, riskLevel: 1, matchType: 2, syncStatus: 0, updateTime: '2026-06-01 15:28' },
])

const selectedGroup = shallowRef<string | undefined>()
const searchKeyword = shallowRef('')
const modalVisible = shallowRef(false)
const modalLoading = shallowRef(false)
const editingRecord = shallowRef<RiskFeatureKeyword | null>(null)
const formRef = useTemplateRef<any>('featureForm')

const formState = reactive({
  keyword: '',
  groupId: undefined as string | undefined,
  risk_details_id: undefined as number | undefined,
  riskLevel: 1 as RiskLevel,
  matchType: 1 as MatchType,
  syncStatus: 0 as SyncStatus,
})

const formRules: Record<string, any[]> = {
  keyword: [{ required: true, message: '请输入特征词', trigger: 'blur' }],
  groupId: [{ required: true, message: '请选择词库分组', trigger: 'change' }],
  risk_details_id: [{ required: true, type: 'number', message: '请输入所属小类 ID', trigger: 'change' }],
  riskLevel: [{ required: true, type: 'number', message: '请选择风险等级', trigger: 'change' }],
  matchType: [{ required: true, type: 'number', message: '请选择匹配模式', trigger: 'change' }],
}

const filteredKeywords = computed(() => {
  const text = searchKeyword.value.trim().toLowerCase()

  return keywords.value.filter((item) => {
    const matchesGroup = !selectedGroup.value || item.groupId === selectedGroup.value
    const matchesKeyword = !text
      || item.keyword.toLowerCase().includes(text)
      || String(item.risk_details_id).includes(text)

    return matchesGroup && matchesKeyword
  })
})

const pendingSyncCount = computed(() => filteredKeywords.value.filter(item => item.syncStatus === 0).length)
const syncedCount = computed(() => filteredKeywords.value.filter(item => item.syncStatus === 1).length)

function getCurrentTime() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function getGroupName(groupId: string) {
  return featureGroups.find(item => item.id === groupId)?.name || '未分组'
}

function resetForm() {
  Object.assign(formState, {
    keyword: '',
    groupId: selectedGroup.value,
    risk_details_id: undefined,
    riskLevel: 1,
    matchType: 1,
    syncStatus: 0,
  })
}

function showModal(record?: RiskFeatureKeyword) {
  editingRecord.value = record || null
  if (record) {
    Object.assign(formState, {
      keyword: record.keyword,
      groupId: record.groupId,
      risk_details_id: record.risk_details_id,
      riskLevel: record.riskLevel,
      matchType: record.matchType,
      syncStatus: record.syncStatus,
    })
  }
  else {
    resetForm()
  }
  modalVisible.value = true
}

async function handleModalOk() {
  try {
    await formRef.value?.validate()
    modalLoading.value = true

    if (editingRecord.value) {
      const current = editingRecord.value
      const coreChanged = current.keyword !== formState.keyword
        || current.groupId !== formState.groupId
        || current.risk_details_id !== formState.risk_details_id
        || current.riskLevel !== formState.riskLevel
        || current.matchType !== formState.matchType

      Object.assign(current, {
        keyword: formState.keyword,
        groupId: formState.groupId!,
        risk_details_id: formState.risk_details_id!,
        riskLevel: formState.riskLevel,
        matchType: formState.matchType,
        syncStatus: coreChanged ? 0 : formState.syncStatus,
        updateTime: getCurrentTime(),
      })
      message.success('特征词更新成功')
    }
    else {
      const maxId = Math.max(...keywords.value.map(item => item.id), 10000)
      keywords.value.unshift({
        id: maxId + 1,
        keyword: formState.keyword,
        groupId: formState.groupId!,
        risk_details_id: formState.risk_details_id!,
        riskLevel: formState.riskLevel,
        matchType: formState.matchType,
        syncStatus: 0,
        updateTime: getCurrentTime(),
      })
      message.success('特征词新增成功')
    }

    modalVisible.value = false
  }
  catch {
    // form validation error
  }
  finally {
    modalLoading.value = false
  }
}

function handleDelete(record: RiskFeatureKeyword) {
  Modal.confirm({
    title: '确认删除特征词',
    content: `确定删除「${record.keyword}」吗？删除后仅影响当前 mock 数据。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      keywords.value = keywords.value.filter(item => item.id !== record.id)
      message.success('特征词删除成功')
    },
  })
}

function handlePushToRedis() {
  const pendingIds = new Set(filteredKeywords.value.filter(item => item.syncStatus === 0).map(item => item.id))
  if (!pendingIds.size) {
    message.info('当前筛选范围内没有待同步特征词')
    return
  }

  keywords.value = keywords.value.map(item =>
    pendingIds.has(item.id)
      ? { ...item, syncStatus: 1, updateTime: getCurrentTime() }
      : item,
  )
  message.success(`已推送 ${pendingIds.size} 个特征词到 Redis 热更字典树`)
}

function handleResetFilters() {
  selectedGroup.value = undefined
  searchKeyword.value = ''
}
</script>

<template>
  <div class="risk-feature-page">
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">
            首页
          </router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>知识库管理</a-breadcrumb-item>
        <a-breadcrumb-item>风险特征库</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title">
            风险特征库
          </h2>
          <p class="page-desc">
            维护 AC 自动机与热更字典树使用的高危特征词，覆盖漏洞利用、注入攻击、暴恐极端与黑灰产工具链。
          </p>
        </div>
      </div>

      <div class="toolbar-card">
        <div class="toolbar-left">
          <a-select
            v-model:value="selectedGroup"
            class="group-select"
            allow-clear
            placeholder="全部分组"
          >
            <a-select-option v-for="item in featureGroups" :key="item.id" :value="item.id">
              {{ item.name }}
            </a-select-option>
          </a-select>

          <a-input-search
            v-model:value="searchKeyword"
            class="search-input"
            allow-clear
            placeholder="搜索特征词或小类 ID"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input-search>

          <a-button @click="handleResetFilters">
            重置
          </a-button>
        </div>

        <div class="toolbar-actions">
          <a-button @click="showModal()">
            <template #icon>
              <PlusOutlined />
            </template>
            新增特征词
          </a-button>
          <a-button type="primary" danger class="redis-button" @click="handlePushToRedis">
            <template #icon>
              <SyncOutlined />
            </template>
            推送到 Redis (热更字典树)
          </a-button>
        </div>
      </div>

      <div class="table-card">
        <div class="table-header">
          <div>
            <h3 class="table-title">
              特征词列表
            </h3>
            <p class="table-desc">
              当前筛选 {{ filteredKeywords.length }} 条，待同步 {{ pendingSyncCount }} 条，已同步 {{ syncedCount }} 条
            </p>
          </div>
        </div>

        <a-table
          :columns="columns"
          :data-source="filteredKeywords"
          :pagination="pagination"
          :scroll="{ x: 1120 }"
          row-key="id"
          :locale="{ emptyText: '暂无风险特征词数据' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'id'">
              <span class="keyword-id">{{ record.id }}</span>
            </template>

            <template v-if="column.key === 'keyword'">
              <div class="keyword-cell">
                <span class="keyword-text">{{ record.keyword }}</span>
                <span class="group-name">{{ getGroupName(record.groupId) }}</span>
              </div>
            </template>

            <template v-if="column.key === 'risk_details_id'">
              <span class="details-id">{{ record.risk_details_id }}</span>
            </template>

            <template v-if="column.key === 'riskLevel'">
              <a-tag v-if="record.riskLevel === 1" color="red">
                硬阻断
              </a-tag>
              <a-tag v-else color="orange">
                疑似打标
              </a-tag>
            </template>

            <template v-if="column.key === 'matchType'">
              <a-tag color="blue">
                {{ record.matchType === 1 ? '精确' : '模糊' }}
              </a-tag>
            </template>

            <template v-if="column.key === 'syncStatus'">
              <span v-if="record.syncStatus === 0" class="sync-status pending">
                <WarningOutlined />
                待同步
              </span>
              <span v-else class="sync-status synced">
                <CheckCircleOutlined />
                已同步
              </span>
            </template>

            <template v-if="column.key === 'action'">
              <a-button size="small" type="link" @click="showModal(record as RiskFeatureKeyword)">
                <template #icon>
                  <EditOutlined />
                </template>
                编辑
              </a-button>
              <a-button size="small" type="link" danger @click="handleDelete(record as RiskFeatureKeyword)">
                <template #icon>
                  <DeleteOutlined />
                </template>
                删除
              </a-button>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="editingRecord ? '编辑特征词' : '新增特征词'"
      width="620px"
      :confirm-loading="modalLoading"
      :destroy-on-close="true"
      @ok="handleModalOk"
    >
      <a-form ref="featureForm" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item name="keyword" label="特征词">
          <a-input v-model:value="formState.keyword" placeholder="请输入特征词，例如 CVE-2021-44228" />
        </a-form-item>

        <a-form-item name="groupId" label="词库分组">
          <a-select v-model:value="formState.groupId" placeholder="请选择词库分组">
            <a-select-option v-for="item in featureGroups" :key="item.id" :value="item.id">
              {{ item.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item name="risk_details_id" label="所属小类 ID">
          <a-input-number
            v-model:value="formState.risk_details_id"
            :min="1"
            :precision="0"
            class="full-input"
            placeholder="请输入所属小类 ID"
          />
        </a-form-item>

        <div class="form-grid">
          <a-form-item name="riskLevel" label="风险等级">
            <a-select v-model:value="formState.riskLevel">
              <a-select-option :value="1">
                硬阻断
              </a-select-option>
              <a-select-option :value="2">
                疑似打标
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item name="matchType" label="匹配模式">
            <a-select v-model:value="formState.matchType">
              <a-select-option :value="1">
                精确
              </a-select-option>
              <a-select-option :value="2">
                模糊
              </a-select-option>
            </a-select>
          </a-form-item>
        </div>

        <a-form-item label="同步状态">
          <a-select v-model:value="formState.syncStatus">
            <a-select-option :value="0">
              待同步
            </a-select-option>
            <a-select-option :value="1">
              已同步
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.risk-feature-page {
  padding: 24px;
}

.page-breadcrumb {
  margin-bottom: 20px;
}

.page-breadcrumb :deep(.ant-breadcrumb-link) {
  color: #8c8c8c;
  font-size: 13px;
}

.page-breadcrumb :deep(.ant-breadcrumb-link a) {
  color: #8c8c8c;
}

.page-breadcrumb :deep(.ant-breadcrumb-link a:hover) {
  color: #1677ff;
}

.page-content {
  max-width: 1400px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-title {
  color: #262626;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 6px;
}

.page-desc {
  color: #8c8c8c;
  font-size: 13px;
}

.toolbar-card,
.table-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

.toolbar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  margin-bottom: 16px;
}

.toolbar-left,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.group-select {
  width: 220px;
}

.search-input {
  width: 280px;
}

.redis-button {
  box-shadow: 0 6px 14px rgba(255, 77, 79, 0.18);
}

.table-card {
  overflow: hidden;
}

.table-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.table-title {
  color: #262626;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.table-desc {
  color: #8c8c8c;
  font-size: 12px;
}

.keyword-id {
  color: #1677ff;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
}

.keyword-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.keyword-text {
  color: #262626;
  font-weight: 500;
}

.group-name {
  color: #8c8c8c;
  font-size: 12px;
}

.details-id {
  color: #595959;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
}

.sync-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.sync-status.pending {
  color: #d46b08;
  background: #fff7e6;
}

.sync-status.synced {
  color: #389e0d;
  background: #f6ffed;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-input {
  width: 100%;
}

@media (max-width: 768px) {
  .risk-feature-page {
    padding: 16px;
  }

  .page-header,
  .toolbar-card {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .group-select,
  .search-input,
  .toolbar-card :deep(.ant-btn) {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
