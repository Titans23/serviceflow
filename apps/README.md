# 应用模块

- `serviceflow-server/`：Java 21 + Spring Boot 后端。遵循 Maven 标准目录，采用 `controller / service / service.impl / mapper / model` 分层，Agent、RAG 与基础设施组件独立分包；测试镜像生产代码位置。
- `serviceflow-web/`：Vue 3 + TypeScript 前端。页面、状态、路由和 API 客户端位于 `src`，浏览器端到端测试位于 `e2e`。

应用模块只保存可构建源码及其模块级配置；部署配置、质量报告和业务数据分别位于仓库根目录的 `deploy/`、`quality/` 和 `data/`。
