import type { Result } from '../types/api'
import request from './request'

export interface Manufacturer {
  id?: number
  manufacturerName: string
  manufacturerCode: string
  defaultBaseUrl?: string
  describe?: string
  icon?: string
  enable?: boolean
  createTime?: string
  updateTime?: string
}

export interface CreateManufacturerReq {
  manufacturerName: string
  manufacturerCode: string
  defaultBaseUrl?: string
  describe?: string
  icon?: string
  enable?: boolean
}

export interface UpdateManufacturerReq {
  id: number
  manufacturerName?: string
  manufacturerCode?: string
  defaultBaseUrl?: string
  describe?: string
  icon?: string
  enable?: boolean
}

/** 查询厂商列表 */
export function getManufacturerList() {
  return request.get<Result<Manufacturer[]>>('/source/manufacturer/list')
}

/** 查询单个厂商 */
export function getManufacturer(id: number) {
  return request.get<Result<Manufacturer>>('/source/manufacturer/get', { params: { id } })
}

/** 创建厂商 */
export function createManufacturer(data: CreateManufacturerReq) {
  return request.post<Result<Manufacturer>>('/source/manufacturer/create', data)
}

/** 更新厂商 */
export function updateManufacturer(data: UpdateManufacturerReq) {
  return request.put<Result<Manufacturer>>('/source/manufacturer/update', data)
}

/** 删除厂商 */
export function deleteManufacturer(id: number) {
  return request.delete<Result<null>>('/source/manufacturer/delete', { params: { id } })
}
