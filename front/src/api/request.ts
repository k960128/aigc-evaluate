import type { Result } from '../types/api'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { AUTH_TOKEN_HEADER_NAME, clearAuthToken, getAuthToken } from '../utils/auth'

const request = axios.create({
  baseURL: '/api/aigc-eval',
  timeout: 30000,
})

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

function isAuthErrorMessage(errorMessage?: string | null) {
  if (!errorMessage) {
    return false
  }

  return ['未登录', '无效', '失效', '过期', 'token', 'Token', 'satoken'].some(keyword => errorMessage.includes(keyword))
}

request.interceptors.request.use(
  (config) => {
    const token = getAuthToken()
    if (token) {
      config.headers[AUTH_TOKEN_HEADER_NAME] = token
    }
    return config
  },
  error => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const data = response.data as Result
    if (String(data.code) !== '0' && String(data.code) !== '200') {
      if (isAuthErrorMessage(data.message)) {
        clearAuthToken()
        redirectToLogin()
      }
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const responseData = error.response?.data as Partial<Result> | undefined
    if (
      error.response?.status === 401
      || error.response?.status === 403
      || isAuthErrorMessage(responseData?.message)
    ) {
      clearAuthToken()
      redirectToLogin()
    }
    message.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default request
