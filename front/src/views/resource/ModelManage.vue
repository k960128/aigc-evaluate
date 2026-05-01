<template>
  <div class="model-page">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">资源中心</router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>模型管理</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <!-- 头部区域 -->
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title">模型管理</h2>
          <p class="page-desc">管理模型版本、API Key 等配置，监控模型在线状态</p>
        </div>
        <div class="header-actions">
          <a-select
            v-model:value="vendorFilter"
            style="width: 160px"
            placeholder="厂商筛选"
            allowClear
          >
            <a-select-option value="">全部</a-select-option>
            <a-select-option v-for="v in vendorOptions" :key="v.code" :value="v.code">
              {{ v.label }}
            </a-select-option>
          </a-select>
          <a-button type="primary" @click="showModelModal()">
            <template #icon><PlusOutlined /></template>
            新增大模型
          </a-button>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" style="text-align: center; padding: 80px 0;">
        <a-spin size="large" />
      </div>

      <!-- 卡片网格 -->
      <div v-else class="model-grid">
        <div
          v-for="item in filteredModels"
          :key="item.id"
          class="model-card card-hover-transition"
        >
          <!-- 卡片顶部状态标签 -->
          <div class="card-status-badge">
            <template v-if="item._online">
              <span class="status-dot status-dot-active online-dot" />
              <span class="status-text online-text">在线(Active)</span>
            </template>
            <template v-else>
              <span class="status-dot offline-dot" />
              <span class="status-text offline-text">离线(Disabled)</span>
            </template>
          </div>

          <!-- 模型基础信息 -->
          <div class="card-body">
            <div class="model-icon-wrap" :style="{ background: getVendorStyle(item.manufacturerCode).bg }">
              <RobotOutlined class="model-icon" :style="{ color: getVendorStyle(item.manufacturerCode).color }" />
            </div>
            <h4 class="model-name">{{ item.model }}</h4>
            <p class="model-vendor">{{ getVendorLabel(item.manufacturerCode) }}</p>
            <div class="model-meta">
              <span class="meta-text">{{ maskApiKey(item.apiKey) }}</span>
              <span class="meta-divider">|</span>
              <span class="meta-text">并发 {{ item.maxThreadSize || '-' }}</span>
              <span class="meta-divider">|</span>
              <span class="model-text">词元 {{ item.maxCompletionTokens || '-' }}</span>
            </div>
            <div class="model-base-url" v-if="item.baseUrl">
              <LinkOutlined class="base-url-icon" />
              <span class="base-url-value">{{ item.baseUrl }}</span>
            </div>
            <p class="model-desc">{{ item.modelDescribe }}</p>
          </div>

          <!-- 卡片底部操作 -->
          <div class="card-footer">
            <a-button type="link" size="small" @click="showModelModal(item)">编辑</a-button>
            <a-divider type="vertical" />
            <a-button type="link" size="small" @click="toggleModelStatus(item)">
              {{ item._online ? '停用' : '启用' }}
            </a-button>
            <a-divider type="vertical" />
            <a-popconfirm
              title="确定要删除该模型吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleDeleteModel(item)"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <a-empty
        v-if="!loading && filteredModels.length === 0"
        description="暂无模型数据"
        style="margin-top: 80px"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingModel ? '编辑大模型' : '新增大模型'"
      width="560px"
      :destroyOnClose="true"
      :footer="null"
      @cancel="modalVisible = false"
    >
      <a-form
        :model="formState"
        :rules="formRules"
        ref="formRef"
        layout="vertical"
        style="margin-top: 16px"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="model" label="模型名称">
              <a-input v-model:value="formState.model" placeholder="例如：GPT-4o" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="manufacturerCode" label="所属厂商">
              <a-select v-model:value="formState.manufacturerCode" placeholder="请选择厂商" @change="onVendorOrKeyChange">
                <a-select-option v-for="v in vendorOptions" :key="v.code" :value="v.code">
                  {{ v.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item name="apiKey" label="API Key">
          <a-input-password v-model:value="formState.apiKey" placeholder="请输入 API Key" @change="onVendorOrKeyChange" />
        </a-form-item>

        <a-form-item name="baseUrl" label="自定义 Base URL">
          <a-input v-model:value="formState.baseUrl" placeholder="例如：https://api.openai.com/v1" @change="onVendorOrKeyChange">
            <template #prefix>
              <LinkOutlined style="color: rgba(0, 0, 0, 0.25)" />
            </template>
          </a-input>
          <template #extra>
            <span v-if="defaultBaseUrl" class="base-url-extra">
              默认地址：{{ defaultBaseUrl }}，
              <a-button type="link" size="small" @click="formState.baseUrl = defaultBaseUrl" style="padding: 0; height: auto; font-size: 12px;">使用默认</a-button>
            </span>
            <span v-else class="base-url-extra">留空则使用厂商默认地址</span>
          </template>
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="maxThreadSize" label="最大并发">
              <a-input-number v-model:value="formState.maxThreadSize" :min="1" :max="1000" placeholder="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="maxCompletionTokens" label="生成词元数量">
              <a-input-number v-model:value="formState.maxCompletionTokens" :min="1" :max="1000000" placeholder="500" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item name="modelDescribe" label="模型描述">
          <a-textarea
            v-model:value="formState.modelDescribe"
            placeholder="请输入模型描述信息"
            :rows="3"
          />
        </a-form-item>

        <a-form-item name="extraConfig" label="扩展配置">
          <a-textarea
            v-model:value="formState.extraConfig"
            placeholder='请输入 JSON 格式配置，例如：{"temperature": 0.7}'
            :rows="4"
            style="font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 13px;"
          />
          <template #extra>
            <span class="base-url-extra">留空表示无扩展配置，填写时须为合法 JSON 格式</span>
          </template>
        </a-form-item>

        <a-form-item name="online" label="启用状态">
          <a-switch
            v-model:checked="formState.online"
            checked-children="在线"
            un-checked-children="离线"
          />
        </a-form-item>

        <!-- 连接性测试 -->
        <a-form-item label="连接性测试">
          <div class="connection-test-row">
            <a-button
              :type="connectionTestStatus === 'success' ? 'primary' : 'default'"
              :danger="connectionTestStatus === 'fail'"
              :loading="connectionTestStatus === 'testing'"
              :disabled="connectionTestStatus === 'testing' || !canTestConnection"
              @click="handleTestConnection"
            >
              <template #icon>
                <CheckCircleOutlined v-if="connectionTestStatus === 'success'" />
                <CloseCircleOutlined v-else-if="connectionTestStatus === 'fail'" />
                <ApiOutlined v-else />
              </template>
              {{ connectionTestButtonText }}
            </a-button>
            <span class="connection-test-hint" v-if="connectionTestStatus === 'idle'">
              请先填写厂商和 API Key，然后点击测试
            </span>
            <span class="connection-test-hint connection-test-hint-success" v-if="connectionTestStatus === 'success'">
              连接成功，可以保存配置
            </span>
            <span class="connection-test-hint connection-test-hint-fail" v-if="connectionTestStatus === 'fail'">
              连接失败，请检查厂商和 API Key 是否正确
            </span>
          </div>
        </a-form-item>
      </a-form>

      <!-- 自定义底部按钮 -->
      <div class="modal-footer">
        <a-button @click="modalVisible = false">取消</a-button>
        <a-tooltip :title="connectionTestStatus !== 'success' ? '请先通过连接性测试' : ''">
          <a-button
            type="primary"
            :loading="modalLoading"
            :disabled="connectionTestStatus !== 'success'"
            @click="handleModalOk"
          >
            确定
          </a-button>
        </a-tooltip>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  RobotOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ApiOutlined,
  LinkOutlined,
} from '@ant-design/icons-vue'
import type { ModelInfo } from '../../types/model'
import { getModelList, createModel, updateModel, deleteModel, testConnectivity } from '../../api/model'

// 厂商选项（与后端枚举 ModelManufacturerEnum 对齐）
const vendorOptions = [
  { code: 'OPENAI', label: 'OpenAI', defaultBaseUrl: 'https://api.openai.com/v1', bg: '#e6f4ff', color: '#10a37f' },
  { code: 'DEEPSEEK', label: 'DeepSeek', defaultBaseUrl: 'https://api.deepseek.com/v1', bg: '#e6f4ff', color: '#4d6bfe' },
  { code: 'QWEN', label: '阿里云灵积', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', bg: '#fff7e6', color: '#ff6a00' },
  { code: 'TELE', label: 'Tele', defaultBaseUrl: '', bg: '#f0f5ff', color: '#722ed1' },
  { code: 'SPARK', label: '讯飞星火', defaultBaseUrl: '', bg: '#fffbe6', color: '#d97706' },
  { code: 'GLM', label: '智谱 AI', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4', bg: '#f0f5ff', color: '#3b5cff' },
  { code: 'KIMI', label: 'Moonshot', defaultBaseUrl: 'https://api.moonshot.cn/v1', bg: '#fff0f6', color: '#eb2f96' },
]

function getVendorStyle(code: string) {
  return vendorOptions.find(v => v.code === code) || { bg: '#e6f4ff', color: '#1677ff' }
}

function getVendorLabel(code: string) {
  return vendorOptions.find(v => v.code === code)?.label || code
}

function maskApiKey(apiKey: string) {
  if (!apiKey) return 'sk-****'
  if (apiKey.length <= 8) return 'sk-****'
  return `sk-****${apiKey.slice(-4)}`
}

// 列表数据
interface DisplayModel extends ModelInfo {
  _online: boolean
}

const models = ref<DisplayModel[]>([])
const loading = ref(false)
const vendorFilter = ref<string>('')

const filteredModels = computed(() => {
  if (!vendorFilter.value) return models.value
  return models.value.filter(m => m.manufacturerCode === vendorFilter.value)
})

async function fetchModels() {
  loading.value = true
  try {
    const { data: res } = await getModelList()
    if (res.code === '0' && res.data) {
      models.value = res.data.map(m => ({ ...m, _online: m.stream !== false }))
    }
  } catch {
    message.error('获取模型列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchModels()
})

// 弹窗状态
const modalVisible = ref(false)
const modalLoading = ref(false)
const editingModel = ref<DisplayModel | null>(null)
const formRef = ref()
const connectionTestStatus = ref<'idle' | 'testing' | 'success' | 'fail'>('idle')

const formState = reactive({
  model: '',
  manufacturerCode: undefined as string | undefined,
  apiKey: '',
  baseUrl: '',
  maxThreadSize: 1 as number,
  maxCompletionTokens: 500 as number,
  modelDescribe: '',
  extraConfig: '',
  online: true,
})

// 当前厂商的默认 Base URL
const defaultBaseUrl = computed(() => {
  const v = vendorOptions.find(o => o.code === formState.manufacturerCode)
  return v?.defaultBaseUrl || ''
})

// JSON 格式校验
const validateJson = (_rule: any, value: string) => {
  if (!value || value.trim() === '') return Promise.resolve()
  try {
    JSON.parse(value)
    return Promise.resolve()
  } catch {
    return Promise.reject('请输入合法的 JSON 格式')
  }
}

const formRules = {
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  manufacturerCode: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  extraConfig: [{ validator: validateJson, trigger: 'blur' }],
}

const canTestConnection = computed(() => {
  return formState.manufacturerCode && formState.apiKey
})

const connectionTestButtonText = computed(() => {
  const map: Record<string, string> = {
    idle: '测试连接',
    testing: '测试中...',
    success: '连接成功',
    fail: '连接失败，重试',
  }
  return map[connectionTestStatus.value]
})

const onVendorOrKeyChange = () => {
  if (connectionTestStatus.value !== 'idle') {
    connectionTestStatus.value = 'idle'
  }
}

const handleTestConnection = async () => {
  connectionTestStatus.value = 'testing'
  try {
    const { data: res } = await testConnectivity({
      model: formState.model,
      baseUrl: formState.baseUrl || defaultBaseUrl.value,
      apiKey: formState.apiKey,
      manufacturerCode: formState.manufacturerCode!,
    })
    if (res.code === '0' && res.data?.result) {
      connectionTestStatus.value = 'success'
      message.success('连接测试通过')
    } else {
      connectionTestStatus.value = 'fail'
      message.error(res.message || '连接测试失败，请检查配置')
    }
  } catch {
    connectionTestStatus.value = 'fail'
    message.error('连接测试异常，请稍后重试')
  }
}

const showModelModal = (record: DisplayModel | null = null) => {
  editingModel.value = record
  connectionTestStatus.value = 'idle'
  if (record) {
    Object.assign(formState, {
      model: record.model,
      manufacturerCode: record.manufacturerCode,
      apiKey: '',
      baseUrl: record.baseUrl || '',
      maxThreadSize: record.maxThreadSize ?? 1,
      maxCompletionTokens: record.maxCompletionTokens ?? 500,
      modelDescribe: record.modelDescribe || '',
      extraConfig: record.config ? JSON.stringify(record.config, null, 2) : '',
      online: record._online,
    })
  } else {
    Object.assign(formState, {
      model: '',
      manufacturerCode: undefined,
      apiKey: '',
      baseUrl: '',
      maxThreadSize: 1,
      maxCompletionTokens: 500,
      modelDescribe: '',
      extraConfig: '',
      online: true,
    })
  }
  modalVisible.value = true
}

const handleModalOk = async () => {
  try {
    await formRef.value.validate()
    modalLoading.value = true

    let config: Record<string, any> | null = null
    if (formState.extraConfig && formState.extraConfig.trim()) {
      config = JSON.parse(formState.extraConfig)
    }

    if (editingModel.value) {
      const updateData: any = {
        id: editingModel.value.id,
        model: formState.model,
        baseUrl: formState.baseUrl || defaultBaseUrl.value,
        manufacturerCode: formState.manufacturerCode,
        maxThreadSize: formState.maxThreadSize,
        maxCompletionTokens: formState.maxCompletionTokens,
        modelDescribe: formState.modelDescribe,
        stream: formState.online,
        config,
      }
      if (formState.apiKey) {
        updateData.apiKey = formState.apiKey
      }
      const { data: res } = await updateModel(updateData)
      if (res.code === '0') {
        message.success('更新成功')
        modalVisible.value = false
        fetchModels()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const { data: res } = await createModel({
        model: formState.model,
        baseUrl: formState.baseUrl || defaultBaseUrl.value,
        apiKey: formState.apiKey,
        manufacturerCode: formState.manufacturerCode!,
        maxThreadSize: formState.maxThreadSize,
        maxCompletionTokens: formState.maxCompletionTokens,
        modelDescribe: formState.modelDescribe,
        stream: formState.online,
        config,
      })
      if (res.code === '0') {
        message.success('新增成功')
        modalVisible.value = false
        fetchModels()
      } else {
        message.error(res.message || '新增失败')
      }
    }
  } catch {
    // form validation error
  } finally {
    modalLoading.value = false
  }
}

const toggleModelStatus = async (item: DisplayModel) => {
  const newState = !item._online
  try {
    const { data: res } = await updateModel({
      id: item.id,
      stream: newState,
    })
    if (res.code === '0') {
      item._online = newState
      message.success(`${item.model} 已${newState ? '启用' : '停用'}`)
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  }
}

const handleDeleteModel = async (item: DisplayModel) => {
  try {
    const { data: res } = await deleteModel(item.id)
    if (res.code === '0') {
      models.value = models.value.filter(m => m.id !== item.id)
      message.success('删除成功')
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}
</script>

<style scoped>
.model-page {
  padding: 24px;
}

.page-breadcrumb {
  margin-bottom: 20px;
}

.page-breadcrumb :deep(.ant-breadcrumb-link) {
  color: #8c8c8c;
  font-size: 13px;
}

.page-breadcrumb :deep(.ant-breadcrumb-link a) {
  color: #8c8c8c;
}

.page-breadcrumb :deep(.ant-breadcrumb-link a:hover) {
  color: #1677ff;
}

.page-content {
  max-width: 1200px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 13px;
  color: #8c8c8c;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 卡片网格 */
.model-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.model-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.3s, transform 0.2s;
}

.model-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

/* 状态标签 */
.card-status-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.online-dot {
  background: #52c41a;
}

.offline-dot {
  background: #d9d9d9;
}

.status-text {
  font-size: 12px;
  white-space: nowrap;
}

.online-text {
  color: #52c41a;
}

.offline-text {
  color: #8c8c8c;
}

/* 卡片内容 */
.card-body {
  flex: 1;
  padding-top: 4px;
}

.model-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.model-icon {
  font-size: 20px;
}

.model-name {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 4px;
}

.model-vendor {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 12px;
}

.model-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.meta-divider {
  color: #e8e8e8;
  font-size: 12px;
}

.meta-text {
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.model-desc {
  font-size: 13px;
  color: #595959;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 卡片中 Base URL 显示 */
.model-base-url {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
  padding: 4px 8px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.base-url-icon {
  font-size: 11px;
  color: #8c8c8c;
  flex-shrink: 0;
}

.base-url-value {
  font-size: 11px;
  color: #8c8c8c;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 表单中 Base URL 额外提示 */
.base-url-extra {
  font-size: 12px;
  color: #8c8c8c;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f5f5f5;
}

.card-footer :deep(.ant-btn-link) {
  padding: 0 4px;
  height: auto;
  font-size: 13px;
}

.card-footer :deep(.ant-btn-link:hover) {
  color: #1677ff;
}

.card-footer :deep(.ant-divider-vertical) {
  border-color: #e8e8e8;
}

/* 连接性测试 */
.connection-test-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.connection-test-hint {
  font-size: 13px;
  color: #8c8c8c;
}

.connection-test-hint-success {
  color: #52c41a;
}

.connection-test-hint-fail {
  color: #ff4d4f;
}

/* 弹窗底部 */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

/* 响应式 */
@media (max-width: 1024px) {
  .model-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .model-page {
    padding: 16px;
  }

  .model-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .ant-select {
    flex: 1;
  }
}
</style>
