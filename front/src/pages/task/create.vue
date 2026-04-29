<script setup lang="ts">
import type { Rule } from 'ant-design-vue/es/form'
import type { CreateEvalTaskRequest } from '../../types/eval-task'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useEvalTaskStore } from '../../stores/eval-task'

const router = useRouter()
const store = useEvalTaskStore()
const formRef = ref()
const submitting = ref(false)

const formState = reactive<CreateEvalTaskRequest>({
  taskName: '',
  modelId: undefined as unknown as number,
  datasetId: undefined as unknown as number,
})

const rules: Record<string, Rule[]> = {
  taskName: [{ required: true, message: '请输入任务名称' }],
  modelId: [{ required: true, message: '请选择模型' }],
  datasetId: [{ required: true, message: '请选择数据集' }],
}

// Mock data for now - will be replaced with API calls
const modelOptions = ref([
  { label: 'DeepSeek-V3', value: 1 },
  { label: 'Qwen-Max', value: 2 },
  { label: 'GPT-4o', value: 3 },
  { label: 'Spark-V3', value: 4 },
])

const datasetOptions = ref([
  { label: '通用对话评测集', value: 1 },
  { label: '代码生成评测集', value: 2 },
  { label: 'L1拦截评测集', value: 3 },
])

async function handleSubmit() {
  try {
    await formRef.value?.validateFields()
    submitting.value = true
    await store.handleCreateTask(toRaw(formState))
    router.push('/task')
  }
  catch {}
  finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.push('/task')
}
</script>

<template>
  <div>
    <h2 style="margin-bottom: 24px;">
      创建评测任务
    </h2>
    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 16 }"
    >
      <a-form-item label="任务名称" name="taskName">
        <a-input v-model:value="formState.taskName" placeholder="请输入任务名称" />
      </a-form-item>

      <a-form-item label="评测模型" name="modelId">
        <a-select
          v-model:value="formState.modelId"
          placeholder="请选择评测模型"
          :options="modelOptions"
        />
      </a-form-item>

      <a-form-item label="评测数据集" name="datasetId">
        <a-select
          v-model:value="formState.datasetId"
          placeholder="请选择评测数据集"
          :options="datasetOptions"
        />
      </a-form-item>

      <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
        <a-space>
          <a-button type="primary" :loading="submitting" @click="handleSubmit">
            提交
          </a-button>
          <a-button @click="handleCancel">
            取消
          </a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>
