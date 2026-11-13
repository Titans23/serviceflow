<script setup lang="ts">
import { computed } from 'vue'
import type { ReturnRequest } from '../api/returns'
const props = defineProps<{ request: ReturnRequest }>()
const stage = computed(() => {
  if (props.request.status === 'RECEIVED') {
    if (props.request.refundStatus === 'SUCCEEDED') return '退货退款已完成'
    if (props.request.refundStatus === 'FAILED') return '商品已收回，退款失败，等待客服处理'
    return '商品已收回，退款处理中'
  }
  return {
    REQUESTED: '等待客服审核',
    APPROVED: '审核通过，等待商家收到退货',
    REJECTED: '审核未通过',
  }[props.request.status]
})
</script>

<template>
  <section data-testid="return-progress">
    <h3>收货后退货退款</h3>
    <p>退货单号：{{ request.publicId }}</p>
    <p>商品：{{ request.productName }} · 全额退款：¥{{ request.amount }}</p>
    <p>当前进度：{{ stage }}</p>
    <p>申请原因：{{ request.reason }}</p>
    <p>申请时间：{{ request.createdAt }}</p>
    <p v-if="request.reviewNote">审核说明 / 退回指引：{{ request.reviewNote }}</p>
    <p v-if="request.reviewedAt">审核时间：{{ request.reviewedAt }}</p>
    <p v-if="request.receiptNote">验收结果：{{ request.receiptNote }}</p>
    <p v-if="request.receivedAt">收货时间：{{ request.receivedAt }}</p>
  </section>
</template>
