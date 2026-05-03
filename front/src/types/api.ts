export interface Result<T = any> {
  code: string
  message?: string
  requestId?: string
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
