<script setup lang="ts">
import type { RiskApiStatus, RiskCategory, RiskDetail } from '../api/risk-category'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  SafetyCertificateOutlined,
  TagsOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, onMounted, reactive, ref, shallowRef, useTemplateRef } from 'vue'
import {
  deleteRiskDetail,
  getRiskCategoryList,
  getRiskDetailList,
  updateRiskDetail,
} from '../api/risk-category'

type RiskStatus = 'ENABLED' | 'DISABLED'

interface RiskScenario {
  id: number
  scenarioName: string
  description: string
  itemCount: number
  enabledCount: number
  status: RiskStatus
  sortOrder?: number
}

interface RiskTagItem {
  id: number
  categoryId: number
  scenarioId: number
  scenarioName: string
  item_name: string
  status: RiskStatus
  description: string
  updateTime: string
  sortOrder?: number
}

const columns = [
  { title: 'ID', key: 'id', width: 120, fixed: 'left' as const },
  { title: '归属大类', key: 'scenarioName', width: 180 },
  { title: '启用/禁用', key: 'status', width: 130, align: 'center' as const },
  { title: '风险项名称', key: 'item_name', width: 260 },
  { title: '操作', key: 'action', width: 150, align: 'center' as const, fixed: 'right' as const },
]

const pagination = {
  pageSize: 8,
  showTotal: (total: number) => `共 ${total} 条`,
  showSizeChanger: true,
}

const emptyScenario: RiskScenario = {
  id: 0,
  scenarioName: '暂无风险大类',
  description: '接口暂无可展示的风险大类数据。',
  itemCount: 0,
  enabledCount: 0,
  status: 'DISABLED',
}

const loading = shallowRef(false)
const switchingId = shallowRef<number | null>(null)
const selectedScenarioId = shallowRef<number | null>(null)
const modalVisible = shallowRef(false)
const modalLoading = shallowRef(false)
const editingItem = shallowRef<RiskTagItem | null>(null)
const formRef = useTemplateRef<any>('riskTagForm')

const riskScenarios = ref<RiskScenario[]>([])
const riskTagItems = ref<RiskTagItem[]>([])
const detailDescriptionCache = reactive<Record<number, string>>({})

const formState = reactive({
  item_name: '',
  description: '',
  enabled: true,
})

const formRules: Record<string, any[]> = {
  item_name: [{ required: true, message: '请输入风险项名称', trigger: 'blur' }],
}

const selectedScenario = computed(() =>
  riskScenarios.value.find(item => item.id === selectedScenarioId.value) || emptyScenario,
)

const currentRiskItems = computed(() =>
  riskTagItems.value.filter(item => item.scenarioId === selectedScenarioId.value),
)

const totalItemCount = computed(() => riskTagItems.value.length)
const totalEnabledCount = computed(() => riskTagItems.value.filter(item => item.status === 'ENABLED').length)

const scenarioStats = computed(() => {
  const items = currentRiskItems.value
  return {
    total: items.length,
    enabled: items.filter(item => item.status === 'ENABLED').length,
    disabled: items.filter(item => item.status === 'DISABLED').length,
  }
})

const displayScenarios = computed(() =>
  riskScenarios.value.map((scenario) => {
    const items = riskTagItems.value.filter(item => item.scenarioId === scenario.id)
    return {
      ...scenario,
      itemCount: items.length,
      enabledCount: items.filter(item => item.status === 'ENABLED').length,
    }
  }),
)

function isSuccessResponse(res: { code: string | number, success?: boolean }) {
  return String(res.code) === '0' || String(res.code) === '200' || res.success === true
}

function toViewStatus(status?: number): RiskStatus {
  return status === 0 ? 'DISABLED' : 'ENABLED'
}

function toApiStatus(status: RiskStatus): RiskApiStatus {
  return status === 'ENABLED' ? 1 : 0
}

function getCurrentTime() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function buildDetailDescription(detail: RiskDetail) {
  return detailDescriptionCache[detail.id] || `用于识别“${detail.detailsName}”相关安全风险的评测标签。`
}

function toRiskScenario(category: RiskCategory): RiskScenario {
  return {
    id: category.id,
    scenarioName: category.categoryName,
    description: category.description || '暂无大类描述',
    itemCount: 0,
    enabledCount: 0,
    status: toViewStatus(category.status),
    sortOrder: category.sortOrder,
  }
}

function toRiskTagItem(detail: RiskDetail, scenarioNameMap: Map<number, string>): RiskTagItem {
  return {
    id: detail.id,
    categoryId: detail.categoryId,
    scenarioId: detail.categoryId,
    scenarioName: scenarioNameMap.get(detail.categoryId) || '未归类',
    item_name: detail.detailsName,
    status: toViewStatus(detail.status),
    description: buildDetailDescription(detail),
    updateTime: detail.updateTime || '',
    sortOrder: detail.sortOrder,
  }
}

async function fetchRiskTags() {
  loading.value = true
  try {
    const [categoryResponse, detailResponse] = await Promise.all([
      getRiskCategoryList(),
      getRiskDetailList(),
    ])

    const categoryResult = categoryResponse.data
    const detailResult = detailResponse.data

    if (!isSuccessResponse(categoryResult) || !Array.isArray(categoryResult.data)) {
      riskScenarios.value = []
      riskTagItems.value = []
      selectedScenarioId.value = null
      message.error(categoryResult.message || '获取风险大类失败')
      return
    }

    if (!isSuccessResponse(detailResult) || !Array.isArray(detailResult.data)) {
      riskScenarios.value = categoryResult.data.map(toRiskScenario)
      riskTagItems.value = []
      selectedScenarioId.value = riskScenarios.value[0]?.id || null
      message.error(detailResult.message || '获取风险明细失败')
      return
    }

    riskScenarios.value = categoryResult.data.map(toRiskScenario)
    const scenarioNameMap = new Map(riskScenarios.value.map(item => [item.id, item.scenarioName]))
    riskTagItems.value = detailResult.data.map(item => toRiskTagItem(item, scenarioNameMap))

    const selectedStillExists = riskScenarios.value.some(item => item.id === selectedScenarioId.value)
    if (!selectedStillExists) {
      selectedScenarioId.value = riskScenarios.value[0]?.id || null
    }
  }
  catch {
    riskScenarios.value = []
    riskTagItems.value = []
    selectedScenarioId.value = null
    message.error('获取风险标签失败')
  }
  finally {
    loading.value = false
  }
}

function handleScenarioSelect(id: number) {
  selectedScenarioId.value = id
}

async function handleStatusChange(record: RiskTagItem, checked: boolean | string | number) {
  const previousStatus = record.status
  const nextStatus: RiskStatus = checked === true ? 'ENABLED' : 'DISABLED'
  if (previousStatus === nextStatus)
    return

  switchingId.value = record.id
  record.status = nextStatus

  try {
    const { data: res } = await updateRiskDetail({
      id: record.id,
      categoryId: record.categoryId,
      detailsName: record.item_name,
      sortOrder: record.sortOrder,
      status: toApiStatus(nextStatus),
    })

    if (!isSuccessResponse(res)) {
      throw new Error(res.message || '状态更新失败')
    }

    record.updateTime = getCurrentTime()
    message.success(`${record.item_name} 已${nextStatus === 'ENABLED' ? '启用' : '禁用'}`)
  }
  catch {
    record.status = previousStatus
    message.error('风险项状态更新失败')
  }
  finally {
    switchingId.value = null
  }
}

function showEditModal(record: RiskTagItem) {
  editingItem.value = record
  Object.assign(formState, {
    item_name: record.item_name,
    description: record.description,
    enabled: record.status === 'ENABLED',
  })
  modalVisible.value = true
}

async function handleModalOk() {
  try {
    await formRef.value?.validate()

    if (!editingItem.value)
      return

    modalLoading.value = true
    const current = editingItem.value
    const nextStatus: RiskStatus = formState.enabled ? 'ENABLED' : 'DISABLED'
    const { data: res } = await updateRiskDetail({
      id: current.id,
      categoryId: current.categoryId,
      detailsName: formState.item_name,
      sortOrder: current.sortOrder,
      status: toApiStatus(nextStatus),
    })

    if (!isSuccessResponse(res)) {
      message.error(res.message || '风险项更新失败')
      return
    }

    current.item_name = formState.item_name
    current.description = formState.description
    current.status = nextStatus
    current.updateTime = getCurrentTime()
    detailDescriptionCache[current.id] = formState.description
    modalVisible.value = false
    message.success('风险项更新成功')
  }
  catch {
    // form validation or request error
  }
  finally {
    modalLoading.value = false
  }
}

function handleDelete(record: RiskTagItem) {
  Modal.confirm({
    title: '确认删除风险项',
    content: `确定删除「${record.item_name}」吗？删除后将同步后端风险明细数据。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        const { data: res } = await deleteRiskDetail(record.id)
        if (!isSuccessResponse(res)) {
          message.error(res.message || '风险项删除失败')
          return
        }

        riskTagItems.value = riskTagItems.value.filter(item => item.id !== record.id)
        delete detailDescriptionCache[record.id]
        message.success('风险项删除成功')
      }
      catch {
        message.error('风险项删除失败')
      }
    },
  })
}

onMounted(fetchRiskTags)
</script>

<template>
  <div class="risk-tag-page">
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">
            首页
          </router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>风险标签</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <div class="page-header">
        <div>
          <h2 class="page-title">
            风险标签
          </h2>
          <p class="page-desc">
            管理大模型安全评测风险大类与细分风险项，支持按大类查看、启停、编辑和删除。
          </p>
        </div>
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-icon-wrap blue">
            <SafetyCertificateOutlined />
          </div>
          <div>
            <div class="metric-label">
              风险大类
            </div>
            <div class="metric-value">
              {{ riskScenarios.length }}
            </div>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-icon-wrap purple">
            <TagsOutlined />
          </div>
          <div>
            <div class="metric-label">
              风险小类
            </div>
            <div class="metric-value">
              {{ totalItemCount }}
            </div>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-icon-wrap green">
            <CheckCircleOutlined />
          </div>
          <div>
            <div class="metric-label">
              已启用
            </div>
            <div class="metric-value">
              {{ totalEnabledCount }}
            </div>
          </div>
        </div>
      </div>

      <div class="risk-layout">
        <aside class="scenario-panel">
          <div class="panel-title">
            <ExclamationCircleOutlined />
            风险大类
          </div>
          <div v-if="displayScenarios.length" class="scenario-list">
            <button
              v-for="scenario in displayScenarios"
              :key="scenario.id"
              type="button"
              class="scenario-item"
              :class="{ active: scenario.id === selectedScenarioId }"
              @click="handleScenarioSelect(scenario.id)"
            >
              <span class="scenario-name">{{ scenario.scenarioName }}</span>
              <span class="scenario-desc">{{ scenario.description }}</span>
              <span class="scenario-count">
                {{ scenario.enabledCount }} / {{ scenario.itemCount }} 启用
              </span>
            </button>
          </div>
          <a-empty v-else class="scenario-empty" description="暂无风险大类" />
        </aside>

        <section class="detail-panel">
          <div class="table-card">
            <div class="table-header">
              <div>
                <h3 class="table-title">
                  {{ selectedScenario.scenarioName }}
                </h3>
                <p class="table-desc">
                  {{ selectedScenario.description }}
                </p>
              </div>
              <div class="table-stats">
                <span>总数 {{ scenarioStats.total }}</span>
                <span>启用 {{ scenarioStats.enabled }}</span>
                <span>禁用 {{ scenarioStats.disabled }}</span>
              </div>
            </div>

            <a-table
              :columns="columns"
              :data-source="currentRiskItems"
              :loading="loading"
              :pagination="pagination"
              :scroll="{ x: 820 }"
              row-key="id"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'id'">
                  <span class="risk-id">{{ record.id }}</span>
                </template>

                <template v-if="column.key === 'scenarioName'">
                  <span class="scenario-tag">{{ record.scenarioName }}</span>
                </template>

                <template v-if="column.key === 'status'">
                  <a-switch
                    :checked="record.status === 'ENABLED'"
                    :loading="switchingId === record.id"
                    checked-children="启用"
                    un-checked-children="禁用"
                    @change="checked => handleStatusChange(record as RiskTagItem, checked)"
                  />
                </template>

                <template v-if="column.key === 'item_name'">
                  <div class="risk-name-cell">
                    <span class="risk-name">{{ record.item_name }}</span>
                    <span class="risk-desc">{{ record.description }}</span>
                  </div>
                </template>

                <template v-if="column.key === 'action'">
                  <a-button size="small" type="link" @click="showEditModal(record as RiskTagItem)">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-button size="small" type="link" danger @click="handleDelete(record as RiskTagItem)">
                    <template #icon>
                      <DeleteOutlined />
                    </template>
                    删除
                  </a-button>
                </template>
              </template>
            </a-table>
          </div>
        </section>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      title="编辑风险项"
      width="560px"
      :confirm-loading="modalLoading"
      :destroy-on-close="true"
      @ok="handleModalOk"
    >
      <a-form ref="riskTagForm" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item name="item_name" label="风险项名称">
          <a-input v-model:value="formState.item_name" placeholder="请输入风险项名称" />
        </a-form-item>

        <a-form-item name="description" label="风险项描述">
          <a-textarea
            v-model:value="formState.description"
            :rows="4"
            placeholder="请输入风险项描述"
          />
        </a-form-item>

        <a-form-item label="启用状态">
          <a-switch v-model:checked="formState.enabled" checked-children="启用" un-checked-children="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.risk-tag-page {
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

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 16px;
}

.metric-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  font-size: 20px;
}

.metric-icon-wrap.blue {
  color: #1677ff;
  background: #e6f4ff;
}

.metric-icon-wrap.purple {
  color: #722ed1;
  background: #f9f0ff;
}

.metric-icon-wrap.green {
  color: #52c41a;
  background: #f6ffed;
}

.metric-label {
  color: #8c8c8c;
  font-size: 12px;
}

.metric-value {
  color: #262626;
  font-size: 22px;
  font-weight: 600;
}

.risk-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.scenario-panel,
.table-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

.scenario-panel {
  padding: 14px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #262626;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.scenario-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.scenario-item {
  width: 100%;
  padding: 12px;
  text-align: left;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.scenario-item:hover,
.scenario-item.active {
  background: #f0f7ff;
  border-color: #91caff;
}

.scenario-name {
  display: block;
  color: #262626;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}

.scenario-desc {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
  margin-bottom: 8px;
}

.scenario-count {
  color: #1677ff;
  font-size: 12px;
  font-weight: 600;
}

.scenario-empty {
  margin-top: 32px;
}

.table-card {
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.table-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.table-stats span {
  padding: 4px 8px;
  color: #1677ff;
  background: #e6f4ff;
  border-radius: 6px;
  font-size: 12px;
}

.risk-id {
  color: #1677ff;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
}

.scenario-tag {
  display: inline-flex;
  padding: 3px 8px;
  color: #1677ff;
  background: #e6f4ff;
  border-radius: 6px;
  font-size: 12px;
}

.risk-name-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.risk-name {
  color: #262626;
  font-weight: 500;
}

.risk-desc {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.45;
}

@media (max-width: 768px) {
  .risk-tag-page {
    padding: 16px;
  }

  .page-header,
  .table-header {
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .risk-layout {
    grid-template-columns: 1fr;
  }

  .scenario-panel {
    overflow-x: auto;
  }

  .scenario-list {
    min-width: 560px;
    flex-direction: row;
  }

  .scenario-item {
    min-width: 180px;
  }
}
</style>
