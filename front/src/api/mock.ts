import type { EvalTask, EvalTaskDetail, ModelInfo, DataSet } from '../types/eval-task'
import { TaskStatus } from '../types/eval-task'
import type { PageResult } from '../types/api'

export const mockModels: any[] = [
  { id: 1, model: 'gpt-4o-2024', baseUrl: 'https://api.openai.com/v1', apiKey: 'sk-xxx', manufacturerCode: 'OPENAI', modelDescribe: 'GPT-4o 2024', maxThreadSize: 10, originName: 'gpt-4o-2024', maxCompletionTokens: 16384, stream: true, status: true, config: null, version: 1, createTime: '2024-01-15 10:00', updateTime: '2024-01-15 10:00' },
  { id: 2, model: 'qwen-max', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKey: 'sk-xxx', manufacturerCode: 'QWEN', modelDescribe: 'Qwen Max', maxThreadSize: 10, originName: 'qwen-max', maxCompletionTokens: 8192, stream: true, status: true, config: null, version: 1, createTime: '2024-01-20 14:30', updateTime: '2024-01-20 14:30' },
  { id: 3, model: 'claude-3-5-sonnet', baseUrl: 'https://api.anthropic.com/v1', apiKey: 'sk-xxx', manufacturerCode: 'ANTHROPIC', modelDescribe: 'Claude 3.5 Sonnet', maxThreadSize: 10, originName: 'claude-3-5-sonnet', maxCompletionTokens: 204800, stream: true, status: true, config: null, version: 1, createTime: '2024-02-01 09:00', updateTime: '2024-02-01 09:00' },
  { id: 4, model: 'glm-4', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', apiKey: 'sk-xxx', manufacturerCode: 'GLM', modelDescribe: 'GLM-4', maxThreadSize: 10, originName: 'glm-4', maxCompletionTokens: 8192, stream: true, status: true, config: null, version: 1, createTime: '2024-02-10 11:00', updateTime: '2024-02-10 11:00' },
  { id: 5, model: 'llama-3-70b', baseUrl: 'http://localhost:8080/v1', apiKey: 'sk-xxx', manufacturerCode: 'META', modelDescribe: 'Llama-3-70b', maxThreadSize: 10, originName: 'llama-3-70b', maxCompletionTokens: 8192, stream: true, status: true, config: null, version: 1, createTime: '2024-02-15 16:00', updateTime: '2024-02-15 16:00' },
]

export const mockDatasets: DataSet[] = [
  { id: 1, datasetName: '平台官方全维度基线题库 (v2.0)', description: '覆盖越狱攻击、有毒内容、隐私泄露、偏见歧视等核心维度', sampleCount: 15000, createTime: '2024-01-01 00:00', updateTime: '2024-03-01 00:00' },
  { id: 2, datasetName: '自定义全维度混合题库', description: '用户自定义评测题库', sampleCount: 5000, createTime: '2024-02-01 00:00', updateTime: '2024-02-15 00:00' },
  { id: 3, datasetName: '越狱攻击专项题库', description: '专注于越狱攻击测试', sampleCount: 4000, createTime: '2024-01-15 00:00', updateTime: '2024-01-15 00:00' },
  { id: 4, datasetName: '偏见与歧视测试题库', description: '检测模型偏见歧视问题', sampleCount: 3000, createTime: '2024-01-20 00:00', updateTime: '2024-01-20 00:00' },
  { id: 5, datasetName: '隐私泄露测试题库', description: '检测模型隐私泄露问题', sampleCount: 2000, createTime: '2024-02-01 00:00', updateTime: '2024-02-01 00:00' },
]

export const mockTasks: EvalTask[] = [
  { id: 1, taskName: 'Qwen 模型日常合规巡检', modelId: 2, datasetId: 1, status: TaskStatus.RUNNING, createTime: '2024-05-20 14:30', updateTime: '2024-05-20 14:35' },
  { id: 2, taskName: 'GPT-4o 准入基线评估', modelId: 1, datasetId: 1, status: TaskStatus.COMPLETED, createTime: '2024-05-19 09:15', updateTime: '2024-05-19 11:30' },
  { id: 3, taskName: '本地 Llama-3 安全摸底', modelId: 5, datasetId: 3, status: TaskStatus.ERROR, createTime: '2024-05-18 16:20', updateTime: '2024-05-18 17:45' },
  { id: 4, taskName: 'Claude 3 偏见评估', modelId: 3, datasetId: 4, status: TaskStatus.COMPLETED, createTime: '2024-05-17 10:00', updateTime: '2024-05-17 12:00' },
  { id: 5, taskName: 'GLM-4 隐私泄露测试', modelId: 4, datasetId: 5, status: TaskStatus.RUNNING, createTime: '2024-05-16 14:45', updateTime: '2024-05-16 15:00' },
]

export const mockTaskDetails: Record<number, EvalTaskDetail> = {
  1: {
    id: 1,
    taskId: 1,
    serialNo: 1,
    taskName: 'Qwen 模型日常合规巡检',
    modelId: 2,
    datasetId: 1,
    status: TaskStatus.RUNNING,
    totalCount: 15000,
    finishedCount: 6750,
    failedCount: 234,
    tokenUsage: 1250000,
    createTime: '2024-05-20 14:30',
    updateTime: '2024-05-20 14:35',
    startTime: '2024-05-20 14:31',
  },
  2: {
    id: 2,
    taskId: 2,
    serialNo: 2,
    taskName: 'GPT-4o 准入基线评估',
    modelId: 1,
    datasetId: 1,
    status: TaskStatus.COMPLETED,
    totalCount: 15000,
    finishedCount: 15000,
    failedCount: 270,
    tokenUsage: 3200000,
    createTime: '2024-05-19 09:15',
    updateTime: '2024-05-19 11:30',
    startTime: '2024-05-19 09:16',
    endTime: '2024-05-19 11:28',
  },
  3: {
    id: 3,
    taskId: 3,
    serialNo: 3,
    taskName: '本地 Llama-3 安全摸底',
    modelId: 5,
    datasetId: 3,
    status: TaskStatus.ERROR,
    totalCount: 4000,
    finishedCount: 2680,
    failedCount: 1320,
    tokenUsage: 850000,
    createTime: '2024-05-18 16:20',
    updateTime: '2024-05-18 17:45',
    startTime: '2024-05-18 16:21',
    endTime: '2024-05-18 17:45',
  },
  4: {
    id: 4,
    taskId: 4,
    serialNo: 4,
    taskName: 'Claude 3 偏见评估',
    modelId: 3,
    datasetId: 4,
    status: TaskStatus.COMPLETED,
    totalCount: 3000,
    finishedCount: 3000,
    failedCount: 270,
    tokenUsage: 650000,
    createTime: '2024-05-17 10:00',
    updateTime: '2024-05-17 12:00',
    startTime: '2024-05-17 10:01',
    endTime: '2024-05-17 11:58',
  },
  5: {
    id: 5,
    taskId: 5,
    serialNo: 5,
    taskName: 'GLM-4 隐私泄露测试',
    modelId: 4,
    datasetId: 5,
    status: TaskStatus.RUNNING,
    totalCount: 2000,
    finishedCount: 460,
    failedCount: 52,
    tokenUsage: 180000,
    createTime: '2024-05-16 14:45',
    updateTime: '2024-05-16 15:00',
    startTime: '2024-05-16 14:46',
  },
}

export function mockGetTaskList(params?: { current?: number; size?: number; taskName?: string; status?: number }) {
  const page = params?.current || 1
  const pageSize = params?.size || 10
  let filtered = [...mockTasks]

  if (params?.taskName !== undefined) {
    const taskName = params.taskName as string
    filtered = filtered.filter(t => t.taskName.includes(taskName))
  }

  if (params?.status !== undefined && params.status !== null) {
    filtered = filtered.filter(t => t.status === params.status)
  }

  const start = (page - 1) * pageSize
  const end = start + pageSize

  const result: PageResult<EvalTask> = {
    records: filtered.slice(start, end),
    total: filtered.length,
    size: pageSize,
    current: page,
    pages: Math.ceil(filtered.length / pageSize),
  }

  return Promise.resolve({
    code: '0',
    message: 'success',
    data: result,
  })
}

export function mockGetTaskDetail(taskId: number) {
  const detail = mockTaskDetails[taskId]
  return Promise.resolve({
    code: '0',
    message: 'success',
    data: detail || null,
  })
}

export function mockCreateTask(data: { taskName: string; modelId: number; datasetId: number }) {
  const newTask: EvalTask = {
    id: Date.now(),
    taskName: data.taskName,
    modelId: data.modelId,
    datasetId: data.datasetId,
    status: TaskStatus.CREATING,
    createTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
    updateTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
  }

  mockTasks.push(newTask)

  return Promise.resolve({
    code: '0',
    message: '任务创建成功',
    data: null,
  })
}

export function mockGetModels() {
  return Promise.resolve({
    code: '0',
    message: 'success',
    data: mockModels,
  })
}

export function mockGetDatasets() {
  return Promise.resolve({
    code: '0',
    message: 'success',
    data: mockDatasets,
  })
}

export const mockManufacturers: any[] = [
  { id: 1, manufacturerName: 'OpenAI', manufacturerCode: 'OPENAI', defaultBaseUrl: 'https://api.openai.com/v1', describe: 'OpenAI API', enable: true, createTime: '2024-01-01 00:00', updateTime: '2024-01-01 00:00' },
  { id: 2, manufacturerName: '阿里云灵积', manufacturerCode: 'QWEN', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', describe: '阿里云Qwen模型服务', enable: true, createTime: '2024-01-05 00:00', updateTime: '2024-01-05 00:00' },
  { id: 3, manufacturerName: 'Anthropic', manufacturerCode: 'ANTHROPIC', defaultBaseUrl: 'https://api.anthropic.com/v1', describe: 'Anthropic Claude模型', enable: true, createTime: '2024-01-10 00:00', updateTime: '2024-01-10 00:00' },
  { id: 4, manufacturerName: '智谱 AI', manufacturerCode: 'GLM', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4', describe: '智谱GLM模型服务', enable: true, createTime: '2024-01-15 00:00', updateTime: '2024-01-15 00:00' },
  { id: 5, manufacturerName: 'Moonshot', manufacturerCode: 'KIMI', defaultBaseUrl: 'https://api.moonshot.cn/v1', describe: 'Moonshot Kimi模型', enable: true, createTime: '2024-01-20 00:00', updateTime: '2024-01-20 00:00' },
  { id: 6, manufacturerName: '讯飞星火', manufacturerCode: 'SPARK', defaultBaseUrl: '', describe: '讯飞星火大模型', enable: false, createTime: '2024-02-01 00:00', updateTime: '2024-02-01 00:00' },
]

export function mockGetManufacturerList() {
  return Promise.resolve({
    code: '0',
    message: 'success',
    data: mockManufacturers,
  })
}

export function mockCreateManufacturer(data: { manufacturerName: string; manufacturerCode: string; defaultBaseUrl?: string; enable?: boolean }) {
  const newManufacturer = {
    id: Date.now(),
    manufacturerName: data.manufacturerName,
    manufacturerCode: data.manufacturerCode,
    defaultBaseUrl: data.defaultBaseUrl || '',
    describe: '',
    enable: data.enable ?? true,
    createTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
    updateTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
  }
  mockManufacturers.push(newManufacturer)
  return Promise.resolve({
    code: '0',
    message: '创建成功',
    data: newManufacturer,
  })
}

export function mockUpdateManufacturer(data: { id: number; manufacturerName?: string; manufacturerCode?: string; defaultBaseUrl?: string; enable?: boolean }) {
  const index = mockManufacturers.findIndex(m => m.id === data.id)
  if (index !== -1) {
    mockManufacturers[index] = {
      ...mockManufacturers[index],
      ...data,
      updateTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
    }
    return Promise.resolve({
      code: '0',
      message: '更新成功',
      data: mockManufacturers[index],
    })
  }
  return Promise.resolve({
    code: '1',
    message: '厂商不存在',
    data: null,
  })
}

export function mockDeleteManufacturer(id: number) {
  const index = mockManufacturers.findIndex(m => m.id === id)
  if (index !== -1) {
    mockManufacturers.splice(index, 1)
    return Promise.resolve({
      code: '0',
      message: '删除成功',
      data: null,
    })
  }
  return Promise.resolve({
    code: '1',
    message: '厂商不存在',
    data: null,
  })
}
