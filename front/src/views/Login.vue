<script setup lang="ts">
import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

const MOCK_AUTH_KEY = 'mock-authenticated'

const router = useRouter()
const loading = shallowRef(false)
const formState = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules: Record<string, Rule[]> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true

  try {
    localStorage.setItem(MOCK_AUTH_KEY, 'true')
    message.success('登录成功')
    await router.push('/home')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-shell">
      <div class="brand-panel">
        <div class="brand-mark">
          <SafetyCertificateOutlined />
        </div>
        <div class="brand-copy">
          <h1 class="brand-title">
            大模型安全评测平台
          </h1>
          <p class="brand-subtitle">
            AIGC Safety Evaluation Console
          </p>
        </div>
      </div>

      <div class="login-panel">
        <div class="login-header">
          <h2 class="login-title">
            登录
          </h2>
          <span class="login-desc">请输入账号信息进入平台</span>
        </div>

        <a-form
          :model="formState"
          :rules="rules"
          layout="vertical"
          class="login-form"
          @finish="handleLogin"
        >
          <a-form-item name="username" label="用户名">
            <a-input
              v-model:value="formState.username"
              size="large"
              placeholder="请输入用户名"
              autocomplete="username"
            >
              <template #prefix>
                <UserOutlined class="input-icon" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="password" label="密码">
            <a-input-password
              v-model:value="formState.password"
              size="large"
              placeholder="请输入密码"
              autocomplete="current-password"
            >
              <template #prefix>
                <LockOutlined class="input-icon" />
              </template>
            </a-input-password>
          </a-form-item>

          <a-button
            type="primary"
            size="large"
            html-type="submit"
            block
            :loading="loading"
            class="login-button"
          >
            登录
          </a-button>
        </a-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.08), rgba(82, 196, 26, 0.04)),
    var(--gray-50);
}

.login-shell {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  background: #fff;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.brand-panel {
  min-height: 420px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 48px;
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.12), rgba(19, 194, 194, 0.08)),
    #f8fbff;
  border-right: 1px solid var(--gray-200);
}

.brand-mark {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--primary-blue);
  color: #fff;
  font-size: 32px;
  box-shadow: 0 10px 24px rgba(22, 119, 255, 0.2);
}

.brand-copy {
  min-width: 0;
}

.brand-title {
  margin: 0;
  color: var(--gray-800);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
}

.brand-subtitle {
  margin: 10px 0 0;
  color: var(--gray-500);
  font-size: 14px;
}

.login-panel {
  padding: 44px 36px;
}

.login-header {
  margin-bottom: 28px;
}

.login-title {
  margin: 0;
  color: var(--gray-800);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.4;
}

.login-desc {
  display: inline-block;
  margin-top: 8px;
  color: var(--gray-500);
  font-size: 13px;
}

.login-form :deep(.ant-form-item-label > label) {
  color: var(--gray-700);
  font-size: 13px;
}

.input-icon {
  color: var(--gray-400);
}

.login-button {
  margin-top: 6px;
  border-radius: var(--radius-md);
}

@media (max-width: 768px) {
  .login-page {
    padding: 20px;
  }

  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
    padding: 28px;
    border-right: 0;
    border-bottom: 1px solid var(--gray-200);
  }

  .brand-title {
    font-size: 22px;
  }

  .login-panel {
    padding: 30px 24px;
  }
}

@media (max-width: 480px) {
  .brand-panel {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
