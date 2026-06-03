<script setup lang="ts">
import type { Rule } from 'ant-design-vue/es/form'
import type { Component } from 'vue'
import {
  BugOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  EnvironmentOutlined,
  FileSearchOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

interface Task {
  id: string
  name: string
  model: string
  type: string
  baseline: string
  status: 'running' | 'completed' | 'failed'
  progress: number
  createTime: string
}

const router = useRouter()

const searchText = ref('')
const statusFilter = ref('')
const createModalVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref()

const createForm = reactive({
  name: '',
  models: [] as string[],
  baseline: 'official',
  priority: 'normal',
})

const formRules: Record<string, Rule[]> = {
  name: [{ type: 'string', required: true, message: '请输入任务名称', trigger: 'blur' }],
  models: [
    {
      type: 'array',
      required: true,
      message: '请至少选择一个模型',
      trigger: 'change',
    },
  ],
  baseline: [{ type: 'string', required: true, message: '请选择评测题库', trigger: 'change' }],
}

const modelOptions = [
  { label: 'gpt-4o-2024', value: 'gpt-4o-2024' },
  { label: 'qwen-max', value: 'qwen-max' },
  { label: 'Claude 3.5 Sonnet', value: 'Claude 3.5 Sonnet' },
  { label: 'glm-4', value: 'glm-4' },
  { label: 'Llama-3-70b', value: 'Llama-3-70b' },
]

const columns = [
  {
    title: '任务信息',
    key: 'name',
    width: 180,
    fixed: 'left' as const,
  },
  {
    title: '被测模型',
    key: 'model',
    width: 140,
  },
  {
    title: '评测类型/题库',
    key: 'type',
    width: 180,
  },
  {
    title: '状态与进度',
    key: 'status',
    width: 200,
  },
  {
    title: '创建时间',
    key: 'createTime',
    width: 160,
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right' as const,
  },
]

const tasks = ref<Task[]>([
  {
    id: 'TASK-20240520-001',
    name: 'Qwen 模型日常合规巡检',
    model: 'qwen-max',
    type: '全维度安全扫描',
    baseline: 'Official_Baseline_V2.0',
    status: 'running',
    progress: 45,
    createTime: '今天 14:30',
  },
  {
    id: 'TASK-20240519-088',
    name: 'GPT-4o 准入基线评估',
    model: 'gpt-4o-2024',
    type: '全维度安全扫描',
    baseline: 'Official_Baseline_V2.0',
    status: 'completed',
    progress: 100,
    createTime: '昨天 09:15',
  },
  {
    id: 'TASK-20240518-042',
    name: '本地 Llama-3 安全摸底',
    model: 'Llama-3-70b',
    type: '越狱攻击专项测试',
    baseline: 'Jailbreak_Test_Suite_V1.5',
    status: 'failed',
    progress: 67,
    createTime: '2024-05-18 16:20',
  },
  {
    id: 'TASK-20240517-033',
    name: 'Claude 3 偏见评估',
    model: 'Claude 3.5 Sonnet',
    type: '偏见与歧视测试',
    baseline: 'Bias_Test_Suite_V2.1',
    status: 'completed',
    progress: 100,
    createTime: '2024-05-17 10:00',
  },
  {
    id: 'TASK-20240516-021',
    name: 'GLM-4 隐私泄露测试',
    model: 'glm-4',
    type: '隐私泄露专项测试',
    baseline: 'Privacy_Test_Suite_V1.0',
    status: 'running',
    progress: 23,
    createTime: '2024-05-16 14:45',
  },
])

const filteredTasks = computed<Task[]>(() => {
  let result = tasks.value

  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    result = result.filter(
      task =>
        task.id.toLowerCase().includes(keyword)
        || task.name.toLowerCase().includes(keyword),
    )
  }

  if (statusFilter.value)
    result = result.filter(task => task.status === statusFilter.value)

  return result
})

function showCreateModal() {
  createModalVisible.value = true
}

async function handleSubmit() {
  try {
    await createFormRef.value.validate()
    submitting.value = true

    await new Promise(resolve => setTimeout(resolve, 1000))

    message.success('任务创建成功')
    createModalVisible.value = false

    createForm.name = ''
    createForm.models = []
    createForm.baseline = 'official'
    createForm.priority = 'normal'
  }
  catch (error) {
    console.error('表单验证失败:', error)
  }
  finally {
    submitting.value = false
  }
}

function handleViewReport(task: Task) {
  message.info(`查看任务 ${task.id} 的报告`)
  router.push('/report')
}

function handleViewDetail(task: Task) {
  router.push({
    name: 'TaskDetail',
    params: {
      id: task.id,
    },
  })
}

function handleStopTask(task: Task) {
  Modal.confirm({
    title: '确认终止任务',
    content: `确定要终止任务 ${task.name} 吗？`,
    okText: '确认终止',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      message.success('任务已终止')
      task.status = 'failed'
      task.progress = 0
    },
  })
}

function getTypeIcon(type: string): Component {
  if (type.includes('越狱'))
    return ThunderboltOutlined
  if (type.includes('有毒'))
    return BugOutlined
  if (type.includes('隐私'))
    return EnvironmentOutlined
  if (type.includes('偏见'))
    return SafetyCertificateOutlined
  return WarningOutlined
}

function getTypeIconStyle(type: string) {
  if (type.includes('越狱'))
    return { color: '#ff4d4f', marginRight: '4px' }
  if (type.includes('有毒'))
    return { color: '#faad14', marginRight: '4px' }
  if (type.includes('隐私'))
    return { color: '#1677ff', marginRight: '4px' }
  if (type.includes('偏见'))
    return { color: '#722ed1', marginRight: '4px' }
  return { color: '#52c41a', marginRight: '4px' }
}

function getStatusColor(status: string) {
  if (status === 'running')
    return '#1677ff'
  if (status === 'completed')
    return '#52c41a'
  return '#ff4d4f'
}

function getStatusText(status: string) {
  if (status === 'running')
    return '运行中'
  if (status === 'completed')
    return '已完成'
  return '失败'
}
</script>

<template>
  <div class="task-page">
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">
            首页
          </router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>评测任务</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <div class="page-header">
        <div>
          <h2 class="page-title">
            评测任务管理
          </h2>
          <p class="page-desc">
            创建、监控和管理模型安全评测任务
          </p>
        </div>
        <a-button type="primary" @click="showCreateModal">
          <template #icon>
            <PlusOutlined />
          </template>
          创建评测任务
        </a-button>
      </div>

      <div class="filter-section">
        <div class="filter-item">
          <a-input-search
            v-model:value="searchText"
            placeholder="搜索任务 ID 或名称..."
            style="width: 280px"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input-search>
        </div>
        <div class="filter-item">
          <a-select
            v-model:value="statusFilter"
            placeholder="全部状态"
            allow-clear
            style="width: 140px"
          >
            <a-select-option value="">
              全部状态
            </a-select-option>
            <a-select-option value="running">
              运行中
            </a-select-option>
            <a-select-option value="completed">
              已完成
            </a-select-option>
            <a-select-option value="failed">
              失败
            </a-select-option>
          </a-select>
        </div>
      </div>

      <div class="table-section">
        <a-table
          :data-source="filteredTasks"
          :columns="columns"
          :pagination="false"
          :scroll="{ y: 600 }"
          row-key="id"
          :locale="{ emptyText: '暂无任务数据' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="task-name-cell">
                <div class="task-name-main">
                  {{ record.name }}
                </div>
                <div class="task-id">
                  {{ record.id }}
                </div>
              </div>
            </template>

            <template v-if="column.key === 'model'">
              <span class="model-tag">{{ record.model }}</span>
            </template>

            <template v-if="column.key === 'type'">
              <div class="task-type-cell">
                <div class="task-type-main">
                  <component :is="getTypeIcon(record.type)" class="type-icon" :style="getTypeIconStyle(record.type)" />
                  {{ record.type }}
                </div>
                <div class="task-baseline">
                  {{ record.baseline }}
                </div>
              </div>
            </template>

            <template v-if="column.key === 'status'">
              <div class="task-status-cell">
                <div class="status-header">
                  <span class="status-icon" :style="{ color: getStatusColor(record.status) }">
                    <ClockCircleOutlined v-if="record.status === 'running'" />
                    <CheckCircleOutlined v-else-if="record.status === 'completed'" />
                    <CloseCircleOutlined v-else />
                  </span>
                  <span class="status-text">{{ getStatusText(record.status) }}</span>
                  <span class="progress-text">({{ record.progress }}%)</span>
                </div>
                <a-progress :percent="record.progress" :stroke-color="getStatusColor(record.status)" :size="8" :show-info="false" />
              </div>
            </template>

            <template v-if="column.key === 'createTime'">
              <div class="create-time-cell">
                <ClockCircleOutlined class="time-icon" />
                {{ record.createTime }}
              </div>
            </template>

            <template v-if="column.key === 'actions'">
              <div class="task-actions">
                <a-button
                  size="small"
                  type="link"
                  @click="handleViewDetail(record as Task)"
                >
                  <template #icon>
                    <FileSearchOutlined />
                  </template>
                  查看详情
                </a-button>
                <a-button
                  v-if="(record as Task).status === 'running'"
                  size="small"
                  danger
                  type="link"
                  @click="handleStopTask(record as Task)"
                >
                  终止任务
                </a-button>
                <a-button
                  v-else
                  size="small"
                  type="link"
                  @click="handleViewReport(record as Task)"
                >
                  查看报告
                </a-button>
              </div>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal
      v-model:open="createModalVisible"
      title="创建评测任务"
      width="600px"
      :destroy-on-close="true"
      :footer="null"
      @cancel="createModalVisible = false"
    >
      <div class="modal-info">
        <a-alert type="info" show-icon>
          <template #message>
            <strong>执行全维度基线扫描</strong>
          </template>
          <template #description>
            涵盖：<b>越狱攻击、有毒内容、隐私泄露、偏见歧视</b> 等核心维度。预计耗时较长，请耐心等待。
          </template>
        </a-alert>
      </div>

      <a-form
        ref="createFormRef"
        :model="createForm"
        :rules="formRules"
        layout="vertical"
        style="margin-top: 20px"
      >
        <a-form-item name="name" label="任务名称">
          <a-input v-model:value="createForm.name" placeholder="例如：Qwen2.5 准入安全评估" />
        </a-form-item>

        <a-form-item name="models" label="选择被测模型">
          <a-checkbox-group v-model:value="createForm.models" :options="modelOptions" />
        </a-form-item>

        <a-form-item name="baseline" label="基线评测题库">
          <a-select v-model:value="createForm.baseline" placeholder="请选择评测题库">
            <a-select-option value="official">
              平台官方全维度基线题库 (v2.0) - 推荐
            </a-select-option>
            <a-select-option value="custom">
              自定义全维度混合题库
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item name="priority" label="任务优先级">
          <a-radio-group v-model:value="createForm.priority">
            <a-radio value="normal">
              普通
            </a-radio>
            <a-radio value="high">
              高优先级
            </a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>

      <div class="modal-footer">
        <a-button @click="createModalVisible = false">
          取消
        </a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">
          确认创建
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.task-page {
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
  flex-wrap: wrap;
  margin-bottom: 24px;
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

.filter-section {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-item {
  flex-shrink: 0;
}

.table-section {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
}

.table-section :deep(.ant-table) {
  background: #fff;
}

.table-section :deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  color: #262626;
  font-weight: 600;
}

.table-section :deep(.ant-table-tbody > tr:hover > td) {
  background: #fafafa;
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.task-name-main {
  color: #262626;
  font-size: 14px;
  font-weight: 500;
}

.task-id {
  color: #8c8c8c;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
}

.model-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: #f5f5f5;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  color: #595959;
  font-size: 12px;
}

.task-type-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-type-main {
  display: flex;
  align-items: center;
  color: #1677ff;
  font-size: 13px;
  font-weight: 500;
}

.type-icon {
  font-size: 14px;
}

.task-baseline {
  color: #8c8c8c;
  font-size: 11px;
}

.task-status-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status-icon {
  font-size: 14px;
}

.status-text {
  font-weight: 500;
}

.progress-text {
  color: #8c8c8c;
  font-size: 12px;
}

.create-time-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #595959;
  font-size: 13px;
}

.time-icon {
  color: #8c8c8c;
  font-size: 14px;
}

.task-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.task-actions :deep(.ant-btn-link) {
  height: auto;
  padding: 0 4px;
  font-size: 13px;
}

.modal-info {
  margin-bottom: 16px;
}

.modal-info :deep(.ant-alert) {
  border-radius: 8px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.modal-footer :deep(.ant-btn) {
  min-width: 80px;
}

@media (max-width: 768px) {
  .task-page {
    padding: 16px;
  }

  .page-header,
  .filter-section {
    flex-direction: column;
  }

  .filter-item {
    width: 100%;
  }

  .filter-item :deep(*) {
    width: 100%;
  }
}
</style>
