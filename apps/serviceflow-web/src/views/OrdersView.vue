<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

type Order = {
  orderNo: string
  status: string
  totalAmount: number
  items: { sku: string; productName: string; quantity: number; unitPrice: number }[]
  payment: { status: string; refundStatus: string } | null
  refund: { publicId: string; status: string; amount: number; attemptNo: number; failureReason: string | null; updatedAt: string } | null
}
const route = useRoute()
const orderNo = ref(String(route.query.orderNo || ''))
const order = ref<Order>()
const loading = ref(false)
const sandbox = ref(false)
const refundLabels: Record<string, string> = { NONE: '未发起退款', PROCESSING: '处理中', SUCCEEDED: '已完成', REFUNDED: '已完成', FAILED: '失败，等待处理' }

async function load() {
  if (!orderNo.value.trim()) return
  loading.value = true
  order.value = undefined
  try {
    order.value = await api<Order>(`/orders/${encodeURIComponent(orderNo.value.trim())}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '订单查询失败')
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  try {
    const info = await api<{ serviceflow?: { refundSandboxEnabled?: boolean | string } }>('/actuator/info')
    sandbox.value = String(info.serviceflow?.refundSandboxEnabled) === 'true'
  } catch {
    ElMessage.warning('暂时无法获取环境信息，请确认当前运行环境')
  }
  await load()
})
</script>

<template>
  <div class="page">
    <h1>订单与退款进度</h1>
    <el-alert v-if="sandbox" title="当前为退款演示环境，不会发生真实资金转移。" type="info" :closable="false" />
    <el-space style="margin: 20px 0">
      <el-input v-model="orderNo" placeholder="输入您的订单号" @keyup.enter="load" />
      <el-button type="primary" :loading="loading" @click="load">查询 / 刷新</el-button>
    </el-space>
    <el-card v-if="order">
      <h2>{{ order.orderNo }}</h2>
      <p>订单状态：{{ order.status }} · 订单金额：¥{{ order.totalAmount }}</p>
      <el-table :data="order.items">
        <el-table-column prop="productName" label="商品" />
        <el-table-column prop="sku" label="SKU" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="unitPrice" label="成交单价" />
      </el-table>
      <h3>退款进度</h3>
      <p>{{ refundLabels[order.refund?.status || order.payment?.refundStatus || 'NONE'] }}</p>
      <template v-if="order.refund">
        <p>退款编号：{{ order.refund.publicId }}</p>
        <p>金额：¥{{ order.refund.amount }} · 当前第 {{ order.refund.attemptNo }} 次处理</p>
        <p v-if="order.refund.failureReason">处理说明：{{ order.refund.failureReason }}</p>
        <p>更新时间：{{ order.refund.updatedAt }}</p>
      </template>
      <router-link :to="{ path: '/chat', query: { orderNo: order.orderNo } }">
        <el-button type="primary">向客服咨询此订单</el-button>
      </router-link>
    </el-card>
  </div>
</template>
