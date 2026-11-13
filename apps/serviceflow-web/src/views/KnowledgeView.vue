<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
const rows = ref<any[]>([]),
  file = ref<File>(),
  form = reactive({ title: '', documentType: 'POLICY', productId: '' })
const editing = ref<string | null>(null)
const busy = ref(false)
const uploadControl = ref<any>()
function edit(row?: any) {
  editing.value = row?.publicId || null
  form.title = row?.title || ''
  form.documentType = row?.documentType || 'POLICY'
  form.productId = row?.productId ? String(row.productId) : ''
  file.value = undefined
  uploadControl.value?.clearFiles()
}
async function load() {
  rows.value = await api('/admin/knowledge-documents')
}
function choose(value: any) {
  file.value = value.raw
}
async function upload() {
  if (!file.value || busy.value) return
  busy.value = true
  const data = new FormData()
  data.append('title', form.title)
  data.append('documentType', form.documentType)
  if (form.productId) data.append('productId', form.productId)
  data.append('file', file.value)
  try {
    await api(
      editing.value
        ? `/admin/knowledge-documents/${editing.value}/versions`
        : '/admin/knowledge-documents',
      { method: 'POST', body: data },
    )
    ElMessage.success('已提交入库')
    edit()
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}
async function retry(id: number) {
  if (busy.value) return
  busy.value = true
  try {
    await api(`/admin/knowledge-document-versions/${id}/retry`, { method: 'POST' })
    ElMessage.success('已创建新的索引版本，原生效资料继续服务')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败')
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <h1>知识库</h1>
    <p>
      新资料完整入库后生效。处理中断会自动恢复，连续超时最多尝试三次；失败后可重建索引，保留原失败版本。
    </p>
    <el-button :disabled="busy" @click="load">刷新状态</el-button>
    <el-button v-if="editing" :disabled="busy" @click="edit()">返回新建文档</el-button>
    <p v-if="editing">正在更新 {{ form.title }} 的资料</p>
    <el-card
      ><el-form inline
        ><el-form-item label="标题"
          ><el-input v-model="form.title" :disabled="Boolean(editing) || busy" /></el-form-item
        ><el-form-item label="类型"
          ><el-select
            v-model="form.documentType"
            :disabled="Boolean(editing) || busy"
            style="width: 190px"
            ><el-option label="售后政策" value="POLICY" /><el-option
              label="商品说明书"
              value="PRODUCT_MANUAL" /><el-option
              label="客服 SOP"
              value="CUSTOMER_SERVICE_SOP" /></el-select></el-form-item
        ><el-form-item v-if="form.documentType === 'PRODUCT_MANUAL'" label="商品 ID"
          ><el-input v-model="form.productId" :disabled="Boolean(editing) || busy" /></el-form-item
        ><el-form-item
          ><el-upload
            ref="uploadControl"
            :disabled="busy"
            :auto-upload="false"
            :limit="1"
            :on-change="choose"
            :on-remove="
              () => {
                file = undefined
              }
            "
            ><el-button>选择文件</el-button></el-upload
          ></el-form-item
        ><el-button type="primary" :loading="busy" :disabled="!file" @click="upload">{{
          editing ? '上传新版本' : '上传'
        }}</el-button></el-form
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
          ><el-button link :disabled="busy" @click="edit(scope.row)">更新资料</el-button
          ><el-button
            v-if="scope.row.latestStatus === 'FAILED'"
            link
            type="primary"
            :disabled="busy"
            @click="retry(scope.row.latestVersionId)"
            >重建索引</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </div>
</template>
