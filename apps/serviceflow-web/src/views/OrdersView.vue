<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

type Order = {
  orderNo: string
  status: string
  totalAmount: number
  createdAt: string
  items: { sku: string; productName: string; quantity: number; unitPrice: number }[]
  payment: { status: string; refundStatus: string } | null
  refund: {
    publicId: string
    status: string
    amount: number
    attemptNo: number
    failureReason: string | null
    updatedAt: string
  } | null
}
type OrderPage = { items: Order[]; nextCursor: number | null }
const route = useRoute()
const router = useRouter()
const orderNo = ref(String(route.query.orderNo || ''))
const order = ref<Order>()
const orders = ref<Order[]>([])
const nextCursor = ref<number | null>(null)
const keyword = ref('')
const status = ref('')
const dates = ref<string[] | null>(null)
const loading = ref(false)
const listLoading = ref(false)
const listError = ref('')
let appliedQuery = new URLSearchParams({ size: '10' })
const sandbox = ref(false)
const orderLabels: Record<string, string> = {
  CREATED: '待付款',
  PAID: '已付款',
  PROCESSING: '处理中',
  SHIPPED: '已发货',
  DELIVERED: '已送达',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}
const refundLabels: Record<string, string> = {
  NONE: '未发起退款',
  PROCESSING: '处理中',
  SUCCEEDED: '已完成',
  REFUNDED: '已完成',
  FAILED: '失败，等待处理',
}

async function loadList(more = false) {
  if (listLoading.value) return
  listLoading.value = true
  listError.value = ''
  const query = more ? new URLSearchParams(appliedQuery) : new URLSearchParams({ size: '10' })
  if (!more) {
    if (keyword.value.trim()) query.set('keyword', keyword.value.trim())
    if (status.value) query.set('status', status.value)
    if (dates.value?.length === 2) {
      query.set('from', dates.value[0])
      query.set('to', dates.value[1])
    }
  }
  if (more && nextCursor.value) query.set('cursor', String(nextCursor.value))
  try {
    const result = await api<OrderPage>(`/orders?${query}`)
    if (!more) appliedQuery = new URLSearchParams(query)
    orders.value = more ? [...orders.value, ...result.items] : result.items
    nextCursor.value = result.nextCursor
  } catch (error) {
    listError.value = error instanceof Error ? error.message : '订单列表加载失败'
  } finally {
    listLoading.value = false
  }
}
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
async function openOrder(value: string) {
  orderNo.value = value
  await load()
  await router.replace({ query: { orderNo: value } })
}
watch(
  () => route.query.orderNo,
  (value) => {
    if (value && value !== orderNo.value) {
      orderNo.value = String(value)
      void load()
    }
  },
)
onMounted(async () => {
  await loadList()
  try {
    const info = await api<{ serviceflow?: { refundSandboxEnabled?: boolean | string } }>(
      '/actuator/info',
    )
    sandbox.value = String(info.serviceflow?.refundSandboxEnabled) === 'true'
  } catch {
    ElMessage.warning('暂时无法获取环境信息，请确认当前运行环境')
  }
  await load()
})
</script>

<template>
  <div class="page">
    <h1>我的订单</h1>
    <el-alert
      v-if="sandbox"
      title="当前为退款演示环境，不会发生真实资金转移。"
      type="info"
      :closable="false"
    />
    <el-form inline style="margin-top: 20px" @submit.prevent="loadList()">
      <el-form-item label="商品或订单">
        <el-input
          v-model="keyword"
          maxlength="80"
          placeholder="商品名称、型号或订单号"
          clearable
          :disabled="listLoading"
        />
      </el-form-item>
      <el-form-item label="订单状态">
        <el-select
          v-model="status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          :disabled="listLoading"
        >
          <el-option
            v-for="(label, value) in orderLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="下单日期">
        <el-date-picker
          v-model="dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          :disabled="listLoading"
        />
      </el-form-item>
      <el-form-item
        ><el-button native-type="submit" type="primary" :loading="listLoading"
          >筛选 / 刷新列表</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <el-empty v-else-if="!listLoading && !orders.length" description="没有找到符合条件的订单" />
    <el-card
      v-for="item in orders"
      :key="item.orderNo"
      shadow="never"
      style="margin: 12px 0"
      class="order-summary"
    >
      <strong>{{ item.orderNo }}</strong>
      <p>
        {{ orderLabels[item.status] || item.status }} · ¥{{ item.totalAmount }} ·
        {{ new Date(item.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}
      </p>
      <p v-for="product in item.items" :key="product.sku">
        {{ product.productName }} × {{ product.quantity }}
      </p>
      <el-space>
        <el-button @click="openOrder(item.orderNo)" :disabled="loading">查看详情</el-button>
        <router-link :to="{ path: '/chat', query: { orderNo: item.orderNo } }"
          ><el-button type="primary">咨询这笔订单</el-button></router-link
        >
      </el-space>
    </el-card>
    <el-button v-if="nextCursor" :loading="listLoading" @click="loadList(true)"
      >加载更多订单</el-button
    >

    <h2>订单详情与退款进度</h2>
    <el-space style="margin: 8px 0 20px">
      <el-input v-model="orderNo" placeholder="输入您的订单号" @keyup.enter="load" />
      <el-button type="primary" :loading="loading" @click="load">查询 / 刷新</el-button>
    </el-space>
    <el-card v-if="order" data-testid="order-detail">
      <h2>{{ order.orderNo }}</h2>
      <p>
        订单状态：{{ orderLabels[order.status] || order.status }} · 订单金额：¥{{
          order.totalAmount
        }}
      </p>
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
