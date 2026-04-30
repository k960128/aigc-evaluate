import type { Result } from '../types/api'
import { message } from 'ant-design-vue'
import axios from 'axios'

const request = axios.create({
  baseURL: '/api/aigc-eval',
  timeout: 30000,
})

request.interceptors.request.use(
  (config) => {
    return config
  },
  error => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const data = response.data as Result
    if (data.code !== '0' && data.code !== '200') {
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  (error) => {
    message.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default request
