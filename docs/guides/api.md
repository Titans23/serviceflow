# 接口导航

所有路径包含统一的 `/api` 前缀。权限由 SecurityConfig 和业务归属检查共同约束。

## 身份与商品

```text
POST /api/auth/guest
POST /api/auth/login
GET  /api/me
GET  /api/products
GET  /api/products/{id}
POST /api/products/compare
POST /api/admin/products/import
```

## 聊天与订单

```text
POST /api/chat/sessions
GET  /api/chat/sessions
DELETE /api/chat/sessions                        # 删除本人全部会话
DELETE /api/chat/sessions/{sessionId}
GET  /api/chat/sessions/{sessionId}/messages
POST /api/chat/sessions/{sessionId}/messages/stream
POST /api/chat/actions/{actionId}/confirm         # decision 表达确认或放弃
GET  /api/chat/actions/{actionId}/result           # 本人只读核验已提交取消，不触发执行
GET  /api/orders/{orderNo}
GET  /api/orders                                 # orderNo 精确筛选 / keyword 模糊筛选
POST /api/orders/{orderNo}/return
```

## 退款、退货与工单

```text
GET  /api/admin/refunds
POST /api/admin/refunds/{publicId}/sandbox-events  # 仅显式启用的管理员沙箱
POST /api/admin/refunds/{id}/retry                # 沙箱或自动投递模式启用
POST /api/admin/refunds/{id}/reconcile            # 恢复原编号的后台核验
POST /api/internal/refunds/{id}/events            # 独立渠道凭证，不是顾客接口
GET  /api/admin/returns
POST /api/admin/returns/{id}/review
POST /api/admin/returns/{id}/receive
GET  /api/tickets
GET  /api/tickets/{id}
POST /api/tickets/{id}/messages
POST /api/tickets/{id}/close
GET  /api/admin/tickets
GET  /api/admin/tickets/{id}
POST /api/admin/tickets/{id}/messages
PUT  /api/admin/tickets/{publicId}/status
PUT  /api/admin/tickets/{publicId}/customer-message
GET  /api/admin/ai-audits
```

## 知识库与评测

```text
POST /api/admin/knowledge-documents
POST /api/admin/knowledge-documents/{documentId}/versions
GET  /api/admin/knowledge-documents
POST /api/admin/knowledge-document-versions/{versionId}/retry
POST /api/admin/evaluation/rag                    # 仅显式开启的管理员评测入口
```

SSE 事件包含 `progress`、`meta`、`token`、`rag_evidence`、`product_selection_required`、`product_comparison`、`order_selection_required`、`order`、`action_required`、`ticket`、`done` 和 `error`。详细行为见 [聊天章节](../wiki/chat.md)。

完整请求字段以运行中的 Swagger 和 Controller 为准。新增接口请同步本索引。
