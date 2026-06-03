<script setup lang="ts">
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  MailOutlined,
  PhoneOutlined,
  PlusOutlined,
  SearchOutlined,
  StopOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, reactive, ref, shallowRef, useTemplateRef } from 'vue'

type UserRole = 'ADMIN' | 'EVAL_MANAGER' | 'DATA_MANAGER' | 'VIEWER'
type UserStatus = 'ENABLED' | 'DISABLED'
type StatusFilter = 'all' | UserStatus

interface UserAccount {
  id: number
  username: string
  realName: string
  phone: string
  email: string
  role: UserRole
  department: string
  status: UserStatus
  lastLoginTime: string
  createTime: string
  updateTime: string
}

const roleOptions: Array<{ value: UserRole, label: string, color: string, bg: string }> = [
  { value: 'ADMIN', label: '管理员', color: '#1677ff', bg: '#e6f4ff' },
  { value: 'EVAL_MANAGER', label: '评测管理员', color: '#722ed1', bg: '#f9f0ff' },
  { value: 'DATA_MANAGER', label: '数据管理员', color: '#52c41a', bg: '#f6ffed' },
  { value: 'VIEWER', label: '只读访客', color: '#8c8c8c', bg: '#fafafa' },
]

const departmentOptions = ['平台运营部', '安全评测部', '数据治理部', '模型接入部', '质量保障部']

const columns = [
  { title: '账号信息', key: 'account', width: 260, fixed: 'left' as const },
  { title: '联系方式', key: 'contact', width: 240 },
  { title: '角色', key: 'role', width: 130 },
  { title: '部门', key: 'department', width: 130 },
  { title: '状态', key: 'status', width: 120, align: 'center' as const },
  { title: '最后登录', key: 'lastLoginTime', width: 170 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 230, align: 'center' as const, fixed: 'right' as const },
]

const pagination = {
  pageSize: 8,
  showTotal: (total: number) => `共 ${total} 条`,
  showSizeChanger: true,
}

const users = ref<UserAccount[]>([
  {
    id: 1,
    username: 'admin',
    realName: '平台管理员',
    phone: '138-0000-0001',
    email: 'admin@aigc-eval.local',
    role: 'ADMIN',
    department: '平台运营部',
    status: 'ENABLED',
    lastLoginTime: '2026-06-02 09:12',
    createTime: '2024-01-01 00:00',
    updateTime: '2026-06-02 09:12',
  },
  {
    id: 2,
    username: 'eval_chen',
    realName: '陈评测',
    phone: '138-0000-0012',
    email: 'chen.eval@aigc-eval.local',
    role: 'EVAL_MANAGER',
    department: '安全评测部',
    status: 'ENABLED',
    lastLoginTime: '2026-06-01 18:30',
    createTime: '2024-02-10 10:20',
    updateTime: '2026-06-01 18:30',
  },
  {
    id: 3,
    username: 'data_li',
    realName: '李数据',
    phone: '138-0000-0023',
    email: 'li.data@aigc-eval.local',
    role: 'DATA_MANAGER',
    department: '数据治理部',
    status: 'ENABLED',
    lastLoginTime: '2026-05-31 14:05',
    createTime: '2024-03-18 09:45',
    updateTime: '2026-05-31 14:05',
  },
  {
    id: 4,
    username: 'model_wang',
    realName: '王接入',
    phone: '138-0000-0034',
    email: 'wang.model@aigc-eval.local',
    role: 'EVAL_MANAGER',
    department: '模型接入部',
    status: 'ENABLED',
    lastLoginTime: '2026-05-28 11:26',
    createTime: '2024-04-06 16:10',
    updateTime: '2026-05-28 11:26',
  },
  {
    id: 5,
    username: 'qa_zhao',
    realName: '赵质检',
    phone: '138-0000-0045',
    email: 'zhao.qa@aigc-eval.local',
    role: 'VIEWER',
    department: '质量保障部',
    status: 'DISABLED',
    lastLoginTime: '2026-04-20 15:40',
    createTime: '2024-05-12 13:30',
    updateTime: '2026-05-20 10:10',
  },
  {
    id: 6,
    username: 'guest_read',
    realName: '只读访客',
    phone: '138-0000-0056',
    email: 'guest.read@aigc-eval.local',
    role: 'VIEWER',
    department: '平台运营部',
    status: 'ENABLED',
    lastLoginTime: '2026-05-22 08:50',
    createTime: '2024-06-01 12:00',
    updateTime: '2026-05-22 08:50',
  },
])

const keyword = shallowRef('')
const roleFilter = shallowRef<UserRole | undefined>()
const departmentFilter = shallowRef<string | undefined>()
const statusFilter = shallowRef<StatusFilter>('all')
const modalVisible = shallowRef(false)
const modalLoading = shallowRef(false)
const editingUser = shallowRef<UserAccount | null>(null)
const formRef = useTemplateRef<any>('userForm')

const formState = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  role: undefined as UserRole | undefined,
  department: undefined as string | undefined,
  enabled: true,
})

function validateEmail(_rule: unknown, value: string) {
  if (!value) {
    return Promise.resolve()
  }

  const parts = value.split('@')
  const domain = parts[1] || ''
  const valid = parts.length === 2 && parts[0].trim().length > 0 && domain.includes('.') && !value.includes(' ')
  return valid ? Promise.resolve() : Promise.reject(new Error('请输入合法邮箱地址'))
}

function validatePhone(_rule: unknown, value: string) {
  if (!value) {
    return Promise.resolve()
  }

  const valid = /^[\d-]{7,20}$/.test(value)
  return valid ? Promise.resolve() : Promise.reject(new Error('手机号仅支持数字和短横线'))
}

const formRules: Record<string, any[]> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  department: [{ required: true, message: '请选择部门', trigger: 'change' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
}

const filteredUsers = computed(() => {
  const text = keyword.value.trim().toLowerCase()

  return users.value.filter((item) => {
    const matchesKeyword = !text
      || item.username.toLowerCase().includes(text)
      || item.realName.toLowerCase().includes(text)
      || item.phone.toLowerCase().includes(text)
      || item.email.toLowerCase().includes(text)

    const matchesRole = !roleFilter.value || item.role === roleFilter.value
    const matchesDepartment = !departmentFilter.value || item.department === departmentFilter.value
    const matchesStatus = statusFilter.value === 'all' || item.status === statusFilter.value

    return matchesKeyword && matchesRole && matchesDepartment && matchesStatus
  })
})

const enabledCount = computed(() => users.value.filter(item => item.status === 'ENABLED').length)
const disabledCount = computed(() => users.value.filter(item => item.status === 'DISABLED').length)
const latestLoginTime = computed(() => {
  const sorted = users.value
    .map(item => item.lastLoginTime)
    .filter(Boolean)
    .sort((a, b) => b.localeCompare(a))

  return sorted[0] || '-'
})

const metricCards = computed(() => [
  {
    key: 'total',
    label: '用户总数',
    value: users.value.length,
    desc: '平台账号规模',
    icon: TeamOutlined,
    iconColor: '#1677ff',
    bgColor: '#e6f4ff',
  },
  {
    key: 'enabled',
    label: '启用用户',
    value: enabledCount.value,
    desc: '可登录平台',
    icon: CheckCircleOutlined,
    iconColor: '#52c41a',
    bgColor: '#f6ffed',
  },
  {
    key: 'disabled',
    label: '停用用户',
    value: disabledCount.value,
    desc: '已限制访问',
    icon: StopOutlined,
    iconColor: '#ff4d4f',
    bgColor: '#fff2f0',
  },
  {
    key: 'latest',
    label: '最近登录',
    value: latestLoginTime.value,
    desc: '按最后登录倒序',
    icon: ClockCircleOutlined,
    iconColor: '#faad14',
    bgColor: '#fff7e6',
  },
])

function getRoleMeta(role: UserRole) {
  return roleOptions.find(item => item.value === role) || roleOptions[0]
}

function getInitials(name: string) {
  if (!name) {
    return 'U'
  }

  return name.trim().slice(0, 1).toUpperCase()
}

function getCurrentTime() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function resetForm() {
  Object.assign(formState, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    role: undefined,
    department: undefined,
    enabled: true,
  })
}

function showModal(record: UserAccount | null = null) {
  editingUser.value = record

  if (record) {
    Object.assign(formState, {
      username: record.username,
      realName: record.realName,
      phone: record.phone,
      email: record.email,
      role: record.role,
      department: record.department,
      enabled: record.status === 'ENABLED',
    })
  }
  else {
    resetForm()
  }

  modalVisible.value = true
}

async function handleModalOk() {
  try {
    await formRef.value?.validate()

    const duplicated = users.value.some(item =>
      item.username === formState.username && item.id !== editingUser.value?.id,
    )
    if (duplicated) {
      message.warning('用户名已存在，请更换后再保存')
      return
    }

    modalLoading.value = true
    const updateTime = getCurrentTime()
    const status: UserStatus = formState.enabled ? 'ENABLED' : 'DISABLED'

    if (editingUser.value) {
      const index = users.value.findIndex(item => item.id === editingUser.value?.id)
      if (index !== -1) {
        users.value[index] = {
          ...users.value[index],
          username: formState.username,
          realName: formState.realName,
          phone: formState.phone,
          email: formState.email,
          role: formState.role!,
          department: formState.department!,
          status,
          updateTime,
        }
        message.success('用户更新成功')
      }
    }
    else {
      users.value.unshift({
        id: Date.now(),
        username: formState.username,
        realName: formState.realName,
        phone: formState.phone,
        email: formState.email,
        role: formState.role!,
        department: formState.department!,
        status,
        lastLoginTime: '-',
        createTime: updateTime,
        updateTime,
      })
      message.success('用户创建成功')
    }

    modalVisible.value = false
  }
  catch {
    // form validation error
  }
  finally {
    modalLoading.value = false
  }
}

function handleStatusChange(record: UserAccount, checked: boolean) {
  record.status = checked ? 'ENABLED' : 'DISABLED'
  record.updateTime = getCurrentTime()
  message.success(`${record.realName} 已${checked ? '启用' : '停用'}`)
}

function handleDelete(record: UserAccount) {
  users.value = users.value.filter(item => item.id !== record.id)
  message.success('用户删除成功')
}

function handleResetPassword(record: UserAccount) {
  record.updateTime = getCurrentTime()
  message.success(`${record.realName} 的密码已重置为初始密码`)
}

function handleResetFilters() {
  keyword.value = ''
  roleFilter.value = undefined
  departmentFilter.value = undefined
  statusFilter.value = 'all'
}
</script>

<template>
  <div class="user-page">
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>
          <router-link to="/home">首页</router-link>
        </a-breadcrumb-item>
        <a-breadcrumb-item>用户管理</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <div class="page-header">
        <div class="header-info">
          <h2 class="page-title">用户管理</h2>
          <p class="page-desc">管理平台账号、用户状态和基础角色，支持账号启停与密码重置</p>
        </div>
        <a-button type="primary" @click="showModal()">
          <template #icon><PlusOutlined /></template>
          新增用户
        </a-button>
      </div>

      <div class="metric-grid">
        <div v-for="item in metricCards" :key="item.key" class="metric-card card-hover-transition">
          <div class="metric-icon-wrap" :style="{ background: item.bgColor }">
            <component :is="item.icon" class="metric-icon" :style="{ color: item.iconColor }" />
          </div>
          <div class="metric-info">
            <div class="metric-label">{{ item.label }}</div>
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <div class="filter-section">
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索用户名、姓名、手机号或邮箱..."
          class="search-input"
          allow-clear
        >
          <template #prefix><SearchOutlined /></template>
        </a-input-search>

        <a-select
          v-model:value="roleFilter"
          placeholder="角色筛选"
          allow-clear
          class="filter-select"
        >
          <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>

        <a-select
          v-model:value="departmentFilter"
          placeholder="部门筛选"
          allow-clear
          class="filter-select"
        >
          <a-select-option v-for="item in departmentOptions" :key="item" :value="item">
            {{ item }}
          </a-select-option>
        </a-select>

        <a-select v-model:value="statusFilter" class="filter-select">
          <a-select-option value="all">全部状态</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已停用</a-select-option>
        </a-select>

        <a-button @click="handleResetFilters">重置</a-button>
      </div>

      <div class="table-card">
        <a-table
          :columns="columns"
          :data-source="filteredUsers"
          :pagination="pagination"
          :scroll="{ x: 1350 }"
          row-key="id"
          :locale="{ emptyText: '暂无用户数据' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'account'">
              <div class="account-cell">
                <a-avatar :size="38" class="user-avatar-cell">
                  {{ getInitials(record.realName) }}
                </a-avatar>
                <div class="account-info">
                  <span class="account-name">{{ record.realName }}</span>
                  <span class="account-username">@{{ record.username }}</span>
                </div>
              </div>
            </template>

            <template v-if="column.key === 'contact'">
              <div class="contact-cell">
                <div class="contact-line">
                  <PhoneOutlined class="contact-icon" />
                  <span>{{ record.phone || '-' }}</span>
                </div>
                <div class="contact-line">
                  <MailOutlined class="contact-icon" />
                  <span>{{ record.email || '-' }}</span>
                </div>
              </div>
            </template>

            <template v-if="column.key === 'role'">
              <a-tag
                class="role-tag"
                :style="{
                  color: getRoleMeta(record.role).color,
                  background: getRoleMeta(record.role).bg,
                  borderColor: getRoleMeta(record.role).bg,
                }"
              >
                {{ getRoleMeta(record.role).label }}
              </a-tag>
            </template>

            <template v-if="column.key === 'department'">
              <span class="plain-text">{{ record.department }}</span>
            </template>

            <template v-if="column.key === 'status'">
              <a-switch
                :checked="record.status === 'ENABLED'"
                checked-children="启用"
                un-checked-children="停用"
                @change="(checked) => handleStatusChange(record as UserAccount, Boolean(checked))"
              />
            </template>

            <template v-if="column.key === 'lastLoginTime'">
              <div class="time-cell">
                <ClockCircleOutlined class="time-icon" />
                <span>{{ record.lastLoginTime }}</span>
              </div>
            </template>

            <template v-if="column.key === 'createTime'">
              <span class="plain-text">{{ record.createTime }}</span>
            </template>

            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="showModal(record as UserAccount)">
                  <template #icon><EditOutlined /></template>
                  编辑
                </a-button>
                <a-popconfirm
                  title="确定要重置该用户密码吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleResetPassword(record as UserAccount)"
                >
                  <a-button type="link" size="small">
                    <template #icon><KeyOutlined /></template>
                    重置密码
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要删除该用户吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleDelete(record as UserAccount)"
                >
                  <a-button type="link" size="small" danger>
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="editingUser ? '编辑用户' : '新增用户'"
      width="560px"
      :confirm-loading="modalLoading"
      :destroyOnClose="true"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleModalOk"
      @cancel="modalVisible = false"
    >
      <a-form
        ref="userForm"
        :model="formState"
        :rules="formRules"
        layout="vertical"
        style="margin-top: 16px"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="username" label="用户名">
              <a-input v-model:value="formState.username" placeholder="例如：admin" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="realName" label="姓名">
              <a-input v-model:value="formState.realName" placeholder="请输入姓名" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="phone" label="手机号">
              <a-input v-model:value="formState.phone" placeholder="例如：138-0000-0000" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="email" label="邮箱">
              <a-input v-model:value="formState.email" placeholder="例如：user@example.com" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="role" label="角色">
              <a-select v-model:value="formState.role" placeholder="请选择角色">
                <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item name="department" label="部门">
              <a-select v-model:value="formState.department" placeholder="请选择部门">
                <a-select-option v-for="item in departmentOptions" :key="item" :value="item">
                  {{ item }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item name="enabled" label="启用状态">
          <a-switch
            v-model:checked="formState.enabled"
            checked-children="启用"
            un-checked-children="停用"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.user-page {
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
  max-width: 1400px;
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

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

.metric-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-icon {
  font-size: 20px;
}

.metric-info {
  min-width: 0;
  flex: 1;
}

.metric-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: #262626;
  line-height: 1.25;
  letter-spacing: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.search-input {
  width: 320px;
}

.filter-select {
  width: 150px;
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

.account-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar-cell {
  flex-shrink: 0;
  background: #1677ff;
  font-size: 14px;
  font-weight: 600;
}

.account-info {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.account-name {
  color: #262626;
  font-size: 14px;
  font-weight: 500;
}

.account-username {
  margin-top: 2px;
  color: #8c8c8c;
  font-size: 12px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.contact-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.contact-line {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #595959;
  font-size: 13px;
}

.contact-icon {
  color: #8c8c8c;
  font-size: 13px;
}

.role-tag {
  margin-inline-end: 0;
  border-radius: 4px;
}

.plain-text {
  color: #595959;
  font-size: 13px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #595959;
  font-size: 13px;
}

.time-icon {
  color: #8c8c8c;
  font-size: 14px;
}

.table-card :deep(.ant-btn-link) {
  padding: 0 4px;
  height: auto;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .user-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
  }

  .page-header :deep(.ant-btn) {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .filter-section {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input,
  .filter-select,
  .filter-section :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
