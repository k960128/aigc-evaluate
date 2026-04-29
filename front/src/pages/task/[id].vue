<script setup lang="ts">
import type { EvalTaskDetail, TaskStatus } from '../../types/eval-task'
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useEvalTaskStore } from '../../stores/eval-task'
import { TaskStatusColorMap, TaskStatusMap } from '../../types/eval-task'

const route = useRoute()
const store = useEvalTaskStore()
const taskId = Number(route.params.id)

let timer: ReturnType<typeof setInterval> | null = null

const detail = ref<EvalTaskDetail | null>(null)

async function loadProgress() {
  await store.fetchTaskProgress(taskId)
  detail.value = store.currentDetail
}

function getProgressPercent(detail: EvalTaskDetail): number {
  if (!detail.totalCount || detail.totalCount === 0)
    return 0
  return Math.round((detail.finishedCount / detail.totalCount) * 100)
}

function isTaskRunning(status: TaskStatus): boolean {
  return status === 0 || status === 1 || status === 3
}

onMounted(() => {
  loadProgress()
  // Poll for progress updates when task is running
  timer = setInterval(() => {
    if (detail.value && !isTaskRunning(detail.value.status as TaskStatus)) {
      if (timer)
        clearInterval(timer)
      return
    }
    loadProgress()
  }, 5000)
})

onUnmounted(() => {
  if (timer)
    clearInterval(timer)
})
</script>

<template>
  <div>
    <div style="display: flex; align-items: center; margin-bottom: 24px;">
      <h2 style="margin: 0;">
        任务进度
      </h2>
      <a-button type="link" @click="$router.push('/task')">
        返回列表
      </a-button>
    </div>

    <a-spin v-if="!detail" tip="加载中..." />

    <template v-else>
      <a-descriptions bordered :column="2">
        <a-descriptions-item label="任务名称">
          {{ detail.taskName }}
        </a-descriptions-item>
        <a-descriptions-item label="流水号">
          {{ detail.serialNo }}
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="TaskStatusColorMap[detail.status as TaskStatus]">
            {{ TaskStatusMap[detail.status as TaskStatus] }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="模型ID">
          {{ detail.modelId }}
        </a-descriptions-item>
        <a-descriptions-item label="数据集ID">
          {{ detail.datasetId }}
        </a-descriptions-item>
        <a-descriptions-item label="Token用量">
          {{ detail.tokenUsage ?? 0 }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">
          {{ detail.createTime }}
        </a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ detail.updateTime }}
        </a-descriptions-item>
      </a-descriptions>

      <a-divider>执行进度</a-divider>

      <div style="max-width: 600px;">
        <a-progress
          :percent="getProgressPercent(detail)"
          :status="detail.status === 5 ? 'exception' : detail.status === 4 ? 'success' : 'active'"
        />
        <div style="display: flex; justify-content: space-between; margin-top: 8px; color: #666;">
          <span>总计: {{ detail.totalCount }}</span>
          <span>已完成: {{ detail.finishedCount ?? 0 }}</span>
          <span>失败: {{ detail.failedCount ?? 0 }}</span>
        </div>
      </div>
    </template>
  </div>
</template>
