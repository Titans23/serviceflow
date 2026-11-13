<script setup lang="ts">
defineProps<{
  review: {
    publicId: string
    status: string
    progress: string
    customerMessage?: string | null
    updatedAt: string
  }
}>()
</script>

<template>
  <section data-testid="refund-review">
    <h3>退款人工核验</h3>
    <p>工单编号：{{ review.publicId }}</p>
    <router-link :to="'/tickets/' + review.publicId">查看工单与补充说明</router-link>
    <p>核验进度：{{ review.progress }}</p>
    <p v-if="review.customerMessage">客服回复：{{ review.customerMessage }}</p>
    <p>更新时间：{{ review.updatedAt }}</p>
  </section>
</template>
