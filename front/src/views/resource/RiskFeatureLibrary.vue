<script setup lang="ts">
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { RiskDetail } from '../../api/risk-category'
import type {
  RiskVocabularyKeyword,
  RiskVocabularyMatchType,
  RiskVocabularyRiskLevel,
  RiskVocabularySyncStatus,
} from '../../api/risk-vocabulary'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, onMounted, reactive, ref, shallowRef, useTemplateRef } from 'vue'
import { getRiskDetailList } from '../../api/risk-category'
import {
  createRiskVocabulary,
  deleteRiskVocabulary,
  getRiskVocabularyPage,
  syncRiskVocabularyToRedis,
  updateRiskVocabulary,
} from '../../api/risk-vocabulary'

interface RiskDetailDisplay extends RiskDetail {
  itemCount: number
  pendingCount: number
}

interface FeatureFormState {
  keyword: string
  riskDetailsId?: number
  riskLevel: RiskVocabularyRiskLevel
  matchType: RiskVocabularyMatchType
  syncStatus: RiskVocabularySyncStatus
}

const columns = [
  { title: 'ID', key: 'id', width: 110, fixed: 'left' as const },
  { title: '特征词', key: 'keyword', width: 240 },
  { title: '风险等级', key: 'riskLevel', width: 150, align: 'center' as const },
  { title: '匹配模式', key: 'matchType', width: 150, align: 'center' as const },
  { title: '同步状态', key: 'syncStatus', width: 160, align: 'center' as const },
  { title: '操作', key: 'action', width: 150, align: 'center' as const, fixed: 'right' as const },
]

const riskDetails = ref<RiskDetail[]>([])
const keywords = ref<RiskVocabularyKeyword[]>([])

const riskDetailsLoading = shallowRef(false)
const tableLoading = shallowRef(false)
const modalVisible = shallowRef(false)
const modalLoading = shallowRef(false)
const syncLoading = shallowRef(false)
const deletingId = shallowRef<number | null>(null)
const editingRecord = shallowRef<RiskVocabularyKeyword | null>(null)

const selectedRiskDetailId = shallowRef<number | null>(null)
const searchKeyword = shallowRef('')
const currentPage = shallowRef(1)
const pageSize = shallowRef(10)
const total = shallowRef(0)
const formRef = useTemplateRef<FormInstance>('featureForm')

const formState = reactive<FeatureFormState>({
  keyword: '',
  riskDetailsId: undefined,
  riskLevel: 1,
  matchType: 1,
  syncStatus: 0,
})

const formRules: Record<string, Rule[]> = {
  keyword: [{ required: true, message: '请输入特征词', trigger: 'blur' }],
  riskDetailsId: [{ required: true, type: 'number', message: '请选择所属小类 ID', trigger: 'change' }],
  riskLevel: [{ required: true, type: 'number', message: '请选择风险等级', trigger: 'change' }],
  matchType: [{ required: true, type: 'number', message: '请选择匹配模式', trigger: 'change' }],
}

const selectedRiskDetail = computed(() =>
  riskDetails.value.find(item => item.id === selectedRiskDetailId.value) || null,
)

const selectedRiskDetailName = computed(() => selectedRiskDetail.value?.detailsName || '暂无风险小类')
const selectedRiskDetailMeta = computed(() => {
  if (!selectedRiskDetail.value)
    return '请选择左侧风险小类'

  return `大类 ID ${selectedRiskDetail.value.categoryId} / 小类 ID ${selectedRiskDetail.value.id}`
})

const currentPageItemCount = computed(() => keywords.value.length)
const pendingSyncCount = computed(() => keywords.value.filter(item => item.syncStatus === 0).length)
const syncedCount = computed(() => keywords.value.filter(item => item.syncStatus === 1).length)

const displayRiskDetails = computed<RiskDetailDisplay[]>(() =>
  riskDetails.value.map((detail) => {
    const isSelected = detail.id === selectedRiskDetailId.value

    return {
      ...detail,
      itemCount: isSelected ? currentPageItemCount.value : 0,
      pendingCount: isSelected ? pendingSyncCount.value : 0,
    }
  }),
)

const tablePagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalNumber: number) => `共 ${totalNumber} 条`,
}))

function isSuccessResponse(res: { code: string | number, success?: boolean }) {
  return String(res.code) === '0' || String(res.code) === '200' || res.success === true
}

function sortRiskDetails(items: RiskDetail[]) {
  return [...items]
    .filter(item => !item.deleted)
    .sort((first, second) => {
      const firstOrder = first.sortOrder ?? Number.MAX_SAFE_INTEGER
      const secondOrder = second.sortOrder ?? Number.MAX_SAFE_INTEGER

      return firstOrder - secondOrder || first.id - second.id
    })
}

function resetVocabularyPage() {
  keywords.value = []
  total.value = 0
  currentPage.value = 1
}

async function fetchVocabularyPage() {
  if (!selectedRiskDetailId.value) {
    resetVocabularyPage()
    return
  }

  tableLoading.value = true
  try {
    const keyword = searchKeyword.value.trim()
    const { data: res } = await getRiskVocabularyPage({
      current: currentPage.value,
      size: pageSize.value,
      riskDetailsId: selectedRiskDetailId.value,
      keyword: keyword || undefined,
    })

    if (!isSuccessResponse(res) || !res.data) {
      throw new Error(res.message || '获取风险特征词失败')
    }

    keywords.value = Array.isArray(res.data.records) ? res.data.records : []
    total.value = Number(res.data.total || 0)
    currentPage.value = Number(res.data.current || currentPage.value)
    pageSize.value = Number(res.data.size || pageSize.value)
  }
  catch (error) {
    keywords.value = []
    total.value = 0
    message.error(error instanceof Error ? error.message : '获取风险特征词失败')
  }
  finally {
    tableLoading.value = false
  }
}

async function fetchRiskDetails() {
  riskDetailsLoading.value = true
  try {
    const { data: res } = await getRiskDetailList()

    if (!isSuccessResponse(res) || !Array.isArray(res.data)) {
      throw new Error(res.message || '获取风险小类失败')
    }

    riskDetails.value = sortRiskDetails(res.data)

    const selectedStillExists = riskDetails.value.some(item => item.id === selectedRiskDetailId.value)
    selectedRiskDetailId.value = selectedStillExists
      ? selectedRiskDetailId.value
      : riskDetails.value[0]?.id || null

    if (selectedRiskDetailId.value) {
      currentPage.value = 1
      await fetchVocabularyPage()
    }
    else {
      resetVocabularyPage()
    }
  }
  catch (error) {
    riskDetails.value = []
    selectedRiskDetailId.value = null
    resetVocabularyPage()
    message.error(error instanceof Error ? error.message : '获取风险小类失败')
  }
  finally {
    riskDetailsLoading.value = false
  }
}

async function handleRiskDetailSelect(id: number) {
  if (id === selectedRiskDetailId.value)
    return

  selectedRiskDetailId.value = id
  currentPage.value = 1
  await fetchVocabularyPage()
}

async function handleSearch() {
  currentPage.value = 1
  await fetchVocabularyPage()
}

async function handleResetSearch() {
  searchKeyword.value = ''
  currentPage.value = 1
  await fetchVocabularyPage()
}

async function handleTableChange(pagination: { current?: number, pageSize?: number }) {
  currentPage.value = pagination.current || 1
  pageSize.value = pagination.pageSize || pageSize.value
  await fetchVocabularyPage()
}

function resetForm() {
  Object.assign(formState, {
    keyword: '',
    riskDetailsId: selectedRiskDetailId.value || riskDetails.value[0]?.id,
    riskLevel: 1,
    matchType: 1,
    syncStatus: 0,
  })
}

function showModal(record?: RiskVocabularyKeyword) {
  editingRecord.value = record || null

  if (record) {
    Object.assign(formState, {
      keyword: record.keyword,
      riskDetailsId: record.riskDetailsId,
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

function closeModal() {
  modalVisible.value = false
}

async function handleModalOk() {
  try {
    await formRef.value?.validate()

    if (!formState.riskDetailsId) {
      message.warning('请选择所属小类 ID')
      return
    }

    modalLoading.value = true

    if (editingRecord.value) {
      const current = editingRecord.value
      const { data: res } = await updateRiskVocabulary({
        id: current.id,
        groupId: current.groupId,
        keyword: formState.keyword,
        riskDetailsId: formState.riskDetailsId,
        riskLevel: formState.riskLevel,
        matchType: formState.matchType,
        syncStatus: formState.syncStatus,
      })

      if (!isSuccessResponse(res) || !res.data) {
        throw new Error(res.message || '编辑特征词失败')
      }

      modalVisible.value = false
      message.success('特征词编辑成功')
      await fetchVocabularyPage()
      return
    }

    const { data: res } = await createRiskVocabulary({
      groupId: 1,
      riskDetailsId: formState.riskDetailsId,
      keyword: formState.keyword,
      riskLevel: formState.riskLevel,
      matchType: formState.matchType,
    })

    if (!isSuccessResponse(res) || !res.data) {
      throw new Error(res.message || '新增特征词失败')
    }

    selectedRiskDetailId.value = formState.riskDetailsId
    currentPage.value = 1
    modalVisible.value = false
    message.success('特征词新增成功')
    await fetchVocabularyPage()
  }
  catch (error) {
    if (error instanceof Error)
      message.error(error.message)
  }
  finally {
    modalLoading.value = false
  }
}

function handleDelete(record: RiskVocabularyKeyword) {
  Modal.confirm({
    title: '确认删除特征词',
    content: `确定删除“${record.keyword}”吗？删除后将同步后端风险词库数据。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      deletingId.value = record.id
      try {
        const shouldMovePrevPage = keywords.value.length === 1 && currentPage.value > 1
        const { data: res } = await deleteRiskVocabulary(record.id)

        if (!isSuccessResponse(res)) {
          throw new Error(res.message || '删除特征词失败')
        }

        if (shouldMovePrevPage)
          currentPage.value -= 1

        message.success('特征词删除成功')
        await fetchVocabularyPage()
      }
      catch (error) {
        message.error(error instanceof Error ? error.message : '删除特征词失败')
      }
      finally {
        deletingId.value = null
      }
    },
  })
}

async function handlePushToRedis() {
  syncLoading.value = true
  try {
    const { data: res } = await syncRiskVocabularyToRedis()

    if (!isSuccessResponse(res)) {
      throw new Error(res.message || '推送 Redis 失败')
    }

    message.success(res.data || res.message || '推送 Redis 成功')
    await fetchVocabularyPage()
  }
  catch (error) {
    message.error(error instanceof Error ? error.message : '推送 Redis 失败')
  }
  finally {
    syncLoading.value = false
  }
}

function getRiskLevelLabel(value: RiskVocabularyRiskLevel) {
  return value === 1 ? '致命级别' : '疑似级别'
}

function getMatchTypeLabel(value: RiskVocabularyMatchType) {
  return value === 1 ? '精确匹配' : '模糊包含'
}

onMounted(fetchRiskDetails)
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
            维护 AC 自动机与热更字典树使用的高危特征词，按风险小类组织拦截与打标规则。
          </p>
        </div>
      </div>

      <div class="toolbar-card">
        <div class="toolbar-left">
          <a-input-search
            v-model:value="searchKeyword"
            class="search-input"
            allow-clear
            placeholder="搜索当前小类下的特征词"
            @search="handleSearch"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input-search>

          <a-button :disabled="tableLoading" @click="handleResetSearch">
            重置
          </a-button>
        </div>

        <div class="toolbar-actions">
          <a-button :disabled="!selectedRiskDetailId" @click="showModal()">
            <template #icon>
              <PlusOutlined />
            </template>
            新增特征词
          </a-button>
          <a-button
            type="primary"
            danger
            class="redis-button"
            :disabled="!riskDetails.length"
            :loading="syncLoading"
            @click="handlePushToRedis"
          >
            <template #icon>
              <SyncOutlined />
            </template>
            推送到 Redis
          </a-button>
        </div>
      </div>

      <div class="risk-layout">
        <aside class="detail-panel-list">
          <div class="panel-title">
            <ExclamationCircleOutlined />
            风险小类
          </div>
          <a-spin :spinning="riskDetailsLoading">
            <div v-if="displayRiskDetails.length" class="detail-list">
              <button
                v-for="detail in displayRiskDetails"
                :key="detail.id"
                type="button"
                class="detail-item"
                :class="{ active: detail.id === selectedRiskDetailId }"
                @click="handleRiskDetailSelect(detail.id)"
              >
                <span class="detail-name">{{ detail.detailsName }}</span>
                <span class="detail-meta">大类 ID {{ detail.categoryId }} / 小类 ID {{ detail.id }}</span>
                <span class="detail-count">
                  当前页 {{ detail.itemCount }} 词条 / {{ detail.pendingCount }} 待同步
                </span>
              </button>
            </div>
            <a-empty v-else class="detail-empty" description="暂无风险小类" />
          </a-spin>
        </aside>

        <section class="table-card">
          <div class="table-header">
            <div>
              <h3 class="table-title">
                {{ selectedRiskDetailName }}
              </h3>
              <p class="table-desc">
                {{ selectedRiskDetailMeta }}
              </p>
            </div>
            <div class="table-stats">
              <span>当前页 {{ currentPageItemCount }}</span>
              <span>待同步 {{ pendingSyncCount }}</span>
              <span>已同步 {{ syncedCount }}</span>
            </div>
          </div>

          <a-table
            :columns="columns"
            :data-source="keywords"
            :loading="tableLoading"
            :pagination="tablePagination"
            :scroll="{ x: 1120 }"
            row-key="id"
            :locale="{ emptyText: '暂无风险特征词数据' }"
            @change="handleTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'id'">
                <span class="keyword-id">{{ record.id }}</span>
              </template>

              <template v-if="column.key === 'keyword'">
                <span class="keyword-text">{{ record.keyword }}</span>
              </template>

              <template v-if="column.key === 'riskDetailsId'">
                <span class="details-id">{{ record.riskDetailsId }}</span>
              </template>

              <template v-if="column.key === 'riskLevel'">
                <a-tag :color="record.riskLevel === 1 ? 'red' : 'orange'">
                  {{ getRiskLevelLabel(record.riskLevel) }}
                </a-tag>
              </template>

              <template v-if="column.key === 'matchType'">
                <a-tag color="blue">
                  {{ getMatchTypeLabel(record.matchType) }}
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
                <a-button size="small" type="link" @click="showModal(record as RiskVocabularyKeyword)">
                  <template #icon>
                    <EditOutlined />
                  </template>
                  编辑
                </a-button>
                <a-button
                  size="small"
                  type="link"
                  danger
                  :loading="deletingId === record.id"
                  @click="handleDelete(record as RiskVocabularyKeyword)"
                >
                  <template #icon>
                    <DeleteOutlined />
                  </template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </section>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="editingRecord ? '编辑特征词' : '新增特征词'"
      width="620px"
      :confirm-loading="modalLoading"
      :destroy-on-close="true"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleModalOk"
      @cancel="closeModal"
    >
      <a-form ref="featureForm" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item name="keyword" label="特征词">
          <a-input v-model:value="formState.keyword" placeholder="请输入特征词，例如 CVE-2021-44228" />
        </a-form-item>

        <a-form-item name="riskDetailsId" label="所属小类 ID">
          <a-select
            v-model:value="formState.riskDetailsId"
            class="full-input"
            placeholder="请选择所属小类"
            show-search
            option-filter-prop="label"
          >
            <a-select-option
              v-for="detail in riskDetails"
              :key="detail.id"
              :value="detail.id"
              :label="`${detail.detailsName} ${detail.id}`"
            >
              {{ detail.detailsName }} / ID {{ detail.id }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <div class="form-grid">
          <a-form-item name="riskLevel" label="风险等级">
            <a-select v-model:value="formState.riskLevel">
              <a-select-option :value="1">
                致命级别
              </a-select-option>
              <a-select-option :value="2">
                疑似级别
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item name="matchType" label="匹配模式">
            <a-select v-model:value="formState.matchType">
              <a-select-option :value="1">
                精确匹配
              </a-select-option>
              <a-select-option :value="2">
                模糊包含
              </a-select-option>
            </a-select>
          </a-form-item>
        </div>

        <a-form-item v-if="editingRecord" label="同步状态">
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
.detail-panel-list,
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

.search-input {
  width: 300px;
}

.redis-button {
  box-shadow: 0 6px 14px rgba(255, 77, 79, 0.18);
}

.risk-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.detail-panel-list {
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

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 720px;
  overflow-y: auto;
  padding-right: 2px;
}

.detail-item {
  width: 100%;
  padding: 12px;
  text-align: left;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.detail-item:hover,
.detail-item.active {
  background: #f0f7ff;
  border-color: #91caff;
}

.detail-name {
  display: block;
  color: #262626;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.detail-meta {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
  margin-bottom: 6px;
}

.detail-count {
  color: #1677ff;
  font-size: 12px;
  font-weight: 600;
}

.detail-empty {
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

.keyword-id {
  color: #1677ff;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
}

.keyword-text {
  color: #262626;
  font-weight: 500;
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
  .toolbar-card,
  .table-header {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input,
  .toolbar-card :deep(.ant-btn) {
    width: 100%;
  }

  .risk-layout {
    grid-template-columns: 1fr;
  }

  .detail-panel-list {
    overflow-x: auto;
  }

  .detail-list {
    min-width: 720px;
    max-height: none;
    flex-direction: row;
    overflow-y: visible;
  }

  .detail-item {
    min-width: 220px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
