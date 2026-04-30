import type { PageResult, Result } from '../types/api'
import type { CreateEvalTaskRequest, EvalTask, EvalTaskDetail } from '../types/eval-task'
import request from './request'

export function createEvalTask(data: CreateEvalTaskRequest) {
  return request.post<Result<void>>('/eval-task/create', data)
}

export function submitEvalTask(taskId: number) {
  return request.get<Result<void>>('/eval-task/submit', { params: { taskId } })
}

export function getEvalTaskProgress(taskId: number) {
  return request.get<Result<EvalTaskDetail>>('/eval-task/progress', { params: { taskId } })
}

export function getEvalTaskList(params?: { current?: number, size?: number, taskName?: string, status?: number }) {
  return request.get<Result<PageResult<EvalTask>>>('/eval-task/list', { params })
}
