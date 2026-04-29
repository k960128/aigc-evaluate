import type { CreateEvalTaskRequest, EvalTask, EvalTaskDetail } from '../types/eval-task'
import { message } from 'ant-design-vue'
import { defineStore } from 'pinia'
import { ref } from 'vue'

import { createEvalTask, getEvalTaskList, getEvalTaskProgress, submitEvalTask } from '../api/eval-task'

export const useEvalTaskStore = defineStore('eval-task', () => {
  const taskList = ref<EvalTask[]>([])
  const total = ref(0)
  const loading = ref(false)
  const currentDetail = ref<EvalTaskDetail | null>(null)

  async function fetchTaskList(params?: { current?: number, size?: number, taskName?: string, status?: number }) {
    loading.value = true
    try {
      const { data } = await getEvalTaskList(params)
      if (data.data) {
        taskList.value = data.data.records
        total.value = data.data.total
      }
    }
    finally {
      loading.value = false
    }
  }

  async function handleCreateTask(req: CreateEvalTaskRequest) {
    await createEvalTask(req)
    message.success('任务创建成功')
  }

  async function handleSubmitTask(taskId: number) {
    await submitEvalTask(taskId)
    message.success('任务已提交')
  }

  async function fetchTaskProgress(taskId: number) {
    const { data } = await getEvalTaskProgress(taskId)
    if (data.data)
      currentDetail.value = data.data
  }

  return {
    taskList,
    total,
    loading,
    currentDetail,
    fetchTaskList,
    handleCreateTask,
    handleSubmitTask,
    fetchTaskProgress,
  }
})
