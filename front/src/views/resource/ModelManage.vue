<script setup>
// 模型管理 - 占位页面
</script>

<template>
  <div class="model-manage-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">模型管理</h2>
        <p class="page-desc">管理大模型实例与参数配置</p>
      </div>
    </div>
    <div class="placeholder-card">
      <a-empty description="功能开发中" />
    </div>
  </div>
</template>

<style scoped>
.model-manage-page {
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

.placeholder-card {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 80px 24px;
  text-align: center;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--gray-200);
}
</style>
