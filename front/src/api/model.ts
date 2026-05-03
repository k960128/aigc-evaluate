import type { Result } from '../types/api'
import type { CreateModelReq, ModelInfo, TestConnectivityReq, ConnectivityResult, UpdateModelReq } from '../types/model'
import request from './request'

/** 查询模型列表 */
export function getModelList() {
  return request.get<Result<ModelInfo[]>>('/source/model/list')
}

/** 查询单个模型 */
export function getModel(id: number) {
  return request.get<Result<ModelInfo>>('/source/model/get', { params: { id } })
}

/** 创建模型 */
export function createModel(data: CreateModelReq) {
  return request.post<Result<ModelInfo>>('/source/model/create', data)
}

/** 更新模型 */
export function updateModel(data: UpdateModelReq) {
  return request.put<Result<ModelInfo>>('/source/model/update', data)
}

/** 删除模型 */
export function deleteModel(id: number) {
  return request.delete<Result<null>>('/source/model/delete', { params: { id } })
}

/** 连通性测试 */
export function testConnectivity(data: TestConnectivityReq) {
  return request.post<Result<ConnectivityResult>>('/source/model/testConnectivity', data)
}
