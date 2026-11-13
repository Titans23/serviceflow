<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
const route = useRoute()
const admin = computed(() => route.path.startsWith('/admin/'))
const endpoint = computed(() => `${admin.value ? '/admin' : ''}/tickets/${route.params.id}`)
const detail = ref<any>()
const input = ref('')
const busy = ref(false)
let pending: { clientRequestId: string; content: string } | null = null
const labels: Record<string, string> = {
  OPEN: '待接手',
  ASSIGNED: '处理中',
  RESOLVED: '待顾客确认解决',
  CLOSED: '已关闭',
}
async function load() {
  const base = endpoint.value
  const loaded = await api(base)
  if (endpoint.value === base) detail.value = loaded
}
watch(
  () => route.path,
  async () => {
    detail.value = undefined
    input.value = ''
    pending = null
    try {
      await load()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '工单加载失败')
    }
  },
  { immediate: true },
)
async function send() {
  if (busy.value || !input.value.trim()) return
  busy.value = true
  const base = endpoint.value
  try {
    if (!pending || pending.content !== input.value.trim())
      pending = { clientRequestId: crypto.randomUUID(), content: input.value.trim() }
    await api(base + '/messages', { method: 'POST', body: JSON.stringify(pending) })
    pending = null
    input.value = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送结果待确认，可重试原消息')
  } finally {
    busy.value = false
  }
}
async function resolve() {
  if (busy.value) return
  busy.value = true
  const base = endpoint.value
  try {
    if (admin.value) {
      const target = detail.value.ticket.status === 'OPEN' ? 'ASSIGNED' : 'RESOLVED'
      let resolutionNote = null
      if (target === 'RESOLVED') {
        const result = await ElMessageBox.prompt(
          '请先向顾客发布处理结果，再填写内部处理备注。',
          '标记解决',
          {
            inputType: 'textarea',
            confirmButtonText: '保存',
            cancelButtonText: '取消',
            inputValidator: (value) =>
              (Boolean(value?.trim()) && value.length <= 500) || '请填写1到500字',
          },
        )
        resolutionNote = result.value.trim()
      }
      await api(base + '/status', {
        method: 'PUT',
        body: JSON.stringify({ status: target, resolutionNote }),
      })
    } else {
      await ElMessageBox.confirm('确认本工单问题已解决并关闭？', '确认解决', {
        confirmButtonText: '确认解决',
        cancelButtonText: '继续处理',
      })
      await api(base + '/close', { method: 'POST' })
    }
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      ElMessage.error(error instanceof Error ? error.message : '处理失败')
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page" v-if="detail">
    <h1>{{ detail.ticket.subject }}</h1>
    <p>工单 {{ detail.ticket.publicId }} · {{ labels[detail.ticket.status] }}</p>
    <p>{{ detail.ticket.description }}</p>
    <el-button :disabled="busy" @click="load">刷新进度</el-button>
    <el-button
      v-if="admin && ['OPEN', 'ASSIGNED'].includes(detail.ticket.status)"
      :disabled="busy"
      @click="resolve"
      >{{ detail.ticket.status === 'OPEN' ? '接手工单' : '标记解决' }}</el-button
    >
    <el-button
      v-if="!admin && detail.ticket.status === 'RESOLVED'"
      :disabled="busy"
      type="success"
      @click="resolve"
      >确认解决</el-button
    >
    <p>最近 200 条沟通记录</p>
    <el-card
      v-for="message in detail.messages"
      :key="message.publicId"
      class="ticket-message"
      style="margin: 12px 0"
    >
      <b>{{ message.authorRole === 'CUSTOMER' ? '顾客' : '客服' }} · {{ message.createdAt }}</b>
      <p style="white-space: pre-wrap">{{ message.content }}</p>
    </el-card>
    <el-form v-if="detail.ticket.status !== 'CLOSED'" @submit.prevent="send">
      <p v-if="!admin && detail.ticket.status === 'RESOLVED'">
        仍有问题时可继续补充，工单会重新进入处理。
      </p>
      <el-input
        v-model="input"
        type="textarea"
        maxlength="500"
        show-word-limit
        :disabled="busy"
        placeholder="填写补充说明或回复"
      />
      <el-button native-type="submit" type="primary" :loading="busy" :disabled="!input.trim()"
        >发送回复</el-button
      >
    </el-form>
  </div>
</template>
