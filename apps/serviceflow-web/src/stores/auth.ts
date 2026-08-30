import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type Role } from '../api/client'

export const useAuthStore=defineStore('auth',()=>{
  const role = ref<Role | null>(sessionStorage.getItem('serviceflow_role') as Role | null)
  async function ensureGuest(){ if(sessionStorage.getItem('serviceflow_token')) return; const value=await api<{accessToken:string,role:Role}>('/auth/guest',{method:'POST'}); save(value.accessToken,value.role) }
  async function login(username:string,password:string){ const value=await api<{accessToken:string,role:Role}>('/auth/login',{method:'POST',body:JSON.stringify({username,password})}); save(value.accessToken,value.role) }
  function save(value:string,newRole:Role){ sessionStorage.setItem('serviceflow_token',value); sessionStorage.setItem('serviceflow_role',newRole); role.value=newRole }
  function logout(){ sessionStorage.clear(); role.value=null; location.href='/products' }
  return {role,ensureGuest,login,logout}
})
