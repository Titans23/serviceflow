# 自动化脚本

从仓库根目录执行。业务故障与云评测先阅读各自协议，不直接运行整个目录。

| 目录 | 用途 | 入口 |
| --- | --- | --- |
| docs/ | 从 Markdown 生成离线 Wiki，并验证源码链接和产物是否最新 | `node scripts/docs/build-wiki.mjs` / `--check` |
| evaluation/ | 数据校验、RAG 对照、多轮业务、退款故障与离线判分 | [评测文档](../quality/evaluation/README.md) |
| verification/ | Milvus、运行环境等专项验收 | [故障演练](../docs/runbooks/fault-drills.md) |
| performance/ | 数据准备与订单查询性能对照 | [性能目录](../quality/performance/) |

旧 `run-evaluation.ps1`、`audit-evaluation-report.ps1`、`normalize-evaluation.mjs` 用于历史单轮评测与标签审计，不能替代当前 RAG 分块评测。保留它们是为了复现历史结果，不把旧引用文件命中率当作检索 Recall。

本地原始响应、诊断和临时报告不提交；公开证据统一放到 quality 对应的 results/reports 目录，并按该目录约定脱敏。
