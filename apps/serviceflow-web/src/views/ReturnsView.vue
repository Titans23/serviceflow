<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import type { ReturnRequest } from '../api/returns'
import ReturnProgress from '../components/ReturnProgress.vue'

const requests = ref<ReturnRequest[]>([])
const status = ref('')
const loading = ref(false)
const busy = ref(false)
const labels = { REQUESTED: '待审核', APPROVED: '待收货', REJECTED: '已拒绝', RECEIVED: '已收货' }
async function load() {
  loading.value = true
  try {
    requests.value = await api(`/admin/returns${status.value ? '?status=' + status.value : ''}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}
async function decide(request: ReturnRequest, decision: 'APPROVE' | 'REJECT' | 'RECEIVE') {
  if (busy.value) return
  busy.value = true
  try {
    const title =
      decision === 'RECEIVE'
        ? '确认收货并退款'
        : decision === 'APPROVE'
          ? '通过退货审核'
          : '拒绝退货申请'
    const message =
      decision === 'RECEIVE'
        ? `确认已收到并验收 ${request.productName}，将启动 ¥${request.amount} 全额退款。请填写顾客可见的验收结果。`
        : decision === 'APPROVE'
          ? '请填写顾客可见的退回地址、联系人或到店交回方式。审核通过后等待商品退回。'
          : '请填写顾客可见的拒绝原因。'
    const result = await ElMessageBox.prompt(message, title, {
      inputType: 'textarea',
      confirmButtonText: decision === 'RECEIVE' ? '确认已收货，开始退款' : '提交审核结果',
      cancelButtonText: '取消',
      inputValidator: (value) =>
        (Boolean(value?.trim()) && value.length <= 500) || '请填写1到500字',
    })
    await api(
      `/admin/returns/${request.publicId}/${decision === 'RECEIVE' ? 'receive' : 'review'}`,
      {
        method: 'POST',
        body: JSON.stringify({
          ...(decision === 'RECEIVE' ? {} : { decision }),
          note: result.value.trim(),
        }),
      },
    )
    ElMessage.success('处理结果已保存')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      ElMessage.error(error instanceof Error ? error.message : '结果待核验，请刷新列表')
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <h1>退货审核与收货</h1>
    <p>核对申请后提供退回指引。商品实际收回并验收后，再确认收货并发起全额退款。</p>
    <el-select
      v-model="status"
      clearable
      placeholder="全部状态"
      style="width: 160px"
      @change="load"
    >
      <el-option v-for="(label, value) in labels" :key="value" :value="value" :label="label" />
    </el-select>
    <el-button @click="load">刷新退货列表</el-button>
    <p>按申请时间展示最近 200 笔，可筛选待审核或待收货。</p>
    <el-card
      v-for="request in requests"
      :key="request.publicId"
      class="return-case"
      style="margin-top: 16px"
    >
      <h2>订单 {{ request.orderNo }}</h2>
      <ReturnProgress :request="request" />
      <el-space v-if="request.status === 'REQUESTED'">
        <el-button type="primary" :disabled="busy" @click="decide(request, 'APPROVE')"
          >通过审核</el-button
        >
        <el-button type="danger" :disabled="busy" @click="decide(request, 'REJECT')"
          >拒绝申请</el-button
        >
      </el-space>
      <el-button
        v-if="request.status === 'APPROVED'"
        type="primary"
        :disabled="busy"
        @click="decide(request, 'RECEIVE')"
        >确认收货并退款</el-button
      >
      <router-link v-if="request.status === 'RECEIVED'" to="/admin/refunds"
        >查看退款渠道进度</router-link
      >
    </el-card>
    <el-empty v-if="!requests.length" description="暂无退货申请" />
  </div>
</template>
