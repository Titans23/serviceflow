import { createRouter, createWebHistory } from 'vue-router'
import ProductsView from './views/ProductsView.vue'
import ProductDetailView from './views/ProductDetailView.vue'
import CompareView from './views/CompareView.vue'
import ChatView from './views/ChatView.vue'
import LoginView from './views/LoginView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import ProductAdminView from './views/ProductAdminView.vue'
import OperationsView from './views/OperationsView.vue'
import OrdersView from './views/OrdersView.vue'
import RefundsView from './views/RefundsView.vue'
import ReturnsView from './views/ReturnsView.vue'
import TicketsView from './views/TicketsView.vue'
import TicketDetailView from './views/TicketDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/products', component: ProductsView },
    { path: '/products/:id', component: ProductDetailView },
    { path: '/compare', component: CompareView },
    { path: '/chat', component: ChatView },
    { path: '/login', component: LoginView },
    { path: '/orders', component: OrdersView, meta: { requiresCustomer: true } },
    { path: '/tickets', component: TicketsView, meta: { requiresCustomer: true } },
    { path: '/tickets/:id', component: TicketDetailView, meta: { requiresCustomer: true } },
    { path: '/admin/tickets/:id', component: TicketDetailView, meta: { requiresAdmin: true } },
    { path: '/admin/refunds', component: RefundsView, meta: { requiresAdmin: true } },
    { path: '/admin/returns', component: ReturnsView, meta: { requiresAdmin: true } },
    { path: '/admin/knowledge', component: KnowledgeView, meta: { requiresAdmin: true } },
    { path: '/admin/products', component: ProductAdminView, meta: { requiresAdmin: true } },
    { path: '/admin/operations', component: OperationsView, meta: { requiresAdmin: true } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresCustomer && sessionStorage.getItem('serviceflow_role') !== 'CUSTOMER') {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && sessionStorage.getItem('serviceflow_role') !== 'ADMIN') {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
