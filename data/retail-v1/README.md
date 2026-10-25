# retail-v1：公开商品与知识资料

本包包含 10 款真实型号的 23 个 SKU、11 份来源资料、88 个独立知识章节。覆盖 Pura 80 / Pro、Mate 70 / Pro+、nova 14 / Pro / Ultra、Mate X6、FreeBuds 6、FreeClip，以及华为中国手机保修政策。官网核验日期为 2026-10-23，来源 URL、适用型号、章节对应关系登记在 [manifest.json](manifest.json)。

## 数据边界

- 商品参数依据厂家公开资料整理；知识文件是限定范围的摘要，不是官网全文镜像。文档保留型号、测量条件、脚注含义和来源章节。
- 所有新 SKU 的 `listPrice` 为 `null`，界面显示“暂无报价”；`OFF_SALE` 是本演示商家的停售配置，不代表厂家停产或真实库存。
- 未提供真实客户、交易、评价或流量数据。评测订单另行合成，固定 99 元仅用于事务测试。
- 资料经过 AI 辅助交叉检查，尚无独立人工复核。商品参数与政策可能变更；应用或再发布前需重新核验来源和使用条款。品牌和原始资料权利归各权利人，不因项目许可证而转移。
- 同型号多个容量 SKU 共享一份知识原文。当前入库 API 按 SKU 绑定文档，因此导入后会产生 24 个文档对象（23 个商品说明 + 1 个政策）；不能把共享副本计算为新增独立知识。

## 导入与更新

只需 Python 3.10+ 标准库。先启动[独立评测环境](../../docs/guides/evaluation-and-refund.md)，再运行：

```powershell
python -B scripts/evaluation/eval_v2.py validate
python -B scripts/evaluation/import_retail.py --base-url http://127.0.0.1:18080/api --wait-seconds 180
```

导入器使用管理员 API 更新同 SKU 商品，保留数据库中其他 SKU；原有 Flyway 演示数据不会被清空。知识文档使用内容 SHA-256 和本地回执判断是否更新版本。回执默认在被 Git 忽略的 `quality/evaluation/reports/retail-import.json`，绑定目标实例地址；发现回执不匹配或文档被外部修改时停止，需检查版本后处理。请为不同数据库实例保留独立回执，不要盲目覆盖。

脚本会等待异步入库完成并检查活动版本。Demo 模式的 READY 只说明解析/事件流程完成，不代表真实向量检索已验证；Cloud 模式导入需要明确允许 Embedding 调用与费用，并传 `--allow-cloud-ingestion`。

新增资料应同步更新 catalog、manifest、知识文件和来源对应的评测参考答案。保留来源族的开发/留出分组，不能为提高成绩把失败样本移出留出集。
