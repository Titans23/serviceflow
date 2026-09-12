<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

const jsonText = ref('[]')
const importing = ref(false)
const imported = ref<any[]>([])

async function importProducts() {
  let products: unknown
  try {
    products = JSON.parse(jsonText.value)
  } catch {
    ElMessage.error('商品 JSON 格式无效')
    return
  }
  if (!Array.isArray(products) || products.length === 0) {
    ElMessage.error('请输入至少一个商品对象')
    return
  }
  importing.value = true
  try {
    const result = await api<{ imported: number; products: any[] }>('/admin/products/import', {
      method: 'POST',
      body: JSON.stringify({ products }),
    })
    imported.value = result.products
    ElMessage.success(`已导入 ${result.imported} 个商品`)
  } catch (error: any) {
    ElMessage.error(error.message || '商品导入失败')
  } finally {
    importing.value = false
  }
}

async function loadFile(event: any) {
  const file = event?.raw as File | undefined
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.json')) {
    ElMessage.error('仅支持 .json 商品目录文件')
    return
  }
  jsonText.value = await file.text()
}
</script>

<template>
  <div class="page">
    <h1>商品目录</h1>
    <el-alert
      title="请只导入已核验的商品主数据。价格、型号、规格和销售状态来自本目录，是客服回答的权威事实源。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-card>
      <template #header>
        <div class="card-header">
          <span>批量导入 JSON</span
          ><el-upload :auto-upload="false" :show-file-list="false" :on-change="loadFile"
            ><el-button>读取 JSON 文件</el-button></el-upload
          >
        </div>
      </template>
      <el-input v-model="jsonText" type="textarea" :rows="18" spellcheck="false" />
      <div class="muted import-hint">
        字段要求：sku、name、brand、model、category、specs、listPrice、saleStatus（ON_SALE /
        OFF_SALE / DISCONTINUED）。同 SKU 会更新已有商品。请从 ERP
        或商品主数据导出真实内容后再导入。
      </div>
      <el-button
        type="primary"
        :loading="importing"
        style="margin-top: 14px"
        @click="importProducts"
        >校验并导入</el-button
      >
    </el-card>
    <el-card v-if="imported.length" style="margin-top: 20px">
      <template #header>本次导入结果</template>
      <el-table :data="imported" stripe>
        <el-table-column prop="sku" label="SKU" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="model" label="型号" />
        <el-table-column prop="category" label="类别" />
        <el-table-column prop="listPrice" label="标价" />
        <el-table-column prop="saleStatus" label="销售状态" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.import-hint {
  margin-top: 10px;
}
</style>
