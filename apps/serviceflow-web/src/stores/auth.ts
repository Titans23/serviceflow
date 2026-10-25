import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type Role } from '../api/client'
import { cancellationOwner, clearPendingCancellation } from './pendingCancellation'

export const useAuthStore = defineStore('auth', () => {
  const role = ref<Role | null>(sessionStorage.getItem('serviceflow_role') as Role | null)
  const identity = ref(cancellationOwner(sessionStorage.getItem('serviceflow_token')))
  async function ensureGuest() {
    if (sessionStorage.getItem('serviceflow_token')) return
    const value = await api<{ accessToken: string; role: Role }>('/auth/guest', { method: 'POST' })
    save(value.accessToken, value.role)
  }
  async function login(username: string, password: string) {
    const value = await api<{ accessToken: string; role: Role }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    save(value.accessToken, value.role)
  }
  function save(value: string, newRole: Role) {
    if (
      cancellationOwner(sessionStorage.getItem('serviceflow_token')) !== cancellationOwner(value)
    ) {
      clearPendingCancellation()
    }
    sessionStorage.setItem('serviceflow_token', value)
    sessionStorage.setItem('serviceflow_role', newRole)
    role.value = newRole
    identity.value = cancellationOwner(value)
  }
  function logout() {
    sessionStorage.clear()
    role.value = null
    identity.value = null
    location.href = '/products'
  }
  return { role, identity, ensureGuest, login, logout }
})
