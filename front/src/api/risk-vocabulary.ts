import type { PageResult, Result } from '../types/api'
import request from './request'

export type RiskVocabularyRiskLevel = 1 | 2
export type RiskVocabularyMatchType = 1 | 2
export type RiskVocabularySyncStatus = 0 | 1

export interface RiskVocabularyKeyword {
  id: number
  groupId: number
  riskDetailsId: number
  keyword: string
  riskLevel: RiskVocabularyRiskLevel
  matchType: RiskVocabularyMatchType
  syncStatus: boolean
  creator?: string
  updater?: string
  createTime?: string
  updateTime?: string
  deleted?: boolean
}

export interface RiskVocabularyPageParams {
  current?: number
  size?: number
  riskDetailsId?: number
  groupId?: number
  keyword?: string
  riskLevel?: RiskVocabularyRiskLevel
  matchType?: RiskVocabularyMatchType
  syncStatus?: boolean
}

export interface CreateRiskVocabularyReq {
  groupId: number
  riskDetailsId: number
  keyword: string
  riskLevel?: RiskVocabularyRiskLevel
  matchType?: RiskVocabularyMatchType
  syncStatus?: RiskVocabularySyncStatus
}

export interface UpdateRiskVocabularyReq extends CreateRiskVocabularyReq {
  id: number
  updater?: string
}

export function getRiskVocabularyPage(params?: RiskVocabularyPageParams) {
  return request.post<Result<PageResult<RiskVocabularyKeyword>>>('/risk/vocabularies/keyword/page', params)
}

export function createRiskVocabulary(data: CreateRiskVocabularyReq) {
  return request.post<Result<RiskVocabularyKeyword>>('/risk/vocabularies/keyword/create', data)
}

export function updateRiskVocabulary(data: UpdateRiskVocabularyReq) {
  return request.put<Result<RiskVocabularyKeyword | null>>('/risk/vocabularies/keyword/update', data)
}

export function syncRiskVocabularyToPublish() {
  return request.post<Result<string>>('/risk/vocabularies/keyword/version/publish')
}

export function deleteRiskVocabulary(id: number) {
  return request.delete<Result<null>>('/risk/vocabularies/keyword/delete', { params: { id } })
}
