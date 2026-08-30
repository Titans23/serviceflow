# 200 条评测集

`serviceflow-eval-200.json` 是脱敏、人工可读的结构化评测集，共 200 条：

- 50 条商品说明检索；
- 50 条售后政策检索；
- 20 条商品详情 Agent；
- 20 条商品比较；
- 20 条订单与取消；
- 20 条投诉和转人工；
- 20 条上下文指代、权限和异常输入。

每条用例包含主体、页面上下文、预期意图、商品、引用、SSE 事件、必答事实和禁止声明。先执行结构校验和 20 条 Smoke，再在本机配置云模型并确认预计调用次数后运行完整评测：

```powershell
# 完整数据集有 50 条 GUEST 用例；评测环境显式提高阈值，业务默认值仍为 20/10 分钟。
$env:SERVICEFLOW_GUEST_RATE_LIMIT_MAX_REQUESTS = '100'
docker compose up -d --build
pwsh ./scripts/run-evaluation.ps1 -BaseUrl http://localhost:8080/api
```

客户和管理员密码可分别通过 `SERVICEFLOW_EVAL_CUSTOMER_PASSWORD`、`SERVICEFLOW_EVAL_ADMIN_PASSWORD` 覆盖。执行器按每条用例的 `principalType` 获取独立身份，不把 JWT 写入报告；错误类用例以 `error` 作为合法终止事件。

报告输出到 `evaluation/reports/`；原始结果不得包含 API Key、JWT 或用户隐私。公开仓库只提交人工复核、脱敏后的汇总报告。完整云评测会产生模型调用费用，应先跑 20 条 Smoke 并确认调用预算，再执行 200 条全集。
