<script setup lang="ts">
import { useAuthStore } from './stores/auth'
const auth = useAuthStore()
</script>

<template>
  <el-container class="shell">
    <el-header class="header">
      <router-link class="brand" to="/products">ServiceFlow</router-link>
      <el-menu mode="horizontal" router :ellipsis="false">
        <el-menu-item index="/products">商品</el-menu-item>
        <el-menu-item index="/compare">对比</el-menu-item>
        <el-menu-item index="/chat">智能客服</el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN'" index="/admin/knowledge">知识库</el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN'" index="/admin/products">商品目录</el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN'" index="/admin/operations">运营中心</el-menu-item>
      </el-menu>
      <div class="account"><span>{{ auth.role || '未连接' }}</span><el-button v-if="auth.role === 'GUEST'" type="primary" @click="$router.push('/login')">登录</el-button><el-button v-else @click="auth.logout()">退出</el-button></div>
    </el-header>
    <el-main><router-view /></el-main>
  </el-container>
</template>
