export interface Result<T = any> {
  code: string
  message?: string | null
  requestId?: string | null
  success?: boolean
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
