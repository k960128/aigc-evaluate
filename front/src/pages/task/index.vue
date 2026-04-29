<script setup lang="ts">
import type { TaskStatus } from '../../types/eval-task'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { h, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useEvalTaskStore } from '../../stores/eval-task'
import { TaskStatusColorMap, TaskStatusMap } from '../../types/eval-task'

const router = useRouter()
const store = useEvalTaskStore()

const searchForm = reactive({
  taskName: '',
  status: undefined as number | undefined,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
})

const columns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
  { title: '模型ID', dataIndex: 'modelId', key: 'modelId' },
  { title: '数据集ID', dataIndex: 'datasetId', key: 'datasetId' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
  { title: '操作', dataIndex: 'action', key: 'action', width: 200 },
]

function loadList() {
  store.fetchTaskList({
    current: pagination.current,
    size: pagination.pageSize,
    taskName: searchForm.taskName || undefined,
    status: searchForm.status,
  })
}

function handleSearch() {
  pagination.current = 1
  loadList()
}

function handleReset() {
  searchForm.taskName = ''
  searchForm.status = undefined
  pagination.current = 1
  loadList()
}

function handleTableChange(pag: { current?: number, pageSize?: number }) {
  if (pag.current)
    pagination.current = pag.current
  if (pag.pageSize)
    pagination.pageSize = pag.pageSize
  loadList()
}

function handleCreate() {
  router.push('/task/create')
}

function handleSubmit(taskId: number) {
  Modal.confirm({
    title: '确认提交',
    icon: () => h(ExclamationCircleOutlined),
    content: '确认发起此评测任务？',
    async onOk() {
      await store.handleSubmitTask(taskId)
      loadList()
    },
  })
}

function handleViewProgress(taskId: number) {
  router.push(`/task/${taskId}`)
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div>
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="任务名称">
          <a-input v-model:value="searchForm.taskName" placeholder="请输入任务名称" allow-clear />
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="searchForm.status"
            placeholder="请选择状态"
            allow-clear
            style="width: 140px;"
          >
            <a-select-option v-for="(label, code) in TaskStatusMap" :key="code" :value="Number(code)">
              {{ label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            查询
          </a-button>
          <a-button style="margin-left: 8px;" @click="handleReset">
            重置
          </a-button>
        </a-form-item>
      </a-form>
      <a-button type="primary" @click="handleCreate">
        创建任务
      </a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="store.taskList"
      :loading="store.loading"
      :pagination="{
        current: pagination.current,
        pageSize: pagination.pageSize,
        total: store.total,
        showSizeChanger: true,
        showTotal: (total: number) => `共 ${total} 条`,
      }"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="TaskStatusColorMap[record.status as TaskStatus]">
            {{ TaskStatusMap[record.status as TaskStatus] }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <a-space>
            <a-button
              type="link"
              size="small"
              :disabled="record.status !== 2"
              @click="handleSubmit(record.id)"
            >
              发起评测
            </a-button>
            <a-button type="link" size="small" @click="handleViewProgress(record.id)">
              查看进度
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>
