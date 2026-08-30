# 5–8 分钟演示脚本

1. `docker compose up -d`，打开 `http://localhost:5173`，展示华为 Pura 80 列表和商品详情。
2. 访客点击“咨询此商品”，演示结构化事实、引用、SSE 增量 token 和无权限订单提示。
3. 打开比较页选择 Pura 80 系列三款，展示同类别限制、缺失规格“暂无数据”和无推荐排序。
4. 使用演示客户登录，查询订单并确认取消；重复点击确认，展示幂等返回。
5. 输入投诉，展示 `ticket` 事件和客服工单页面。
6. 管理员上传一份 Markdown，展示 PENDING→PROCESSING→READY 和活动版本切换。
7. `docker compose --profile observability up -d`，在 Grafana 查看 API、SSE、RAG 降级和 Outbox 面板，在 Jaeger 查看一次请求的 trace。
8. 运行 `mvn -DskipITs verify`、`npm test` 和 k6 Smoke，展示仓库内报告与 CI 工作流。
