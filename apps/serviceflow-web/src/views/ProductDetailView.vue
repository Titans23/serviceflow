<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/client'
import { useAuthStore } from '../stores/auth'
const route = useRoute(),
  auth = useAuthStore(),
  product = ref<any>()
onMounted(async () => {
  await auth.ensureGuest()
  product.value = await api(`/products/${route.params.id}`)
})
</script>
<template>
  <div class="page" v-if="product">
    <el-card
      ><el-tag>{{ product.saleStatus }}</el-tag>
      <h1>{{ product.name }}</h1>
      <p>{{ product.brand }} · {{ product.model }} · {{ product.sku }}</p>
      <p class="price">¥{{ product.listPrice }}</p>
      <el-descriptions title="规格" :column="2" border
        ><el-descriptions-item
          v-for="(value, key) in product.specs"
          :key="key"
          :label="String(key)"
          >{{ value }}</el-descriptions-item
        ></el-descriptions
      >
      <div style="margin-top: 22px">
        <router-link :to="`/chat?productId=${product.id}`"
          ><el-button type="primary">咨询此商品</el-button></router-link
        >
      </div></el-card
    >
  </div>
</template>
