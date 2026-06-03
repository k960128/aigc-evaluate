import type { Result } from '../types/api'
import request from './request'

export type RiskApiStatus = 0 | 1

export interface RiskCategory {
  id: number
  categoryName: string
  description?: string
  sortOrder?: number
  status: RiskApiStatus
  createTime?: string
  updateTime?: string
  deleted?: boolean
}

export interface RiskDetail {
  id: number
  categoryId: number
  detailsName: string
  sortOrder?: number
  status: RiskApiStatus
  createTime?: string
  updateTime?: string
  deleted?: boolean
}

export interface RiskCategoryListParams {
  status?: RiskApiStatus
}

export interface RiskDetailListParams {
  categoryId?: number
  status?: RiskApiStatus
}

export interface UpdateRiskDetailReq {
  id: number
  categoryId?: number
  detailsName?: string
  sortOrder?: number
  status?: RiskApiStatus
}

export function getRiskCategoryList(params?: RiskCategoryListParams) {
  return request.get<Result<RiskCategory[]>>('/risk/category/list', { params })
}

export function getRiskDetailList(params?: RiskDetailListParams) {
  return request.get<Result<RiskDetail[]>>('/risk/category/details/list', { params })
}

export function updateRiskDetail(data: UpdateRiskDetailReq) {
  return request.put<Result<boolean>>('/risk/category/details', data)
}

export function deleteRiskDetail(id: number) {
  return request.delete<Result<boolean>>(`/risk/category/details/${id}`)
}
