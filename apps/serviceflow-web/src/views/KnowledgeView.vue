<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
const rows = ref<any[]>([]),
  file = ref<File>(),
  form = reactive({ title: '', documentType: 'POLICY', productId: '' })
async function load() {
  rows.value = await api('/admin/knowledge-documents')
}
function choose(value: any) {
  file.value = value.raw
}
async function upload() {
  if (!file.value) return
  const data = new FormData()
  data.append('title', form.title)
  data.append('documentType', form.documentType)
  if (form.productId) data.append('productId', form.productId)
  data.append('file', file.value)
  try {
    await api('/admin/knowledge-documents', { method: 'POST', body: data })
    ElMessage.success('已提交入库')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}
async function retry(id: number) {
  await api(`/admin/knowledge-document-versions/${id}/retry`, { method: 'POST' })
  await load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <h1>知识库</h1>
    <el-card
      ><el-form inline
        ><el-form-item label="标题"><el-input v-model="form.title" /></el-form-item
        ><el-form-item label="类型"
          ><el-select v-model="form.documentType" style="width: 190px"
            ><el-option label="售后政策" value="POLICY" /><el-option
              label="商品说明书"
              value="PRODUCT_MANUAL" /><el-option
              label="客服 SOP"
              value="CUSTOMER_SERVICE_SOP" /></el-select></el-form-item
        ><el-form-item v-if="form.documentType === 'PRODUCT_MANUAL'" label="商品 ID"
          ><el-input v-model="form.productId" /></el-form-item
        ><el-form-item
          ><el-upload :auto-upload="false" :limit="1" :on-change="choose"
            ><el-button>选择文件</el-button></el-upload
          ></el-form-item
        ><el-button type="primary" @click="upload">上传</el-button></el-form
      ></el-card
    ><el-table :data="rows" style="margin-top: 20px"
      ><el-table-column prop="title" label="标题" /><el-table-column
        prop="documentType"
        label="类型"
      /><el-table-column prop="latestVersion" label="版本" /><el-table-column
        prop="latestStatus"
        label="状态"
      /><el-table-column prop="errorMessage" label="错误" /><el-table-column label="操作"
        ><template #default="scope"
          ><el-button
            v-if="scope.row.latestStatus === 'FAILED'"
            link
            type="primary"
            @click="retry(scope.row.latestVersionId)"
            >重试</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </div>
</template>
