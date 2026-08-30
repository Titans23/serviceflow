import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080/api'
const profile = __ENV.PROFILE || 'smoke'
const productLatency = new Trend('serviceflow_product_latency', true)
const orderLatency = new Trend('serviceflow_order_latency', true)
const sseLatency = new Trend('serviceflow_sse_latency', true)
const businessErrors = new Counter('serviceflow_business_errors')

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios:
    profile === 'products'
      ? { products: { executor: 'constant-vus', vus: 100, duration: '5m' } }
      : profile === 'orders'
        ? { orders: { executor: 'constant-vus', vus: 50, duration: '3m' } }
        : profile === 'chat'
          ? { chat: { executor: 'constant-vus', vus: 30, duration: '2m' } }
          : { smoke: { executor: 'constant-vus', vus: 5, duration: '30s' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    serviceflow_product_latency: ['p(95)<300'],
    serviceflow_order_latency: ['p(95)<500'],
    serviceflow_sse_latency: ['p(95)<3000'],
  },
}

function guestToken() {
  const response = http.post(`${baseUrl}/auth/guest`, '{}', { headers: { 'Content-Type': 'application/json' } })
  check(response, { 'guest token issued': (r) => r.status === 200 && Boolean(r.json('accessToken')) }) || businessErrors.add(1)
  return response.json('accessToken')
}

function productQuery(token) {
  const started = Date.now()
  const response = http.get(`${baseUrl}/products?page=0&size=20`, { headers: { Authorization: `Bearer ${token}` } })
  productLatency.add(Date.now() - started)
  check(response, { 'product query succeeds': (r) => r.status === 200 && Array.isArray(r.json('items')) }) || businessErrors.add(1)
}

function orderQuery(token) {
  const orderNo = __ENV.ORDER_NO
  if (!orderNo) return
  const started = Date.now()
  const response = http.get(`${baseUrl}/orders/${orderNo}`, { headers: { Authorization: `Bearer ${token}` } })
  orderLatency.add(Date.now() - started)
  check(response, { 'order query returns a valid response': (r) => r.status === 200 || r.status === 404 }) || businessErrors.add(1)
}

function chatQuery(token) {
  const session = http.post(`${baseUrl}/chat/sessions`, '{}', { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } })
  if (!check(session, { 'chat session created': (r) => r.status === 200 })) {
    businessErrors.add(1)
    return
  }
  const started = Date.now()
  const response = http.post(
    `${baseUrl}/chat/sessions/${session.json('publicId')}/messages/stream`,
    JSON.stringify({ message: '请介绍华为 Pura 80 的保修政策', clientRequestId: `${__VU}-${__ITER}-${Date.now()}` }),
    { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, timeout: '95s' },
  )
  sseLatency.add(Date.now() - started)
  check(response, { 'SSE chat completes': (r) => r.status === 200 && r.body.includes('event: done') }) || businessErrors.add(1)
}

export default function () {
  const token = guestToken()
  if (!token) return
  if (profile === 'orders') orderQuery(token)
  else if (profile === 'chat') chatQuery(token)
  else productQuery(token)
  sleep(1)
}
