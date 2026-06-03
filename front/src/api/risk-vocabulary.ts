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
  syncStatus: RiskVocabularySyncStatus
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
  keyword?: string
}

export interface CreateRiskVocabularyReq {
  groupId: number
  riskDetailsId: number
  keyword: string
  riskLevel?: RiskVocabularyRiskLevel
  matchType?: RiskVocabularyMatchType
  syncStatus?: RiskVocabularySyncStatus
}

export function getRiskVocabularyPage(params?: RiskVocabularyPageParams) {
  return request.get<Result<PageResult<RiskVocabularyKeyword>>>('/risk/vocabularies/page', { params })
}

export function createRiskVocabulary(data: CreateRiskVocabularyReq) {
  return request.post<Result<boolean>>('/risk/vocabularies', data)
}

export function syncRiskVocabularyToRedis() {
  return request.post<Result<string>>('/risk/vocabularies/sync-to-redis')
}

export function deleteRiskVocabulary(id: number) {
  return request.delete<Result<boolean>>(`/risk/vocabularies/${id}`)
}
