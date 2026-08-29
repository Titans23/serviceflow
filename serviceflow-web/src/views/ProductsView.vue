<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import { useAuthStore } from '../stores/auth'
type Product={id:number;sku:string;name:string;brand:string;model:string;category:string;listPrice:number;saleStatus:string;specs:Record<string,string>}
const auth=useAuthStore(), keyword=ref(''), category=ref(''), products=ref<Product[]>([]), loading=ref(false)
const categories=computed(()=>[...new Set(products.value.map(p=>p.category))])
async function load(){ loading.value=true; try{ await auth.ensureGuest(); const params=new URLSearchParams();if(keyword.value)params.set('keyword',keyword.value);if(category.value)params.set('category',category.value); const page=await api<{items:Product[]}>(`/products?${params}`); products.value=page.items }finally{loading.value=false} }
onMounted(load)
</script>
<template><div class="page"><div class="page-title"><h1>商品中心</h1><p class="muted">查看结构化规格，或让客服解释功能与使用注意事项。</p><el-space wrap><el-input v-model="keyword" placeholder="搜索 SKU、型号或名称" clearable style="width:360px" @keyup.enter="load"><template #append><el-button @click="load">搜索</el-button></template></el-input><el-select v-model="category" clearable placeholder="全部类别" style="width:160px" @change="load"><el-option v-for="item in categories" :key="item" :label="item" :value="item"/></el-select></el-space></div><div v-loading="loading" class="product-grid"><el-card v-for="p in products" :key="p.id" class="product-card"><el-tag>{{p.category}}</el-tag><h3>{{p.name}}</h3><p class="muted">{{p.brand}} · {{p.model}} · {{p.sku}}</p><p class="price">¥{{p.listPrice}}</p><el-space><router-link :to="`/products/${p.id}`"><el-button type="primary">查看详情</el-button></router-link><router-link :to="`/chat?productId=${p.id}`"><el-button>咨询</el-button></router-link></el-space></el-card></div></div></template>
