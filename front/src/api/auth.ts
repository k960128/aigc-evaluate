import type { Result } from '../types/api'
import request from './request'

export interface LoginRequest {
  username: string
  password: string
}

export interface SaTokenInfo {
  tokenName: string
  tokenValue: string
  isLogin: boolean
  loginId: string | number
  loginType: string
  tokenTimeout: number
  sessionTimeout: number
  tokenSessionTimeout: number
  tokenActiveTimeout: number
  loginDeviceType?: string
  tag?: string
}

export function login(data: LoginRequest) {
  return request.post<Result<SaTokenInfo>>('/auth/login', data)
}

export function logout() {
  return request.post<Result<void>>('/auth/logout')
}
