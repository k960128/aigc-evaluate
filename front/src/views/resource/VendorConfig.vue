<template>
  <div class="vendor-page">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">资源中心</router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>厂商基础配置</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <!-- 头部区域 -->
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title">厂商基础配置</h2>
          <p class="page-desc">维护厂商 Base URL 及启用状态，支持对 API 服务商进行统一管理</p>
        </div>
        <a-button type="primary" @click="showModal()" size="middle">
          <template #icon><PlusOutlined /></template>
          新增厂商
        </a-button>
      </div>

      <!-- 表格 -->
      <div class="table-card">
        <a-table
          :columns="columns"
          :data-source="vendors"
          :pagination="pagination"
          row-key="id"
          :row-class-name="() => 'table-row'"
          :loading="loading"
        >
          <!-- 厂商信息列 -->
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'vendor'">
              <div class="vendor-cell">
                <a-avatar
                  :size="36"
                  :style="{ backgroundColor: record.avatarBg, fontWeight: 600, fontSize: '14px' }"
                >
                  {{ record.avatarText }}
                </a-avatar>
                <div class="vendor-info">
                  <span class="vendor-name">{{ record.manufacturerName }}</span>
                  <span class="vendor-sub">{{ record.manufacturerCode }}</span>
                </div>
              </div>
            </template>

            <!-- Base URL列 -->
            <template v-if="column.key === 'baseUrl'">
              <a-typography-text
                :copyable="{ text: record.defaultBaseUrl, tooltips: ['复制', '已复制'] }"
                class="base-url-text"
              >
                {{ record.defaultBaseUrl || '-' }}
              </a-typography-text>
            </template>

            <!-- 启用状态列 -->
            <template v-if="column.key === 'status'">
              <a-switch
                :checked="record.enable"
                @change="(checked: boolean) => handleStatusChange(record, checked)"
                checked-children="开"
                un-checked-children="关"
              />
            </template>

            <!-- 操作列 -->
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="showModal(record)">
                  编辑
                </a-button>
                <a-popconfirm
                  title="确定要删除该厂商吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleDelete(record)"
                >
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingVendor ? '编辑厂商' : '新增厂商'"
      :confirm-loading="modalLoading"
      @ok="handleModalOk"
      @cancel="modalVisible = false"
      width="520px"
      :destroyOnClose="true"
    >
      <a-form
        :model="formState"
        :rules="formRules"
        ref="formRef"
        layout="vertical"
        style="margin-top: 16px"
      >
        <a-form-item name="manufacturerName" label="厂商名称">
          <a-input v-model:value="formState.manufacturerName" placeholder="请输入厂商名称" />
        </a-form-item>

        <a-form-item name="manufacturerCode" label="厂商标识">
          <a-input v-model:value="formState.manufacturerCode" placeholder="请输入厂商英文标识" />
        </a-form-item>

        <a-form-item name="defaultBaseUrl" label="Base URL">
          <a-input v-model:value="formState.defaultBaseUrl" placeholder="例如：https://api.openai.com/v1" />
        </a-form-item>

        <a-form-item name="enable" label="启用状态">
          <a-switch
            v-model:checked="formState.enable"
            checked-children="启用"
            un-checked-children="停用"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import type { Manufacturer } from '../../api/manufacturer'
import {
  getManufacturerList,
  createManufacturer,
  updateManufacturer,
  deleteManufacturer,
} from '../../api/manufacturer'

const pagination = {
  pageSize: 10,
  showTotal: (total: number) => `共 ${total} 条`,
  showSizeChanger: true,
}

const columns = [
  { title: '厂商信息', key: 'vendor', width: 240 },
  { title: 'Base URL', key: 'baseUrl', ellipsis: true },
  { title: '启用状态', key: 'status', width: 120, align: 'center' },
  { title: '操作', key: 'action', width: 150, align: 'center' },
]

interface DisplayVendor extends Manufacturer {
  avatarText: string
  avatarBg: string
}

const vendors = ref<DisplayVendor[]>([])
const loading = ref(false)

function getAvatarStyle(code: string) {
  const map: Record<string, { text: string; bg: string }> = {
    OPENAI: { text: 'O', bg: '#10a37f' },
    DEEPSEEK: { text: 'D', bg: '#4d6bfe' },
    QWEN: { text: '阿', bg: '#ff6a00' },
    ANTHROPIC: { text: 'A', bg: '#d97706' },
    GLM: { text: '智', bg: '#3b5cff' },
    SPARK: { text: '讯', bg: '#e23636' },
    KIMI: { text: 'M', bg: '#eb2f96' },
    TELE: { text: 'T', bg: '#722ed1' },
  }
  return map[code] || { text: code.charAt(0).toUpperCase(), bg: '#1677ff' }
}

async function fetchVendors() {
  loading.value = true
  try {
    const { data: res } = await getManufacturerList()
    if (res.code === '0' && res.data) {
      vendors.value = res.data.map(v => {
        const style = getAvatarStyle(v.manufacturerCode)
        return { ...v, avatarText: style.text, avatarBg: style.bg }
      })
    }
  } catch {
    message.error('获取厂商列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchVendors()
})

const modalVisible = ref(false)
const modalLoading = ref(false)
const editingVendor = ref<DisplayVendor | null>(null)
const formRef = ref()

const formState = reactive({
  manufacturerName: '',
  manufacturerCode: '',
  defaultBaseUrl: '',
  enable: true,
})

const formRules = {
  manufacturerName: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  manufacturerCode: [{ required: true, message: '请输入厂商标识', trigger: 'blur' }],
  defaultBaseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
}

const showModal = (record: DisplayVendor | null = null) => {
  editingVendor.value = record
  if (record) {
    Object.assign(formState, {
      manufacturerName: record.manufacturerName,
      manufacturerCode: record.manufacturerCode,
      defaultBaseUrl: record.defaultBaseUrl || '',
      enable: record.enable ?? true,
    })
  } else {
    Object.assign(formState, { manufacturerName: '', manufacturerCode: '', defaultBaseUrl: '', enable: true })
  }
  modalVisible.value = true
}

const handleModalOk = async () => {
  try {
    await formRef.value.validate()
    modalLoading.value = true

    if (editingVendor.value) {
      const { data: res } = await updateManufacturer({
        id: editingVendor.value.id!,
        manufacturerName: formState.manufacturerName,
        manufacturerCode: formState.manufacturerCode,
        defaultBaseUrl: formState.defaultBaseUrl,
        enable: formState.enable,
      })
      if (res.code === '0') {
        message.success('更新成功')
        fetchVendors()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const { data: res } = await createManufacturer({
        manufacturerName: formState.manufacturerName,
        manufacturerCode: formState.manufacturerCode,
        defaultBaseUrl: formState.defaultBaseUrl,
        enable: formState.enable,
      })
      if (res.code === '0') {
        message.success('新增成功')
        fetchVendors()
      } else {
        message.error(res.message || '新增失败')
      }
    }

    modalVisible.value = false
  } catch {
    // form validation error
  } finally {
    modalLoading.value = false
  }
}

const handleStatusChange = async (record: DisplayVendor, checked: boolean) => {
  try {
    const { data: res } = await updateManufacturer({
      id: record.id!,
      enable: checked,
    })
    if (res.code === '0') {
      record.enable = checked
      message.success(`${record.manufacturerName} 已${checked ? '启用' : '停用'}`)
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  }
}

const handleDelete = async (record: DisplayVendor) => {
  try {
    const { data: res } = await deleteManufacturer(record.id!)
    if (res.code === '0') {
      vendors.value = vendors.value.filter(v => v.id !== record.id)
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
.vendor-page {
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

.table-card {
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  border: 1px solid #f0f0f0;
}

.table-card :deep(.ant-table) {
  border-radius: 10px;
}

.table-card :deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  font-weight: 600;
  color: #434343;
  font-size: 13px;
  border-bottom: 1px solid #f0f0f0;
}

.table-card :deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f5f5f5;
}

.table-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #fafafa;
}

.vendor-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.vendor-info {
  display: flex;
  flex-direction: column;
}

.vendor-name {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
}

.vendor-sub {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.base-url-text {
  font-size: 13px;
  color: #595959;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

@media (max-width: 768px) {
  .vendor-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
