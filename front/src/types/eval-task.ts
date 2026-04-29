export interface EvalTask {
  id: number
  taskName: string
  modelId: number
  datasetId: number
  status: TaskStatus
  createTime: string
  updateTime: string
  deleted?: boolean
}

export interface EvalTaskDetail {
  id: number
  taskId: number
  serialNo: number
  taskName: string
  modelId: number
  datasetId: number
  status: TaskStatus
  totalCount: number
  finishedCount: number
  failedCount: number
  tokenUsage: number
  createTime: string
  updateTime: string
  startTime?: string
  endTime?: string
}

export interface ModelInfo {
  id: number
  modelName: string
  manufacturer: string
  apiKey?: string
  baseUrl?: string
  createTime: string
  updateTime: string
}

export interface DataSet {
  id: number
  datasetName: string
  description?: string
  sampleCount: number
  createTime: string
  updateTime: string
}

export enum TaskStatus {
  CREATING = 0,
  INITIALIZING = 1,
  READY = 2,
  RUNNING = 3,
  COMPLETED = 4,
  ERROR = 5,
  STOPPED = 6,
}

export const TaskStatusMap: Record<TaskStatus, string> = {
  [TaskStatus.CREATING]: '创建中',
  [TaskStatus.INITIALIZING]: '初始化中',
  [TaskStatus.READY]: '就绪',
  [TaskStatus.RUNNING]: '进行中',
  [TaskStatus.COMPLETED]: '已完成',
  [TaskStatus.ERROR]: '异常',
  [TaskStatus.STOPPED]: '已停止',
}

export const TaskStatusColorMap: Record<TaskStatus, string> = {
  [TaskStatus.CREATING]: 'default',
  [TaskStatus.INITIALIZING]: 'processing',
  [TaskStatus.READY]: 'warning',
  [TaskStatus.RUNNING]: 'processing',
  [TaskStatus.COMPLETED]: 'success',
  [TaskStatus.ERROR]: 'error',
  [TaskStatus.STOPPED]: 'default',
}

export interface CreateEvalTaskRequest {
  taskName: string
  modelId: number
  datasetId: number
}
