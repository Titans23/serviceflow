<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore(),
  products = ref<any[]>([]),
  selected = ref<number[]>([]),
  result = ref<any>()
onMounted(async () => {
  await auth.ensureGuest()
  products.value = (await api<any>('/products?size=50')).items
})
const selectedProducts = computed(() => products.value.filter((p) => selected.value.includes(p.id)))
async function compare() {
  try {
    result.value = await api('/products/compare', {
      method: 'POST',
      body: JSON.stringify({ productIds: selected.value }),
    })
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}
</script>
<template>
  <div class="page">
    <h1 class="page-title">商品对比</h1>
    <el-card
      ><el-checkbox-group v-model="selected" :max="3"
        ><el-checkbox
          v-for="p in products"
          :key="p.id"
          :value="p.id"
          :disabled="selectedProducts.length > 0 && selectedProducts[0].category !== p.category"
          >{{ p.name }}</el-checkbox
        ></el-checkbox-group
      >
      <div style="margin-top: 18px">
        <el-button type="primary" :disabled="selected.length < 2" @click="compare"
          >比较所选商品</el-button
        >
      </div></el-card
    >
    <div v-if="result" class="comparison" style="margin-top: 20px">
      <table class="spec-table">
        <thead>
          <tr>
            <th>项目</th>
            <th v-for="p in result.products" :key="p.id">{{ p.name }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th>标价</th>
            <td v-for="p in result.products" :key="p.id">¥{{ p.listPrice }}</td>
          </tr>
          <tr>
            <th>可售状态</th>
            <td v-for="p in result.products" :key="p.id">{{ p.saleStatus }}</td>
          </tr>
          <tr v-for="field in result.fields" :key="field">
            <th>{{ field }}</th>
            <td v-for="p in result.products" :key="p.id">{{ p.specs[field] ?? '暂无数据' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
