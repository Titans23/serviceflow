<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
const tickets = ref<any[]>([])
const loading = ref(false)
const labels: Record<string, string> = {
  OPEN: '待接手',
  ASSIGNED: '处理中',
  RESOLVED: '待确认解决',
  CLOSED: '已关闭',
}
async function load() {
  loading.value = true
  try {
    tickets.value = await api('/tickets')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工单加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="page" v-loading="loading">
    <h1>我的工单</h1>
    <p>查看最近 200 张工单，补充问题、阅读客服回复，并确认问题是否解决。</p>
    <el-button @click="load">刷新工单</el-button>
    <el-card v-for="ticket in tickets" :key="ticket.publicId" style="margin-top: 16px">
      <h2>{{ ticket.subject }}</h2>
      <p>工单 {{ ticket.publicId }} · {{ labels[ticket.status] }}</p>
      <router-link :to="'/tickets/' + ticket.publicId">查看与回复</router-link>
    </el-card>
    <el-empty v-if="!tickets.length" description="暂无工单，可从智能客服发起人工协助" />
  </div>
</template>
