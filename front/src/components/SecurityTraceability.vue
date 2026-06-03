<script setup lang="ts">
import type { Component } from 'vue'
import {
  ApiOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  FunnelPlotOutlined,
  NodeIndexOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { computed } from 'vue'

export interface SecurityTrace {
  traceId: string
  inputPrompt: string
  timestamp: string
  totalLatency: string
  steps: {
    l1_ac_automaton: {
      status: 'PASS' | 'HIT' | 'FAIL'
      latency: string
      matchedKeywords: string[]
      message: string
    }
    l2_recall: {
      status: 'PASS' | 'HIT' | 'FAIL'
      latency: string
      esScore: number
      milvusScore: number
      milvusThreshold: number
      rrfResult: string
      message: string
    }
    l3_judge_llm: {
      status: 'PASS' | 'HIT' | 'FAIL'
      model: string
      latency: string
      decision: string
      chainOfThought: string
    }
  }
}

interface TraceStepCard {
  key: string
  title: string
  subtitle: string
  status: 'PASS' | 'HIT' | 'FAIL'
  latency: string
  icon: Component
  metrics: Array<{ label: string, value: string }>
  message: string
}

const props = defineProps<{
  trace: SecurityTrace
}>()

const stepCards = computed<TraceStepCard[]>(() => [
  {
    key: 'l1',
    title: 'L1 AC 自动机',
    subtitle: '极速字典拦截',
    status: props.trace.steps.l1_ac_automaton.status,
    latency: props.trace.steps.l1_ac_automaton.latency,
    icon: ThunderboltOutlined,
    metrics: [
      {
        label: '命中关键词',
        value: props.trace.steps.l1_ac_automaton.matchedKeywords.length
          ? props.trace.steps.l1_ac_automaton.matchedKeywords.join('、')
          : '无',
      },
    ],
    message: props.trace.steps.l1_ac_automaton.message,
  },
  {
    key: 'l2',
    title: 'L2 双路召回',
    subtitle: 'ES + Milvus 语义检索',
    status: props.trace.steps.l2_recall.status,
    latency: props.trace.steps.l2_recall.latency,
    icon: NodeIndexOutlined,
    metrics: [
      { label: 'ES 得分', value: String(props.trace.steps.l2_recall.esScore) },
      { label: 'Milvus 得分', value: String(props.trace.steps.l2_recall.milvusScore) },
      { label: '阈值', value: String(props.trace.steps.l2_recall.milvusThreshold) },
      { label: 'RRF 命中', value: props.trace.steps.l2_recall.rrfResult },
    ],
    message: props.trace.steps.l2_recall.message,
  },
  {
    key: 'l3',
    title: 'L3 Judge LLM',
    subtitle: '大模型裁决',
    status: props.trace.steps.l3_judge_llm.status,
    latency: props.trace.steps.l3_judge_llm.latency,
    icon: AuditOutlined,
    metrics: [
      { label: '裁决模型', value: props.trace.steps.l3_judge_llm.model },
      { label: '裁决结果', value: props.trace.steps.l3_judge_llm.decision },
    ],
    message: props.trace.steps.l3_judge_llm.chainOfThought,
  },
])

function getStatusText(status: TraceStepCard['status']) {
  const statusMap: Record<TraceStepCard['status'], string> = {
    PASS: '放行',
    HIT: '命中',
    FAIL: '失败',
  }
  return statusMap[status]
}

function getStatusClass(status: TraceStepCard['status']) {
  return `status-${status.toLowerCase()}`
}
</script>

<template>
  <div class="trace-card">
    <div class="trace-header">
      <div class="trace-title-wrap">
        <div class="trace-icon-wrap">
          <FunnelPlotOutlined class="trace-icon" />
        </div>
        <div>
          <h3 class="trace-title">
            链路溯源透视工具
          </h3>
          <p class="trace-desc">
            AC 自动机 -> 双路召回 -> Judge LLM 三层漏斗全景
          </p>
        </div>
      </div>
      <div class="trace-meta">
        <span class="trace-id">{{ trace.traceId }}</span>
        <span class="trace-time">
          <ClockCircleOutlined />
          {{ trace.totalLatency }}
        </span>
      </div>
    </div>

    <div class="prompt-panel">
      <div class="prompt-label">
        <ApiOutlined />
        攻击 Prompt
      </div>
      <div class="prompt-text">
        {{ trace.inputPrompt }}
      </div>
      <div class="prompt-footer">
        <span>触发时间：{{ trace.timestamp }}</span>
      </div>
    </div>

    <div class="funnel-grid">
      <div
        v-for="(step, index) in stepCards"
        :key="step.key"
        class="step-card"
      >
        <div class="step-card-header">
          <div class="step-title-group">
            <div class="step-icon-wrap" :class="getStatusClass(step.status)">
              <component :is="step.icon" class="step-icon" />
            </div>
            <div>
              <div class="step-title">
                {{ step.title }}
              </div>
              <div class="step-subtitle">
                {{ step.subtitle }}
              </div>
            </div>
          </div>
          <a-tag class="status-tag" :class="getStatusClass(step.status)">
            <CheckCircleOutlined v-if="step.status !== 'FAIL'" />
            <CloseCircleOutlined v-else />
            {{ getStatusText(step.status) }}
          </a-tag>
        </div>

        <div class="step-latency">
          <ClockCircleOutlined />
          <span>耗时 {{ step.latency }}</span>
        </div>

        <div class="metric-list">
          <div v-for="metric in step.metrics" :key="metric.label" class="metric-row">
            <span class="metric-label">{{ metric.label }}</span>
            <span class="metric-value">{{ metric.value }}</span>
          </div>
        </div>

        <div class="step-message">
          {{ step.message }}
        </div>

        <div v-if="index < stepCards.length - 1" class="funnel-arrow">
          进入下一层
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trace-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 18px;
}

.trace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.trace-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trace-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e6f4ff;
  flex-shrink: 0;
}

.trace-icon {
  color: #1677ff;
  font-size: 20px;
}

.trace-title {
  color: #262626;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.trace-desc {
  color: #8c8c8c;
  font-size: 12px;
}

.trace-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.trace-id {
  color: #1677ff;
  font-size: 12px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: #e6f4ff;
  border-radius: 4px;
  padding: 3px 8px;
}

.trace-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #595959;
  font-size: 12px;
}

.prompt-panel {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 16px;
}

.prompt-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #1677ff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.prompt-text {
  color: #262626;
  font-size: 13px;
  line-height: 1.7;
}

.prompt-footer {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.funnel-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.step-card {
  position: relative;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
}

.step-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.step-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.step-icon-wrap {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-icon-wrap.status-pass {
  background: #f6ffed;
  color: #52c41a;
}

.step-icon-wrap.status-hit {
  background: #fff7e6;
  color: #faad14;
}

.step-icon-wrap.status-fail {
  background: #fff2f0;
  color: #ff4d4f;
}

.step-icon {
  font-size: 16px;
}

.step-title {
  color: #262626;
  font-size: 14px;
  font-weight: 600;
}

.step-subtitle {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 2px;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-inline-end: 0;
  border-radius: 4px;
}

.status-tag.status-pass {
  color: #52c41a;
  background: #f6ffed;
  border-color: #b7eb8f;
}

.status-tag.status-hit {
  color: #d48806;
  background: #fff7e6;
  border-color: #ffd591;
}

.status-tag.status-fail {
  color: #ff4d4f;
  background: #fff2f0;
  border-color: #ffccc7;
}

.step-latency {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #595959;
  font-size: 12px;
  margin-bottom: 10px;
}

.metric-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
}

.metric-label {
  color: #8c8c8c;
  flex-shrink: 0;
}

.metric-value {
  color: #262626;
  font-weight: 500;
  text-align: right;
  overflow-wrap: anywhere;
}

.step-message {
  color: #595959;
  font-size: 12px;
  line-height: 1.6;
  background: #fafafa;
  border-radius: 8px;
  padding: 10px;
}

.funnel-arrow {
  position: absolute;
  right: -8px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  color: #8c8c8c;
  font-size: 11px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 999px;
  padding: 2px 6px;
}

@media (max-width: 960px) {
  .trace-header {
    flex-direction: column;
  }

  .trace-meta {
    justify-content: flex-start;
  }

  .funnel-grid {
    grid-template-columns: 1fr;
  }

  .funnel-arrow {
    display: none;
  }
}
</style>
