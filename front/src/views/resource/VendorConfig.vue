<script setup lang="ts">
import type { FormInstance } from 'ant-design-vue'
import type { CreateManufacturerReq, Manufacturer, UpdateManufacturerReq } from '../../api/manufacturer'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { onMounted, reactive, ref } from 'vue'
import {
  createManufacturer,
  deleteManufacturer,
  getManufacturerList,
  updateManufacturer,
} from '../../api/manufacturer'

const loading = ref(false)
const dataSource = ref<Manufacturer[]>([])

// 新增/编辑弹窗
const modalVisible = ref(false)
const modalTitle = ref('新增厂商')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const formState = reactive<CreateManufacturerReq & { id?: number }>({
  manufacturerName: '',
  manufacturerCode: '',
  defaultBaseUrl: '',
  describe: '',
  icon: '',
  enable: true,
})

// eslint-disable-next-line ts/no-unsafe-assignment
const rules: any = {
  manufacturerName: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  manufacturerCode: [{ required: true, message: '请输入厂商编码', trigger: 'blur' }],
  defaultBaseUrl: [{ required: true, message: '请输入Base URL', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await getManufacturerList()
    if (data.data)
      dataSource.value = data.data
  }
  finally {
    loading.value = false
  }
}

function showAddModal() {
  isEdit.value = false
  modalTitle.value = '新增厂商'
  Object.assign(formState, {
    id: undefined,
    manufacturerName: '',
    manufacturerCode: '',
    defaultBaseUrl: '',
    describe: '',
    icon: '',
    enable: true,
  })
  modalVisible.value = true
}

function showEditModal(record: Manufacturer) {
  isEdit.value = true
  modalTitle.value = '编辑厂商'
  Object.assign(formState, {
    id: record.id,
    manufacturerName: record.manufacturerName,
    manufacturerCode: record.manufacturerCode,
    defaultBaseUrl: record.defaultBaseUrl,
    describe: record.describe,
    icon: record.icon,
    enable: record.enable,
  })
  modalVisible.value = true
}

async function handleOk() {
  try {
    await formRef.value?.validateFields()
  }
  catch {
    return
  }

  if (isEdit.value) {
    const req: UpdateManufacturerReq = {
      id: formState.id!,
      manufacturerName: formState.manufacturerName,
      manufacturerCode: formState.manufacturerCode,
      defaultBaseUrl: formState.defaultBaseUrl,
      describe: formState.describe,
      icon: formState.icon,
      enable: formState.enable,
    }
    await updateManufacturer(req)
    message.success('更新成功')
  }
  else {
    const req: CreateManufacturerReq = {
      manufacturerName: formState.manufacturerName,
      manufacturerCode: formState.manufacturerCode,
      defaultBaseUrl: formState.defaultBaseUrl,
      describe: formState.describe,
      icon: formState.icon,
      enable: formState.enable,
    }
    await createManufacturer(req)
    message.success('创建成功')
  }

  modalVisible.value = false
  fetchList()
}

function handleDelete(record: Manufacturer) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除厂商「${record.manufacturerName}」吗？`,
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      await deleteManufacturer(record.id!)
      message.success('删除成功')
      fetchList()
    },
  })
}

async function handleEnableChange(record: Manufacturer, checked: boolean) {
  const req: UpdateManufacturerReq = {
    id: record.id!,
    enable: checked,
  }
  await updateManufacturer(req)
  message.success(checked ? '已启用' : '已停用')
  fetchList()
}

// 表格列
const columns = [
  {
    title: '厂商',
    dataIndex: 'manufacturerName',
    key: 'manufacturerName',
    width: 240,
  },
  {
    title: 'Base URL',
    dataIndex: 'defaultBaseUrl',
    key: 'defaultBaseUrl',
    ellipsis: true,
  },
  {
    title: '描述',
    dataIndex: 'describe',
    key: 'describe',
    ellipsis: true,
    width: 200,
  },
  {
    title: '全局启用',
    dataIndex: 'enable',
    key: 'enable',
    width: 120,
    align: 'center' as const,
  },
  {
    title: '操作',
    key: 'action',
    width: 160,
    align: 'center' as const,
  },
]

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="vendor-config-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">厂商基础配置</h2>
        <p class="page-desc">维护大模型服务商的基础网络配置（Base URL）及全局启用状态</p>
      </div>
      <a-button type="primary" @click="showAddModal">
        <PlusOutlined />
        新增厂商
      </a-button>
    </div>

    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 800 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'manufacturerName'">
            <div class="vendor-cell">
              <div class="vendor-logo">
                <img v-if="record.icon" :src="record.icon" :alt="record.manufacturerName">
                <span v-else class="vendor-logo-text">{{ record.manufacturerName?.charAt(0) }}</span>
              </div>
              <div class="vendor-name-info">
                <div class="vendor-name">{{ record.manufacturerName }}</div>
                <div class="vendor-code">{{ record.manufacturerCode }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'enable'">
            <a-switch
              :checked="record.enable"
              @change="(checked: any) => handleEnableChange(record as Manufacturer, checked as boolean)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record as Manufacturer)">
                编辑
              </a-button>
              <a-button type="link" size="small" danger @click="handleDelete(record as Manufacturer)">
                删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="520"
      :mask-closable="false"
      @ok="handleOk"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        style="margin-top: 16px"
      >
        <a-form-item label="厂商名称" name="manufacturerName">
          <a-input v-model:value="formState.manufacturerName" placeholder="请输入厂商名称" />
        </a-form-item>
        <a-form-item label="厂商编码" name="manufacturerCode">
          <a-input v-model:value="formState.manufacturerCode" placeholder="请输入厂商编码" :disabled="isEdit" />
        </a-form-item>
        <a-form-item label="Base URL" name="defaultBaseUrl">
          <a-input v-model:value="formState.defaultBaseUrl" placeholder="请输入默认 Base URL" />
        </a-form-item>
        <a-form-item label="描述" name="describe">
          <a-textarea v-model:value="formState.describe" placeholder="请输入厂商描述" :rows="3" />
        </a-form-item>
        <a-form-item label="图标" name="icon">
          <a-input v-model:value="formState.icon" placeholder="请输入图标URL" />
        </a-form-item>
        <a-form-item label="启用状态" name="enable">
          <a-switch v-model:checked="formState.enable" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.vendor-config-page {
  max-width: 1400px;
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
  color: var(--gray-800);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 14px;
  color: var(--gray-500);
}

.table-card {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--gray-200);
}

.vendor-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.vendor-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--primary-blue-light);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.vendor-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.vendor-logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--primary-blue);
}

.vendor-name-info {
  display: flex;
  flex-direction: column;
}

.vendor-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-800);
}

.vendor-code {
  font-size: 12px;
  color: var(--gray-500);
  margin-top: 2px;
}
</style>
