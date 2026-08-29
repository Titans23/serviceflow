# 200 条评测集

`serviceflow-eval-200.json` 是脱敏、人工可读的结构化评测集，共 200 条：

- 50 条商品说明检索；
- 50 条售后政策检索；
- 20 条商品详情 Agent；
- 20 条商品比较；
- 20 条订单与取消；
- 20 条投诉、上下文、权限和异常输入。

每条用例包含主体、页面上下文、预期意图、商品、引用、SSE 事件、必答事实和禁止声明。先执行结构校验和 20 条 Smoke，再在本机配置云模型并确认预计调用次数后运行完整评测：

```powershell
pwsh ./scripts/run-evaluation.ps1 -BaseUrl http://localhost:8080/api
```

报告输出到 `evaluation/reports/`；原始结果不得包含 API Key、JWT 或用户隐私。公开仓库只提交脱敏后的 Markdown/JSON 报告。
