<script setup lang="ts">
import {
  BookOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FileSearchOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, reactive, ref, shallowRef, useTemplateRef } from 'vue'

type DimensionKey = 'FULL' | 'JAILBREAK' | 'TOXICITY' | 'PRIVACY' | 'BIAS' | 'PROMPT_INJECTION'
type StatusFilter = 'all' | 'enabled' | 'disabled'

interface KnowledgeBase {
  id: number
  name: string
  code: string
  dimensions: DimensionKey[]
  sampleCount: number
  version: string
  description: string
  enabled: boolean
  createTime: string
  updateTime: string
}

const dimensionOptions: Array<{ value: DimensionKey, label: string, color: string, bg: string }> = [
  { value: 'FULL', label: '全维度', color: '#1677ff', bg: '#e6f4ff' },
  { value: 'JAILBREAK', label: '越狱攻击', color: '#ff4d4f', bg: '#fff2f0' },
  { value: 'TOXICITY', label: '有毒内容', color: '#faad14', bg: '#fff7e6' },
  { value: 'PRIVACY', label: '隐私泄露', color: '#722ed1', bg: '#f9f0ff' },
  { value: 'BIAS', label: '偏见歧视', color: '#1677ff', bg: '#e6f4ff' },
  { value: 'PROMPT_INJECTION', label: '提示注入', color: '#52c41a', bg: '#f6ffed' },
]

const columns = [
  { title: '知识库信息', key: 'knowledge', width: 280 },
  { title: '评测维度', key: 'dimensions', width: 280 },
  { title: '样本数量', key: 'sampleCount', width: 120, align: 'right' as const },
  { title: '版本', key: 'version', width: 100 },
  { title: '启用状态', key: 'status', width: 120, align: 'center' as const },
  { title: '更新时间', key: 'updateTime', width: 170 },
  { title: '操作', key: 'action', width: 150, align: 'center' as const },
]

const pagination = {
  pageSize: 8,
  showTotal: (total: number) => `共 ${total} 条`,
  showSizeChanger: true,
}

const knowledgeBases = ref<KnowledgeBase[]>([
  {
    id: 1,
    name: '平台官方全维度基线知识库',
    code: 'OFFICIAL_BASELINE_V2',
    dimensions: ['FULL', 'JAILBREAK', 'TOXICITY', 'PRIVACY', 'BIAS'],
    sampleCount: 15000,
    version: 'v2.0',
    description: '覆盖越狱攻击、有毒内容、隐私泄露、偏见歧视等核心安全维度，用于模型准入和周期性巡检。',
    enabled: true,
    createTime: '2024-01-01 00:00',
    updateTime: '2024-03-01 10:20',
  },
  {
    id: 2,
    name: '自定义全维度混合知识库',
    code: 'CUSTOM_MIXED_BASELINE',
    dimensions: ['FULL', 'PROMPT_INJECTION', 'PRIVACY', 'BIAS'],
    sampleCount: 5200,
    version: 'v1.3',
    description: '沉淀业务侧高频风险样本，适合回归测试和灰度发布前的补充评测。',
    enabled: true,
    createTime: '2024-02-01 00:00',
    updateTime: '2024-05-12 16:45',
  },
  {
    id: 3,
    name: '越狱与提示注入专项知识库',
    code: 'JAILBREAK_INJECTION_SUITE',
    dimensions: ['JAILBREAK', 'PROMPT_INJECTION'],
    sampleCount: 4200,
    version: 'v1.5',
    description: '聚焦角色扮演、规则绕过、多轮诱导和提示注入攻击场景。',
    enabled: true,
    createTime: '2024-01-15 00:00',
    updateTime: '2024-04-18 11:10',
  },
  {
    id: 4,
    name: '隐私泄露测试知识库',
    code: 'PRIVACY_LEAK_SUITE',
    dimensions: ['PRIVACY'],
    sampleCount: 2600,
    version: 'v1.1',
    description: '用于检测模型是否泄露个人身份信息、密钥、内部策略或敏感业务数据。',
    enabled: true,
    createTime: '2024-02-01 00:00',
    updateTime: '2024-04-09 09:30',
  },
  {
    id: 5,
    name: '偏见与歧视测试知识库',
    code: 'BIAS_FAIRNESS_SUITE',
    dimensions: ['BIAS'],
    sampleCount: 3100,
    version: 'v2.1',
    description: '覆盖职业、地域、性别、年龄等常见公平性评测样本。',
    enabled: false,
    createTime: '2024-01-20 00:00',
    updateTime: '2024-03-26 15:05',
  },
  {
    id: 6,
    name: '有毒内容安全知识库',
    code: 'TOXICITY_SAFETY_SUITE',
    dimensions: ['TOXICITY'],
    sampleCount: 3600,
    version: 'v1.8',
    description: '评估模型在仇恨、辱骂、暴力、违法建议等输出场景下的安全边界。',
    enabled: true,
    createTime: '2024-01-10 00:00',
    updateTime: '2024-05-03 13:40',
  },
])

const keyword = shallowRef('')
const dimensionFilter = shallowRef<DimensionKey | undefined>()
const statusFilter = shallowRef<StatusFilter>('all')
const modalVisible = shallowRef(false)
const modalLoading = shallowRef(false)
const editingRecord = shallowRef<KnowledgeBase | null>(null)
const formRef = useTemplateRef<any>('knowledgeForm')

const formState = reactive({
  name: '',
  code: '',
  dimensions: [] as DimensionKey[],
  sampleCount: undefined as number | undefined,
  version: '',
  description: '',
  enabled: true,
})

const formRules: Record<string, any[]> = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入知识库标识', trigger: 'blur' }],
  dimensions: [{ required: true, type: 'array', message: '请选择评测维度', trigger: 'change' }],
  sampleCount: [{ required: true, type: 'number', message: '请输入样本数量', trigger: 'change' }],
}

const filteredKnowledgeBases = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return knowledgeBases.value.filter((item) => {
    const matchesKeyword = !text
      || item.name.toLowerCase().includes(text)
      || item.code.toLowerCase().includes(text)
      || item.description.toLowerCase().includes(text)

    const matchesDimension = !dimensionFilter.value || item.dimensions.includes(dimensionFilter.value)
    const matchesStatus = statusFilter.value === 'all'
      || (statusFilter.value === 'enabled' && item.enabled)
      || (statusFilter.value === 'disabled' && !item.enabled)

    return matchesKeyword && matchesDimension && matchesStatus
  })
})

const enabledCount = computed(() => knowledgeBases.value.filter(item => item.enabled).length)
const totalSampleCount = computed(() => knowledgeBases.value.reduce((sum, item) => sum + item.sampleCount, 0))
const latestUpdateTime = computed(() => {
  const sorted = [...knowledgeBases.value].sort((a, b) => b.updateTime.localeCompare(a.updateTime))
  return sorted[0]?.updateTime || '-'
})

const metricCards = computed(() => [
  {
    key: 'total',
    label: '知识库总数',
    value: knowledgeBases.value.length,
    desc: '覆盖核心评测场景',
    icon: BookOutlined,
    iconColor: '#1677ff',
    bgColor: '#e6f4ff',
  },
  {
    key: 'enabled',
    label: '已启用',
    value: enabledCount.value,
    desc: '可用于创建评测任务',
    icon: CheckCircleOutlined,
    iconColor: '#52c41a',
    bgColor: '#f6ffed',
  },
  {
    key: 'samples',
    label: '样本总量',
    value: formatNumber(totalSampleCount.value),
    desc: '当前 mock 样本规模',
    icon: FileSearchOutlined,
    iconColor: '#722ed1',
    bgColor: '#f9f0ff',
  },
  {
    key: 'latest',
    label: '最近更新',
    value: latestUpdateTime.value,
    desc: '按更新时间倒序统计',
    icon: ClockCircleOutlined,
    iconColor: '#faad14',
    bgColor: '#fff7e6',
  },
])

function getDimensionMeta(value: DimensionKey) {
  return dimensionOptions.find(item => item.value === value) || dimensionOptions[0]
}

function formatNumber(value: number) {
  return value.toLocaleString('zh-CN')
}

function getCurrentTime() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function resetForm() {
  Object.assign(formState, {
    name: '',
    code: '',
    dimensions: [],
    sampleCount: undefined,
    version: 'v1.0',
    description: '',
    enabled: true,
  })
}

function showModal(record: KnowledgeBase | null = null) {
  editingRecord.value = record

  if (record) {
    Object.assign(formState, {
      name: record.name,
      code: record.code,
      dimensions: [...record.dimensions],
      sampleCount: record.sampleCount,
      version: record.version,
      description: record.description,
      enabled: record.enabled,
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

    const duplicated = knowledgeBases.value.some(item =>
      item.code === formState.code && item.id !== editingRecord.value?.id,
    )
    if (duplicated) {
      message.warning('知识库标识已存在，请更换后再保存')
      return
    }

    modalLoading.value = true
    const updateTime = getCurrentTime()

    if (editingRecord.value) {
      const index = knowledgeBases.value.findIndex(item => item.id === editingRecord.value?.id)
      if (index !== -1) {
        knowledgeBases.value[index] = {
          ...knowledgeBases.value[index],
          name: formState.name,
          code: formState.code,
          dimensions: [...formState.dimensions],
          sampleCount: formState.sampleCount || 0,
          version: formState.version || 'v1.0',
          description: formState.description,
          enabled: formState.enabled,
          updateTime,
        }
        message.success('知识库更新成功')
      }
    }
    else {
      knowledgeBases.value.unshift({
        id: Date.now(),
        name: formState.name,
        code: formState.code,
        dimensions: [...formState.dimensions],
        sampleCount: formState.sampleCount || 0,
        version: formState.version || 'v1.0',
        description: formState.description,
        enabled: formState.enabled,
        createTime: updateTime,
        updateTime,
      })
      message.success('知识库创建成功')
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

function handleStatusChange(record: KnowledgeBase, checked: boolean) {
  record.enabled = checked
  record.updateTime = getCurrentTime()
  message.success(`${record.name} 已${checked ? '启用' : '停用'}`)
}

function handleDelete(record: KnowledgeBase) {
  knowledgeBases.value = knowledgeBases.value.filter(item => item.id !== record.id)
  message.success('知识库删除成功')
}

function handleResetFilters() {
  keyword.value = ''
  dimensionFilter.value = undefined
  statusFilter.value = 'all'
}
</script>

<template>
  <div class="knowledge-page">
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">首页</router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>知识库管理</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title">评测知识库管理</h2>
          <p class="page-desc">维护评测题库、风险维度和样本规模，用于模型安全评测任务创建</p>
        </div>
        <a-button type="primary" @click="showModal()">
          <template #icon><PlusOutlined /></template>
          新增知识库
        </a-button>
      </div>

      <div class="metric-grid">
        <div v-for="item in metricCards" :key="item.key" class="metric-card card-hover-transition">
          <div class="metric-icon-wrap" :style="{ background: item.bgColor }">
            <component :is="item.icon" class="metric-icon" :style="{ color: item.iconColor }" />
          </div>
          <div class="metric-info">
            <div class="metric-label">{{ item.label }}</div>
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <div class="filter-section">
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索名称、标识或描述..."
          class="search-input"
          allow-clear
        >
          <template #prefix><SearchOutlined /></template>
        </a-input-search>

        <a-select
          v-model:value="dimensionFilter"
          placeholder="评测维度"
          allow-clear
          class="filter-select"
        >
          <a-select-option v-for="item in dimensionOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>

        <a-select v-model:value="statusFilter" class="filter-select">
          <a-select-option value="all">全部状态</a-select-option>
          <a-select-option value="enabled">已启用</a-select-option>
          <a-select-option value="disabled">已停用</a-select-option>
        </a-select>

        <a-button @click="handleResetFilters">重置</a-button>
      </div>

      <div class="table-card">
        <a-table
          :columns="columns"
          :data-source="filteredKnowledgeBases"
          :pagination="pagination"
          :scroll="{ x: 1100 }"
          row-key="id"
          :locale="{ emptyText: '暂无知识库数据' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'knowledge'">
              <div class="knowledge-cell">
                <div class="knowledge-icon-wrap">
                  <BookOutlined class="knowledge-icon" />
                </div>
                <div class="knowledge-info">
                  <span class="knowledge-name">{{ record.name }}</span>
                  <span class="knowledge-code">{{ record.code }}</span>
                  <span class="knowledge-desc">{{ record.description || '-' }}</span>
                </div>
              </div>
            </template>

            <template v-if="column.key === 'dimensions'">
              <div class="dimension-tags">
                <a-tag
                  v-for="dimension in record.dimensions"
                  :key="dimension"
                  class="dimension-tag"
                  :style="{
                    color: getDimensionMeta(dimension).color,
                    background: getDimensionMeta(dimension).bg,
                    borderColor: getDimensionMeta(dimension).bg,
                  }"
                >
                  {{ getDimensionMeta(dimension).label }}
                </a-tag>
              </div>
            </template>

            <template v-if="column.key === 'sampleCount'">
              <span class="sample-count">{{ formatNumber(record.sampleCount) }}</span>
            </template>

            <template v-if="column.key === 'version'">
              <span class="version-text">{{ record.version }}</span>
            </template>

            <template v-if="column.key === 'status'">
              <a-switch
                :checked="record.enabled"
                checked-children="启用"
                un-checked-children="停用"
                @change="(checked) => handleStatusChange(record as KnowledgeBase, Boolean(checked))"
              />
            </template>

            <template v-if="column.key === 'updateTime'">
              <div class="time-cell">
                <ClockCircleOutlined class="time-icon" />
                <span>{{ record.updateTime }}</span>
              </div>
            </template>

            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="showModal(record as KnowledgeBase)">
                  <template #icon><EditOutlined /></template>
                  编辑
                </a-button>
                <a-popconfirm
                  title="确定要删除该知识库吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleDelete(record as KnowledgeBase)"
                >
                  <a-button type="link" size="small" danger>
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="editingRecord ? '编辑知识库' : '新增知识库'"
      width="560px"
      :confirm-loading="modalLoading"
      :destroyOnClose="true"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleModalOk"
      @cancel="modalVisible = false"
    >
      <a-form
        ref="knowledgeForm"
        :model="formState"
        :rules="formRules"
        layout="vertical"
        style="margin-top: 16px"
      >
        <a-form-item name="name" label="知识库名称">
          <a-input v-model:value="formState.name" placeholder="例如：平台官方全维度基线知识库" />
        </a-form-item>

        <a-form-item name="code" label="知识库标识">
          <a-input v-model:value="formState.code" placeholder="例如：OFFICIAL_BASELINE_V2" />
        </a-form-item>

        <a-form-item name="dimensions" label="评测维度">
          <a-select
            v-model:value="formState.dimensions"
            mode="multiple"
            placeholder="请选择评测维度"
          >
            <a-select-option v-for="item in dimensionOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="sampleCount" label="样本数量">
              <a-input-number
                v-model:value="formState.sampleCount"
                :min="1"
                :precision="0"
                placeholder="请输入样本数量"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="version" label="版本号">
              <a-input v-model:value="formState.version" placeholder="例如：v1.0" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item name="description" label="描述">
          <a-textarea
            v-model:value="formState.description"
            placeholder="请输入知识库说明"
            :rows="3"
          />
        </a-form-item>

        <a-form-item name="enabled" label="启用状态">
          <a-switch
            v-model:checked="formState.enabled"
            checked-children="启用"
            un-checked-children="停用"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.knowledge-page {
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
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 13px;
  color: #8c8c8c;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

.metric-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-icon {
  font-size: 20px;
}

.metric-info {
  min-width: 0;
  flex: 1;
}

.metric-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: #262626;
  line-height: 1.25;
  letter-spacing: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.search-input {
  width: 300px;
}

.filter-select {
  width: 150px;
}

.table-card {
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  border: 1px solid #f0f0f0;
}

.table-card :deep(.ant-table) {
  border-radius: 10px;
}

.table-card :deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  font-weight: 600;
  color: #434343;
  font-size: 13px;
  border-bottom: 1px solid #f0f0f0;
}

.table-card :deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f5f5f5;
}

.table-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #fafafa;
}

.knowledge-cell {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.knowledge-icon-wrap {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #e6f4ff;
}

.knowledge-icon {
  color: #1677ff;
  font-size: 18px;
}

.knowledge-info {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.knowledge-name {
  color: #262626;
  font-size: 14px;
  font-weight: 500;
}

.knowledge-code {
  margin-top: 3px;
  color: #8c8c8c;
  font-size: 12px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.knowledge-desc {
  margin-top: 5px;
  color: #595959;
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.dimension-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.dimension-tag {
  margin-inline-end: 0;
  border-radius: 4px;
}

.sample-count {
  color: #262626;
  font-size: 13px;
  font-weight: 600;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.version-text {
  color: #595959;
  font-size: 13px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #595959;
  font-size: 13px;
}

.time-icon {
  color: #8c8c8c;
  font-size: 14px;
}

.table-card :deep(.ant-btn-link) {
  padding: 0 4px;
  height: auto;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .knowledge-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
  }

  .page-header :deep(.ant-btn) {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .filter-section {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input,
  .filter-select,
  .filter-section :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
