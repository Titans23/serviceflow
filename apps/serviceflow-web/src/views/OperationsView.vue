<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'

type TicketStatus = 'OPEN' | 'ASSIGNED' | 'RESOLVED' | 'CLOSED'
type Ticket = {
  publicId: string
  customerName: string
  subject: string
  description: string
  status: TicketStatus
  assignedTo?: string
  resolutionNote?: string
  createdAt: string
  updatedAt: string
}
type Audit = {
  publicId: string
  customerName: string
  modelName: string
  promptVersion: string
  intent: string
  productIdsJson: string
  citationsJson: string
  degraded: boolean
  latencyMs: number
  resultStatus: string
  errorCode?: string
  question: string
  answer?: string
  createdAt: string
}

const tickets = ref<Ticket[]>([])
const audits = ref<Audit[]>([])
const statusFilter = ref('')
const loading = ref(false)

const nextStatus: Record<TicketStatus, TicketStatus | undefined> = {
  OPEN: 'ASSIGNED',
  ASSIGNED: 'RESOLVED',
  RESOLVED: 'CLOSED',
  CLOSED: undefined,
}
const actionLabel: Record<TicketStatus, string> = {
  OPEN: '领取工单',
  ASSIGNED: '标记已解决',
  RESOLVED: '关闭工单',
  CLOSED: '已关闭',
}
const query = computed(() => (statusFilter.value ? `?status=${statusFilter.value}` : ''))

async function load() {
  loading.value = true
  try {
    ;[tickets.value, audits.value] = await Promise.all([
      api<Ticket[]>(`/admin/tickets${query.value}`),
      api<Audit[]>('/admin/ai-audits?limit=100'),
    ])
  } finally {
    loading.value = false
  }
}

async function advance(ticket: Ticket) {
  const target = nextStatus[ticket.status]
  if (!target) return
  let note = ''
  if (target === 'RESOLVED' || target === 'CLOSED') {
    const result = await ElMessageBox.prompt(
      '填写处理结果，便于审计和用户追踪',
      actionLabel[ticket.status],
      {
        inputValidator: (value) => Boolean(value?.trim()) || '处理结果不能为空',
        inputType: 'textarea',
      },
    )
    note = result.value.trim()
  }
  await api(`/admin/tickets/${ticket.publicId}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status: target, resolutionNote: note || null }),
  })
  ElMessage.success(`工单已更新为 ${target}`)
  await load()
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <h1>运营中心</h1>
    <p>模拟企业客服的工单闭环与 AI 回答审计，访客聊天内容不会写入审计库。</p>

    <el-tabs>
      <el-tab-pane label="人工工单">
        <div class="toolbar">
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="全部状态"
            style="width: 180px"
            @change="load"
          >
            <el-option
              v-for="status in Object.keys(nextStatus)"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
          <el-button @click="load">刷新</el-button>
        </div>
        <el-table :data="tickets" row-key="publicId">
          <el-table-column type="expand">
            <template #default="scope">
              <div class="detail"><b>问题描述：</b>{{ scope.row.description }}</div>
              <div class="detail"><b>处理备注：</b>{{ scope.row.resolutionNote || '暂无' }}</div>
              <div class="detail"><b>工单编号：</b>{{ scope.row.publicId }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="subject" label="主题" min-width="160" />
          <el-table-column prop="customerName" label="客户" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="assignedTo" label="处理人" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
          <el-table-column label="操作" width="140">
            <template #default="scope">
              <el-button
                v-if="nextStatus[scope.row.status as TicketStatus]"
                type="primary"
                link
                @click="advance(scope.row)"
              >
                {{ actionLabel[scope.row.status as TicketStatus] }}
              </el-button>
              <span v-else>流程完成</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="AI 回答审计">
        <el-table :data="audits" row-key="publicId">
          <el-table-column type="expand">
            <template #default="scope">
              <div class="detail"><b>用户问题：</b>{{ scope.row.question }}</div>
              <div class="detail">
                <b>最终回答：</b>
                <pre>{{ scope.row.answer || scope.row.errorCode }}</pre>
              </div>
              <div class="detail">
                <b>商品 ID：</b>
                <pre>{{ formatJson(scope.row.productIdsJson) }}</pre>
              </div>
              <div class="detail">
                <b>引用：</b>
                <pre>{{ formatJson(scope.row.citationsJson) }}</pre>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" width="110" />
          <el-table-column prop="intent" label="意图" min-width="140" />
          <el-table-column prop="modelName" label="模型" min-width="150" />
          <el-table-column prop="promptVersion" label="Prompt" width="120" />
          <el-table-column prop="latencyMs" label="耗时(ms)" width="110" />
          <el-table-column prop="resultStatus" label="结果" width="100" />
          <el-table-column label="降级" width="80">
            <template #default="scope">{{ scope.row.degraded ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.detail {
  padding: 6px 48px;
  white-space: pre-wrap;
}
pre {
  margin: 6px 0 0;
  white-space: pre-wrap;
  font-family: inherit;
}
</style>
