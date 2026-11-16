# 知识上传与版本切换

上传、异步解析和活动版本切换各有明确的持久状态，失败版本不覆盖正在服务的知识。

## 知识入库时序

```text
上传 → MySQL document/version=PENDING + Outbox=PENDING（同一事务）
     → Publisher Confirm → RabbitMQ
     → Consumer 幂等解析/Tika/Chunk/Embedding/Milvus
     → READY → 原子切换 active_version_id
```

## 领取、续租与重建

上传先锁文档行再分配版本号，每个源文件使用独立路径，事务回滚清理本次新文件。消费者通过 PENDING→PROCESSING 领取任务，使用三分钟租约并在分块前续期。30 秒一次的恢复扫描处理超时任务，最多自动尝试三次。重试复制源文件引用，创建新版本编号和 Outbox 记录，旧任务的向量写入因此与新尝试隔离。已发布但一直未领取的 PENDING 通知会重新投递；连续五次发布失败则标记 FAILED，支持人工重建。

## 激活条件与跨存储边界

只有持有有效租约的 PROCESSING 版本能变为 READY。激活时按版本号单调推进，旧版本晚完成不会覆盖已生效的新版本；旧内容已经有更新上传时，也不会自动重建旧内容。新版本完整入库前，原活动版本继续服务。MySQL、文件和 Milvus 仍是独立存储，历史失败版本及其向量保留用于追溯，不宣称跨存储 exactly-once。

## 源码阅读入口

- [KnowledgeServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/KnowledgeServiceImpl.java)
- [KnowledgeOutboxPublisher](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/KnowledgeOutboxPublisher.java)
- [DocumentConsumer](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/DocumentConsumer.java)
- [KnowledgeRecovery](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/KnowledgeRecovery.java)
- [KnowledgeChunker](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/KnowledgeChunker.java)
