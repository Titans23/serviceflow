<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'

type Refund = { publicId: string; orderNo: string; amount: number; status: string; attemptNo: number; failureReason: string | null; updatedAt: string }
const refunds = ref<Refund[]>([])
const sandbox = ref(false)
const busy = ref('')

async function load() {
  refunds.value = await api<Refund[]>('/admin/refunds?limit=100')
}
async function apply(refund: Refund, outcome: 'SUCCEEDED' | 'FAILED') {
  if (busy.value) return
  busy.value = refund.publicId
  try {
    let reason = ''
    if (outcome === 'FAILED') {
      const result = await ElMessageBox.prompt('填写本次模拟失败的原因', '模拟退款失败', {
        confirmButtonText: '确定', cancelButtonText: '取消',
        inputValidator: (value) => Boolean(value?.trim()) && value.trim().length <= 500 || '请填写 1–500 字原因',
      })
      reason = result.value.trim()
    } else {
      await ElMessageBox.confirm('将此沙箱退款任务标记为成功，不会转移真实资金。', '模拟成功回调', { confirmButtonText: '确定', cancelButtonText: '取消' })
    }
    await api(`/admin/refunds/${refund.publicId}/sandbox-events`, {
      method: 'POST', body: JSON.stringify({ eventId: crypto.randomUUID(), attemptNo: refund.attemptNo, outcome, amount: refund.amount, reason }),
    })
    ElMessage.success('模拟回调已处理')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '处理失败')
  } finally {
    busy.value = ''
  }
}
async function retry(refund: Refund) {
  if (busy.value) return
  busy.value = refund.publicId
  try {
    await api(`/admin/refunds/${refund.publicId}/retry`, { method: 'POST', body: JSON.stringify({ expectedAttemptNo: refund.attemptNo }) })
    ElMessage.success('已进入下一次处理，等待新的模拟回调')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败')
  } finally {
    busy.value = ''
  }
}
onMounted(async () => {
  try {
    const info = await api<{ serviceflow?: { refundSandboxEnabled?: boolean | string } }>('/actuator/info')
    sandbox.value = String(info.serviceflow?.refundSandboxEnabled) === 'true'
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退款记录加载失败')
  }
})
</script>

<template>
  <div class="page">
    <h1>退款处理</h1>
    <el-alert :title="sandbox ? '沙箱回调已启用，仅模拟处理结果，不转移真实资金。' : '沙箱回调未启用，此页面仅查看退款记录。'" type="info" :closable="false" />
    <el-button style="margin: 16px 0" @click="load">刷新记录</el-button>
    <el-table :data="refunds" row-key="publicId">
      <el-table-column prop="orderNo" label="订单" min-width="180" />
      <el-table-column prop="amount" label="退款金额" />
      <el-table-column prop="status" label="状态" min-width="130" />
      <el-table-column prop="attemptNo" label="处理次数" />
      <el-table-column prop="failureReason" label="失败说明" min-width="160" />
      <el-table-column v-if="sandbox" label="沙箱操作" min-width="240">
        <template #default="scope">
          <template v-if="scope.row.status === 'PROCESSING'">
            <el-button :disabled="Boolean(busy)" @click="apply(scope.row, 'SUCCEEDED')">模拟成功</el-button>
            <el-button :disabled="Boolean(busy)" @click="apply(scope.row, 'FAILED')">模拟失败</el-button>
          </template>
          <el-button v-if="scope.row.status === 'FAILED'" :disabled="Boolean(busy)" @click="retry(scope.row)">重试退款</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
