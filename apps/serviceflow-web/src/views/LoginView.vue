<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore(),
  router = useRouter(),
  route = useRoute(),
  loading = ref(false)
const form = reactive({ username: 'customer', password: 'Customer123!' })
async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : null
    const destination = requested ?? (auth.role === 'ADMIN' ? '/admin/operations' : '/chat')
    await router.push(destination)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="page" style="max-width: 420px">
    <el-card
      ><h2>登录 ServiceFlow</h2>
      <el-form label-position="top"
        ><el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item
        ><el-form-item label="密码"
          ><el-input v-model="form.password" type="password" show-password /></el-form-item
        ><el-button type="primary" :loading="loading" style="width: 100%" @click="submit"
          >登录</el-button
        ></el-form
      ></el-card
    >
  </div>
</template>
