<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { api, stream } from '../api/client'
import { useAuthStore } from '../stores/auth'

const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })

const auth = useAuthStore(),
  route = useRoute(),
  sessions = ref<any[]>([]),
  current = ref(''),
  messages = ref<any[]>([]),
  input = ref(''),
  sending = ref(false),
  box = ref<HTMLElement>(),
  followOutput = ref(true),
  action = ref<any>(),
  unverifiedAction = ref<any>(),
  recoveredCancellation = ref<any>(),
  confirmationUnknown = ref(false)
let pendingAssistant: any
let pendingSession = ''
const productId = route.query.productId ? Number(route.query.productId) : undefined
async function refresh() {
  sessions.value = await api('/chat/sessions')
}
async function create() {
  const session = await api<any>('/chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ title: productId ? '商品咨询' : '新会话' }),
  })
  await refresh()
  await select(session.publicId)
}
async function select(id: string) {
  current.value = id
  messages.value = await api(`/chat/sessions/${id}/messages`)
  followOutput.value = true
  await nextTick()
  scroll(true)
}
async function send() {
  if (!input.value.trim() || sending.value) return
  if (!current.value) await create()
  const text = input.value
  input.value = ''
  followOutput.value = true
  messages.value.push({ role: 'USER', content: text })
  const assistant: any = { role: 'ASSISTANT', content: '', comparison: null }
  messages.value.push(assistant)
  sending.value = true
  try {
    await stream(
      `/chat/sessions/${current.value}/messages/stream`,
      {
        message: text,
        clientRequestId: crypto.randomUUID(),
        pageContext: productId ? { productId } : null,
      },
      (name, data) => {
        if (name === 'token') assistant.content += data
        if (name === 'meta') {
          assistant.citations = data.citations
          assistant.degraded = data.degraded
        }
        if (name === 'product_selection_required') assistant.candidates = data
        if (name === 'product_comparison') assistant.comparison = data
        if (name === 'order') assistant.order = data
        if (name === 'action_required') action.value = data
        if (name === 'ticket') ElMessage.success(`工单 ${data.publicId} 已创建`)
        if (name === 'error') throw new Error(data.message)
        nextTick(scroll)
      },
    )
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    sending.value = false
  }
}
async function askCandidate(candidate: any) {
  input.value = `请介绍 ${candidate.model}`
  await send()
}
async function confirm(decision: string) {
  if (!action.value || sending.value || confirmationUnknown.value) return
  const pending = action.value
  sending.value = true
  followOutput.value = true
  const assistant: any = { role: 'ASSISTANT', content: '' }
  messages.value.push(assistant)
  let completed = false
  try {
    await stream(`/chat/actions/${pending.actionId}/confirm`, { decision }, (name, data) => {
      if (name === 'token') assistant.content += data
      if (name === 'ticket') ElMessage.success(`工单 ${data.publicId} 已创建`)
      if (name === 'error') throw new Error(data.message)
      if (name === 'done') completed = true
      nextTick(scroll)
    })
    if (!completed) throw new Error('确认响应未完整接收')
    action.value = null
    if (decision === 'CONFIRM' && pending.actionType === 'CANCEL_ORDER') {
      assistant.order = await api(`/orders/${encodeURIComponent(pending.orderNo)}`)
    }
  } catch (error) {
    if (decision === 'CONFIRM' && pending.actionType === 'CANCEL_ORDER') {
      unverifiedAction.value = pending
      action.value = null
      pendingAssistant = assistant
      pendingSession = current.value
      assistant.content = '确认响应不完整，操作结果待核验。'
      confirmationUnknown.value = true
      await readCancellationResult()
    } else {
      ElMessage.error(error instanceof Error ? error.message : '确认操作失败')
    }
  } finally {
    sending.value = false
  }
}
async function readCancellationResult() {
  if (!unverifiedAction.value || !confirmationUnknown.value) return
  sending.value = true
  try {
    const result = await api<any>(
      `/chat/actions/${encodeURIComponent(unverifiedAction.value.actionId)}/result`,
    )
    recoveredCancellation.value = result
    if (current.value === pendingSession && messages.value.includes(pendingAssistant)) {
      pendingAssistant.content = '本次核验已完成，请查看订单结果卡。'
    }
    unverifiedAction.value = null
    confirmationUnknown.value = false
    pendingAssistant = undefined
    pendingSession = ''
  } catch {
    ElMessage.warning('结果尚未核实，查询不到不代表未执行。请再次核验，不要重新提交取消。')
  } finally {
    sending.value = false
    nextTick(scroll)
  }
}
function scroll(force = false) {
  if (box.value && (force || followOutput.value)) {
    box.value.scrollTop = box.value.scrollHeight
  }
}

function updateScrollIntent() {
  if (!box.value) return
  const distanceFromBottom = box.value.scrollHeight - box.value.scrollTop - box.value.clientHeight
  followOutput.value = distanceFromBottom < 80
}

function renderMarkdown(content: string) {
  return markdown.render(content)
}
onMounted(async () => {
  await auth.ensureGuest()
  await refresh()
  if (sessions.value.length) await select(sessions.value[0].publicId)
  else await create()
  if (typeof route.query.orderNo === 'string') input.value = `查询订单 ${route.query.orderNo}`
})
</script>
<template>
  <div class="page chat-layout">
    <aside class="sessions">
      <el-button type="primary" style="width: 100%; margin-bottom: 12px" @click="create"
        >新建会话</el-button
      >
      <div
        v-for="s in sessions"
        :key="s.publicId"
        class="session"
        :class="{ active: current === s.publicId }"
        @click="select(s.publicId)"
      >
        {{ s.title }}
      </div>
    </aside>
    <section class="conversation">
      <div ref="box" class="messages" @scroll="updateScrollIntent">
        <div v-for="(m, index) in messages" :key="m.id ?? index" :class="['message', m.role]">
          <!-- markdown-it has raw HTML disabled, so this only receives escaped/generated markup. -->
          <!-- eslint-disable vue/no-v-html -->
          <div
            v-if="m.role === 'ASSISTANT'"
            class="markdown"
            v-html="renderMarkdown(m.content)"
          ></div>
          <!-- eslint-enable vue/no-v-html -->
          <div v-else>{{ m.content }}</div>
          <el-alert
            v-if="m.degraded"
            title="检索服务已降级，以下内容来自融合检索结果"
            type="warning"
            :closable="false"
          />
          <div v-if="m.candidates?.length">
            <el-button v-for="p in m.candidates" :key="p.id" @click="askCandidate(p)"
              >{{ p.name }}（{{ p.model }}）</el-button
            >
          </div>
          <table v-if="m.comparison" class="spec-table">
            <thead>
              <tr>
                <th>项目</th>
                <th v-for="p in m.comparison.products" :key="p.id">{{ p.name }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in m.comparison.fields" :key="f">
                <th>{{ f }}</th>
                <td v-for="p in m.comparison.products" :key="p.id">
                  {{ p.specs[f] ?? '暂无数据' }}
                </td>
              </tr>
            </tbody>
          </table>
          <el-card v-if="m.order" shadow="never"
            ><strong>订单 {{ m.order.orderNo }}</strong>
            <p>状态：{{ m.order.status }} · 金额：¥{{ m.order.totalAmount }}</p>
            <router-link :to="{ path: '/orders', query: { orderNo: m.order.orderNo } }"
              >查看订单与退款进度</router-link
            ></el-card
          >
          <div v-if="m.citations?.length" class="muted">引用：{{ m.citations.join('、') }}</div>
        </div>
        <div v-if="recoveredCancellation" class="action-card" data-testid="cancellation-result">
          <strong>已核验操作结果：{{ recoveredCancellation.operation.resultMessage }}</strong>
          <p>
            订单 {{ recoveredCancellation.order.orderNo }} · 状态
            {{ recoveredCancellation.order.status }}
          </p>
          <router-link
            :to="{ path: '/orders', query: { orderNo: recoveredCancellation.order.orderNo } }"
            >查看核验订单详情</router-link
          >
        </div>
        <div v-if="unverifiedAction" class="action-card">
          <strong>取消订单 {{ unverifiedAction.orderNo }} 的结果待核验</strong>
          <p>结果待核验。操作标识：{{ unverifiedAction.actionId }}</p>
          <p>
            查询不到不代表未执行。可继续咨询或查看订单，不会自动重发取消。刷新或离开本页前请保存操作标识。
          </p>
          <div style="margin-top: 10px">
            <el-button type="primary" :loading="sending" @click="readCancellationResult"
              >再次核验结果</el-button
            >
            <router-link
              :to="{ path: '/orders', query: { orderNo: unverifiedAction.orderNo } }"
              target="_blank"
              rel="noopener"
              >查看订单状态</router-link
            >
          </div>
        </div>
        <div v-if="action" class="action-card">
          <strong>{{ action.message }}</strong>
          <p v-if="confirmationUnknown">请先核验上一次取消结果，再确认新的操作。</p>
          <div style="margin-top: 10px">
            <el-button
              type="primary"
              :disabled="sending || confirmationUnknown"
              @click="confirm('CONFIRM')"
              >确认</el-button
            ><el-button :disabled="sending || confirmationUnknown" @click="confirm('REJECT')"
              >取消</el-button
            >
          </div>
        </div>
      </div>
      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="咨询商品、订单、物流、退款或售后政策"
          @keydown.ctrl.enter="send"
        /><el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </div>
    </section>
  </div>
</template>
