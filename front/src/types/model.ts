export interface ModelInfo {
  id: number
  model: string
  baseUrl: string
  apiKey: string
  manufacturerCode: string
  modelDescribe: string
  maxThreadSize: number
  originName: string
  maxCompletionTokens: number
  stream: boolean
  config: Record<string, any> | null
  version: number
  createTime: string
  updateTime: string
}

export interface CreateModelReq {
  model: string
  baseUrl: string
  apiKey: string
  manufacturerCode: string
  modelDescribe?: string
  maxThreadSize?: number
  originName?: string
  maxCompletionTokens?: number
  stream?: boolean
  config?: Record<string, any> | null
}

export interface UpdateModelReq {
  id: number
  model?: string
  baseUrl?: string
  apiKey?: string
  manufacturerCode?: string
  modelDescribe?: string
  maxThreadSize?: number
  originName?: string
  maxCompletionTokens?: number
  stream?: boolean
  config?: Record<string, any> | null
}

export interface TestConnectivityReq {
  modelId?: number
  model: string
  baseUrl: string
  apiKey: string
  manufacturerCode: string
}

export interface ConnectivityResult {
  result: boolean
  respContent: string
  elapsed: number
}
