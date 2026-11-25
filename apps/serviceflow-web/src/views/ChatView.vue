<script setup lang="ts">
import RefundReview from '../components/RefundReview.vue'
import ReturnProgress from '../components/ReturnProgress.vue'
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { api, stream, StreamRequestError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import {
  clearPendingCancellation,
  currentCancellationOwner,
  loadPendingCancellation,
  savePendingCancellation,
  type PendingCancellation,
} from '../stores/pendingCancellation'

const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })

type ComparisonProduct = { id: number; name: string; sku: string }
type PendingComparison = {
  comparisonId: string
  targets: { label: string; confirmed: ComparisonProduct | null; candidates: ComparisonProduct[] }[]
}
type ComparisonSelection = {
  comparisonId: string
  targetIndex?: number
  productId?: number
  term?: string
  compareConfirmed?: boolean
}
type OrderSelection = { queryId: string; orderNo: string }
type ChatTurn = {
  sessionId: string
  owner: string | null
  assistant: any
  request: {
    message: string
    clientRequestId: string
    pageContext: { orderNo: string } | { productId: number } | null
    orderSelection?: OrderSelection
    selectedProductId?: number
    comparisonSelection?: ComparisonSelection
  }
}

const auth = useAuthStore(),
  route = useRoute(),
  router = useRouter(),
  sessions = ref<any[]>([]),
  current = ref(''),
  messages = ref<any[]>([]),
  input = ref(''),
  sending = ref(false),
  sessionBusy = ref(false),
  composer = ref<InputInstance>(),
  box = ref<HTMLElement>(),
  followOutput = ref(true),
  action = ref<any>(),
  unverifiedAction = ref<any>(),
  recoveredCancellation = ref<any>(),
  recoveryStorageWarning = ref(''),
  confirmationUnknown = ref(false)
let pendingAssistant: any
let pendingSession = ''
let productId = route.query.productId ? Number(route.query.productId) : undefined
let entryOrderNo = typeof route.query.orderNo === 'string' ? route.query.orderNo : undefined
const orderContext = ref<any>()
let entryPageContext: { sessionId: string; productId: number } | null = null
const comparisonTerms = ref<Record<string, string>>({})
const retryableTurn = shallowRef<ChatTurn | null>(null)
let chatStream: InstanceType<typeof globalThis.AbortController> | undefined
let disposed = false

function clearChatTurn() {
  retryableTurn.value = null
  chatStream?.abort()
}

onBeforeUnmount(() => {
  disposed = true
  clearChatTurn()
})
async function refresh() {
  sessions.value = await api('/chat/sessions')
}
async function create() {
  const session = await api<any>('/chat/sessions', {
    method: 'POST',
    body: JSON.stringify({
      title: entryOrderNo
        ? `订单咨询 · ${entryOrderNo.slice(-6)}`
        : productId
          ? '商品咨询'
          : '新会话',
    }),
  })
  sessions.value = [session, ...sessions.value]
  await select(session.publicId, true)
}
async function select(id: string, useEntryHint = false) {
  clearChatTurn()
  const history = id ? await api<any[]>(`/chat/sessions/${encodeURIComponent(id)}/messages`) : []
  for (const message of history) {
    try {
      const metadata = JSON.parse(message.metadataJson || '{}')
      message.candidates = metadata.productCandidates
      message.candidateQuestion = metadata.productQuestion
      message.comparison = metadata.productComparison
      message.pendingComparison = metadata.pendingComparison
      message.orderCandidates = metadata.orderCandidates
      message.order = metadata.order
      message.action = metadata.action
      message.ticket = metadata.ticket
    } catch {
      /* Older messages may have no structured business metadata. */
    }
  }
  const selectedOrder =
    useEntryHint && entryOrderNo
      ? await api(`/orders/${encodeURIComponent(entryOrderNo)}`)
      : undefined
  orderContext.value = selectedOrder
  entryPageContext = useEntryHint && productId ? { sessionId: id, productId } : null
  current.value = id
  messages.value = history
  comparisonTerms.value = {}
  input.value = ''
  action.value = null
  const savedAction = history.at(-1)?.action
  if (
    savedAction &&
    !recoveryStorageWarning.value &&
    !confirmationUnknown.value &&
    savedAction.actionId !== recoveredCancellation.value?.operation.requestId
  ) {
    try {
      const result = await api<any>(
        `/chat/actions/${encodeURIComponent(savedAction.actionId)}/result`,
      )
      if (result.actionStatus === 'PENDING') action.value = savedAction
    } catch {
      // An old or inaccessible card is never treated as permission to execute.
    }
  }
  await router.replace({
    query: {
      ...route.query,
      sessionId: id || undefined,
      productId: entryPageContext?.productId,
      orderNo: orderContext.value?.orderNo,
    },
  })
  followOutput.value = true
  await nextTick()
  scroll(true)
  composer.value?.focus()
}
async function changeSession(task: () => Promise<void>) {
  if (sessionBusy.value || sending.value) return
  sessionBusy.value = true
  try {
    await task()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话操作失败')
  } finally {
    sessionBusy.value = false
    await nextTick()
    composer.value?.focus()
  }
}
function onEnter(event: KeyboardEvent) {
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (!event.repeat) void send()
}
async function removeSessions(id?: string) {
  const title = id ? '删除此会话' : '清除所有会话'
  try {
    await ElMessageBox.confirm(
      id ? '删除后将无法再打开这段会话。' : '清除后将无法再打开当前账号或访客身份的任何已有会话。',
      title,
      { type: 'warning', confirmButtonText: id ? '删除' : '全部清除', cancelButtonText: '取消' },
    )
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    throw reason
  }
  await api(id ? `/chat/sessions/${encodeURIComponent(id)}` : '/chat/sessions', {
    method: 'DELETE',
  })
  sessions.value = id ? sessions.value.filter((session) => session.publicId !== id) : []
  if (!id || current.value === id) {
    productId = undefined
    entryOrderNo = undefined
    await select(sessions.value[0]?.publicId ?? '')
  }
}
async function send(
  orderSelection?: OrderSelection,
  selectedProductId?: number,
  comparisonSelection?: ComparisonSelection,
) {
  if (!input.value.trim() || sending.value || sessionBusy.value) return
  const text = input.value
  const owner = auth.identity
  sending.value = true
  try {
    if (!current.value) await create()
    if (disposed || owner !== auth.identity) return
    const sessionId = current.value
    input.value = ''
    action.value = null
    followOutput.value = true
    messages.value.push({ role: 'USER', content: text })
    const assistant: any = reactive({
      role: 'ASSISTANT',
      content: '',
      comparison: null,
      candidateQuestion: text,
    })
    messages.value.push(assistant)
    const turn: ChatTurn = {
      sessionId,
      owner,
      assistant,
      request: {
        message: text,
        clientRequestId: crypto.randomUUID(),
        pageContext: orderContext.value
          ? { orderNo: orderContext.value.orderNo }
          : entryPageContext?.sessionId === sessionId
            ? { productId: entryPageContext.productId }
            : null,
        orderSelection,
        selectedProductId,
        comparisonSelection,
      },
    }
    // 只有最新一轮可恢复。旧气泡保留原内容和中断标记。
    retryableTurn.value = turn
    await receiveTurn(turn)
  } catch (error) {
    if (!disposed && owner === auth.identity)
      ElMessage.error(error instanceof Error ? error.message : '发送消息失败')
  } finally {
    sending.value = false
  }
}

async function retryTurn() {
  const turn = retryableTurn.value
  if (!turn?.assistant.canRetry || !isCurrentTurn(turn) || sending.value || sessionBusy.value)
    return
  sending.value = true
  followOutput.value = true
  try {
    await receiveTurn(turn)
  } finally {
    sending.value = false
  }
}

function isCurrentTurn(turn: ChatTurn) {
  return (
    !disposed &&
    retryableTurn.value === turn &&
    turn.sessionId === current.value &&
    turn.owner === auth.identity
  )
}

async function receiveTurn(turn: ChatTurn) {
  const { sessionId, assistant, request } = turn
  const controller = new globalThis.AbortController()
  chatStream = controller
  const next: any = { content: '', comparison: null, candidateQuestion: request.message }
  let done: any
  try {
    await stream(
      `/chat/sessions/${encodeURIComponent(sessionId)}/messages/stream`,
      request,
      (name, data) => {
        if (!isCurrentTurn(turn)) return
        if (name === 'progress') assistant.progress = data.message
        if (name === 'token') {
          next.content += data
          assistant.content = next.content
        }
        if (name === 'meta') {
          next.intent = data.intent
          next.citations = data.citations
          next.degraded = data.degraded
        }
        if (name === 'product_selection_required') next.candidates = data
        if (name === 'product_comparison') next.comparison = data
        if (name === 'comparison_selection_required') next.pendingComparison = data
        if (name === 'order') next.order = data
        if (name === 'order_selection_required') next.orderCandidates = data
        if (name === 'action_required') next.action = data
        if (name === 'ticket') next.ticket = data
        if (name === 'done' && data && typeof data === 'object') done = data
        nextTick(scroll)
      },
      controller.signal,
    )
    if (!isCurrentTurn(turn)) return
    if (!done) {
      assistant.interrupted = '连接已结束，可以重试本轮。'
      assistant.canRetry = true
      return
    }
    // 收到完成事件后再把卡片和会话上下文交给界面。
    Object.assign(assistant, next, {
      publicId: done.messageId,
      interrupted: undefined,
      streamError: undefined,
      canRetry: false,
    })
    const query: LocationQueryRaw = { ...route.query }
    if (next.intent === 'PRODUCT_QUERY' && entryPageContext?.sessionId === sessionId) {
      entryPageContext = null
      query.productId = undefined
    }
    if (next.order || next.orderCandidates) {
      orderContext.value = next.order
      query.orderNo = next.order?.orderNo
    }
    action.value = next.action
    if (
      route.path === '/chat' &&
      route.query.sessionId === sessionId &&
      (query.orderNo !== route.query.orderNo || query.productId !== route.query.productId)
    )
      void router.replace({ query })
    if (next.ticket) ElMessage.success(`工单 ${next.ticket.publicId} 已创建`)
    retryableTurn.value = null
  } catch (error) {
    if (!isCurrentTurn(turn)) return
    const failure = error instanceof StreamRequestError ? error : undefined
    assistant.streamError = failure
    assistant.canRetry = failure?.retryable ?? true
    assistant.interrupted =
      failure?.code === 'REQUEST_IN_PROGRESS'
        ? '服务端仍在处理本轮，请稍后重试。'
        : failure?.message || '网络连接中断，可以重试本轮。'
  } finally {
    if (chatStream === controller) chatStream = undefined
    nextTick(scroll)
  }
}
async function askCandidate(candidate: { id: number }, question: string) {
  if (!question || sending.value || sessionBusy.value) return
  // 同型号的不同容量可能共用一个型号名，用商品 ID 继续回答原问题。
  input.value = question
  await send(undefined, candidate.id)
}
function comparisonTermKey(comparison: PendingComparison, index: number) {
  return `${comparison.comparisonId}:${index}`
}
function confirmedProducts(comparison: PendingComparison) {
  return comparison.targets.flatMap((target) => (target.confirmed ? [target.confirmed] : []))
}
async function supplementComparison(
  comparison: PendingComparison,
  targetIndex: number,
  candidate?: ComparisonProduct,
) {
  if (sending.value || sessionBusy.value) return
  const key = comparisonTermKey(comparison, targetIndex)
  const term = comparisonTerms.value[key]?.trim()
  if (!candidate && !term) return
  const label = candidate ? `${candidate.name}（${candidate.sku}）` : term
  input.value = `补充第 ${targetIndex + 1} 款商品：${label}`
  await send(undefined, undefined, {
    comparisonId: comparison.comparisonId,
    targetIndex,
    productId: candidate?.id,
    term: candidate ? undefined : term,
  })
}
async function compareConfirmed(comparison: PendingComparison) {
  if (sending.value || sessionBusy.value) return
  const count = confirmedProducts(comparison).length
  input.value = `先比较已确认的 ${count} 款商品`
  // 传比较编号表达用户的选择。实际纳入表格的商品由后端读取本次比较的快照确定。
  await send(undefined, undefined, {
    comparisonId: comparison.comparisonId,
    compareConfirmed: true,
  })
}
async function chooseOrder(queryId: string, orderNo: string) {
  input.value = `选择订单 ${orderNo}`
  await send({ queryId, orderNo })
}
async function confirm(decision: string) {
  if (!action.value || sending.value || confirmationUnknown.value) return
  const pending = action.value
  const sourceSession = current.value
  const owner = currentCancellationOwner()
  const marker: PendingCancellation | undefined =
    pending.actionType === 'CANCEL_ORDER' && owner
      ? {
          version: 1,
          owner,
          actionId: pending.actionId,
          orderNo: pending.orderNo,
          sessionId: sourceSession,
        }
      : undefined
  if (pending.actionType === 'CANCEL_ORDER') {
    recoveryStorageWarning.value =
      marker && savePendingCancellation(marker)
        ? ''
        : '无法保存待核验标识。本页仍可核验，但刷新或离开后可能无法自动恢复；请保留操作标识并查看订单。'
  }
  sending.value = true
  followOutput.value = true
  const assistant: any = { role: 'ASSISTANT', content: '' }
  messages.value.push(assistant)
  let completed = false
  try {
    await stream(`/chat/actions/${pending.actionId}/confirm`, { decision }, (name, data) => {
      if (name === 'token') assistant.content += data
      if (name === 'ticket') ElMessage.success(`工单 ${data.publicId} 已创建`)
      if (name === 'done') completed = true
      nextTick(scroll)
    })
    if (!completed) throw new Error('确认响应未完整接收')
    if (currentCancellationOwner() !== owner) return
    action.value = null
    if (pending.actionType === 'CANCEL_ORDER') {
      assistant.order = await api(`/orders/${encodeURIComponent(pending.orderNo)}`)
      if (marker && !clearPendingCancellation(marker))
        recoveryStorageWarning.value =
          '操作已完成，但待核验记录清理失败；下次可能再次只读核验，不会重复取消。'
    }
  } catch (error) {
    if (pending.actionType === 'CANCEL_ORDER') {
      if (currentCancellationOwner() !== owner) return
      unverifiedAction.value = marker || {
        actionId: pending.actionId,
        orderNo: pending.orderNo,
        owner,
        sessionId: sourceSession,
      }
      action.value = null
      pendingAssistant = assistant
      pendingSession = sourceSession
      assistant.content = '决定响应不完整，操作结果待核验。'
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
  const pending = unverifiedAction.value
  if (!pending.owner || pending.owner !== currentCancellationOwner()) {
    unverifiedAction.value = null
    confirmationUnknown.value = false
    recoveredCancellation.value = null
    return
  }
  sending.value = true
  try {
    const result = await api<any>(`/chat/actions/${encodeURIComponent(pending.actionId)}/result`)
    if (
      currentCancellationOwner() !== pending.owner ||
      unverifiedAction.value?.actionId !== pending.actionId
    )
      return
    if (result.operation.requestId !== pending.actionId || result.order.orderNo !== pending.orderNo)
      throw new Error('恢复结果与待核验标识不匹配')
    if (result.actionStatus === 'PENDING') throw new Error('尚未查到最终决定')
    recoveredCancellation.value = result
    recoveryStorageWarning.value = clearPendingCancellation(pending)
      ? ''
      : '结果已核验，但会话存储清理失败；刷新后可能再次只读核验，不会重复取消。'
    if (
      pendingAssistant &&
      current.value === pendingSession &&
      messages.value.includes(pendingAssistant)
    ) {
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
async function restoreCancellation() {
  unverifiedAction.value = null
  confirmationUnknown.value = false
  recoveredCancellation.value = null
  action.value = null
  pendingAssistant = undefined
  pendingSession = ''
  const saved = loadPendingCancellation(currentCancellationOwner())
  recoveryStorageWarning.value = saved.warning || ''
  if (saved.pending) {
    unverifiedAction.value = saved.pending
    confirmationUnknown.value = true
    await readCancellationResult()
  }
}
watch(
  () => auth.identity,
  () => {
    clearChatTurn()
    void restoreCancellation()
  },
)
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
onMounted(() =>
  changeSession(async () => {
    await auth.ensureGuest()
    await restoreCancellation()
    await refresh()
    const saved = sessions.value.find((session) => session.publicId === route.query.sessionId)
    if (saved) await select(saved.publicId, !!productId || !!entryOrderNo)
    else if ((productId || entryOrderNo) && !route.query.sessionId) await create()
    else if (sessions.value.length) await select(sessions.value[0].publicId)
    else await select('')
  }),
)
</script>
<template>
  <div class="page chat-layout">
    <aside class="sessions">
      <el-button
        type="primary"
        style="width: 100%; margin-bottom: 12px"
        :disabled="sending || sessionBusy"
        @click="changeSession(create)"
        >新建会话</el-button
      >
      <el-button
        v-if="sessions.length"
        link
        type="danger"
        :disabled="sending || sessionBusy"
        @click="changeSession(() => removeSessions())"
        >清除所有会话</el-button
      >
      <div v-for="s in sessions" :key="s.publicId" class="session-row">
        <button
          type="button"
          class="session"
          :class="{ active: current === s.publicId }"
          :disabled="sending || sessionBusy"
          @click="changeSession(() => select(s.publicId))"
        >
          {{ s.title }}
        </button>
        <el-button
          text
          :icon="Delete"
          :aria-label="`删除会话：${s.title}`"
          title="删除此会话"
          :disabled="sending || sessionBusy"
          @click="changeSession(() => removeSessions(s.publicId))"
        />
      </div>
      <p v-if="!sessions.length && !sessionBusy" class="muted">暂无会话，发送消息即可开始。</p>
    </aside>
    <section class="conversation">
      <el-alert
        v-if="orderContext"
        type="info"
        :closable="false"
        class="order-context"
        :title="`正在咨询订单 ${orderContext.orderNo} · ¥${orderContext.totalAmount}`"
      />
      <el-alert
        v-if="recoveryStorageWarning"
        :title="recoveryStorageWarning"
        type="warning"
        :closable="false"
        data-testid="recovery-storage-warning"
      />
      <div ref="box" class="messages" @scroll="updateScrollIntent">
        <div v-for="(m, index) in messages" :key="m.id ?? index" :class="['message', m.role]">
          <p
            v-if="sending && index === messages.length - 1 && m.progress && !m.content"
            role="status"
          >
            {{ m.progress }}…
          </p>
          <!-- markdown-it has raw HTML disabled, so this only receives escaped/generated markup. -->
          <!-- eslint-disable vue/no-v-html -->
          <div
            v-if="m.role === 'ASSISTANT'"
            class="markdown"
            v-html="renderMarkdown(m.content)"
          ></div>
          <!-- eslint-enable vue/no-v-html -->
          <div v-else>{{ m.content }}</div>
          <div v-if="m.interrupted" class="chat-interruption">
            <p role="status">回复未完整接收。{{ m.interrupted }}</p>
            <el-button
              v-if="retryableTurn?.assistant === m && m.canRetry"
              :disabled="sending || sessionBusy"
              @click="retryTurn"
              >重试本轮</el-button
            >
          </div>
          <router-link v-if="m.ticket?.publicId" :to="`/tickets/${m.ticket.publicId}`">
            查看工单 {{ m.ticket.publicId }}
          </router-link>
          <el-alert
            v-if="m.degraded && !m.pendingComparison"
            title="检索服务已降级，以下内容来自融合检索结果"
            type="warning"
            :closable="false"
          />
          <div v-if="m.candidates?.length">
            <el-button
              v-for="p in m.candidates"
              :key="p.id"
              @click="askCandidate(p, m.candidateQuestion)"
              >{{ p.name }}（{{ p.sku }}）</el-button
            >
          </div>
          <div v-if="m.comparison" class="chat-comparison-table">
            <table class="spec-table">
              <thead>
                <tr>
                  <th scope="col">项目</th>
                  <th v-for="p in m.comparison.products" :key="p.id" scope="col">
                    {{ p.name }}<br /><small>{{ p.sku }}</small>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="f in m.comparison.fields" :key="f">
                  <th scope="row">{{ f }}</th>
                  <td v-for="p in m.comparison.products" :key="p.id">
                    {{ p.specs[f] ?? '暂无数据' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="m.pendingComparison" class="comparison-choices">
            <p v-if="confirmedProducts(m.pendingComparison).length">
              已确认：{{
                confirmedProducts(m.pendingComparison)
                  .map((product) => `${product.name}（${product.sku}）`)
                  .join('、')
              }}
            </p>
            <template
              v-for="(target, targetIndex) in m.pendingComparison.targets"
              :key="targetIndex"
            >
              <div v-if="!target.confirmed" class="comparison-target">
                <strong>待确认 · {{ target.label }}</strong>
                <div v-if="target.candidates.length" class="comparison-options">
                  <el-button
                    v-for="candidate in target.candidates"
                    :key="candidate.id"
                    :disabled="sending || sessionBusy"
                    @click="
                      supplementComparison(m.pendingComparison, Number(targetIndex), candidate)
                    "
                    >{{ candidate.name }}（{{ candidate.sku }}）</el-button
                  >
                </div>
                <form
                  class="comparison-options"
                  @submit.prevent="supplementComparison(m.pendingComparison, Number(targetIndex))"
                >
                  <el-input
                    v-model="
                      comparisonTerms[comparisonTermKey(m.pendingComparison, Number(targetIndex))]
                    "
                    :aria-label="`补充第 ${Number(targetIndex) + 1} 款商品的型号或 SKU`"
                    placeholder="输入型号或 SKU"
                    maxlength="120"
                    :disabled="sending || sessionBusy"
                  />
                  <el-button native-type="submit" :disabled="sending || sessionBusy"
                    >补充</el-button
                  >
                </form>
              </div>
            </template>
            <el-button
              v-if="confirmedProducts(m.pendingComparison).length >= 2 && !m.comparison"
              :disabled="sending || sessionBusy"
              @click="compareConfirmed(m.pendingComparison)"
              >{{
                confirmedProducts(m.pendingComparison).length === m.pendingComparison.targets.length
                  ? '重试比较'
                  : `先比较已确认的 ${confirmedProducts(m.pendingComparison).length} 款`
              }}</el-button
            >
          </div>
          <el-card v-if="m.order" shadow="never"
            ><strong>订单 {{ m.order.orderNo }}</strong>
            <p>状态：{{ m.order.status }} · 金额：¥{{ m.order.totalAmount }}</p>
            <RefundReview v-if="m.order.refundReview" :review="m.order.refundReview" />
            <ReturnProgress v-if="m.order.returnRequest" :request="m.order.returnRequest" />
            <router-link :to="{ path: '/orders', query: { orderNo: m.order.orderNo } }"
              >查看订单与退款进度</router-link
            ></el-card
          >
          <div v-if="m.orderCandidates" class="order-candidates">
            <p v-if="m.orderCandidates.hasMore">
              这里只列出最近的候选订单，请补充商品名称或日期缩小范围。
            </p>
            <el-card
              v-for="(candidate, candidateIndex) in m.orderCandidates.items"
              :key="candidate.orderNo"
              shadow="never"
              style="margin-top: 8px"
            >
              <strong>第 {{ Number(candidateIndex) + 1 }} 笔 · {{ candidate.orderNo }}</strong>
              <p>{{ candidate.items.map((item: any) => item.productName).join('、') }}</p>
              <p>
                ¥{{ candidate.totalAmount }} · {{ candidate.status }} ·
                {{
                  new Date(candidate.createdAt).toLocaleDateString('zh-CN', {
                    timeZone: 'Asia/Shanghai',
                  })
                }}
              </p>
              <el-button
                :disabled="sending || sessionBusy"
                @click="chooseOrder(m.orderCandidates.queryId, candidate.orderNo)"
                >选择这笔订单</el-button
              >
            </el-card>
          </div>
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
            查询不到不代表未执行。可继续咨询或查看订单，不会自动重发取消。
            <span v-if="!recoveryStorageWarning"
              >同一标签刷新或返回本页时会只读核验；退出或切换账号会清除本页记录。</span
            >
            <span v-else>当前存储异常，不能保证刷新后自动恢复，请保存操作标识。</span>
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
              >放弃本次操作</el-button
            >
          </div>
        </div>
      </div>
      <div class="composer">
        <el-input
          ref="composer"
          v-model="input"
          :disabled="sessionBusy"
          type="textarea"
          :rows="2"
          placeholder="咨询商品、订单、物流、退款或售后政策"
          @keydown.enter="onEnter"
        /><el-button type="primary" :loading="sending" :disabled="sessionBusy" @click="send()"
          >发送</el-button
        >
      </div>
    </section>
  </div>
</template>
