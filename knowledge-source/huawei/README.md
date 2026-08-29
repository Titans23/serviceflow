# 华为真实数据来源清单

本目录保存 ServiceFlow 首批真实商品的可导入知识文档。结构化商品事实由 Flyway `V2__replace_demo_catalog_with_huawei_pura80.sql` 写入 MySQL；这里的文档只承载使用说明、适用条件、安全提示和保修政策，不能覆盖 MySQL 中的价格、型号、规格或销售状态。

核验日期：2026-08-29。

| 数据 | 官方来源 |
| --- | --- |
| HUAWEI Pura 80 规格 | https://consumer.huawei.com/cn/phones/pura80/specs/ |
| HUAWEI Pura 80 产品页/起售价 | https://consumer.huawei.com/cn/phones/pura80/ |
| HUAWEI Pura 80 Pro 规格 | https://consumer.huawei.com/cn/phones/pura80-pro/specs/ |
| HUAWEI Pura 80 Pro 产品页/起售价 | https://consumer.huawei.com/cn/phones/pura80-pro/ |
| HUAWEI Pura 80 Ultra 规格 | https://consumer.huawei.com/cn/phones/pura80-ultra/specs/ |
| 华为中国大陆手机保修政策 | https://consumer.huawei.com/cn/support/warranty-policy/smartphone/ |

价格是官网“起售价”的核验快照，不是实时商城报价。实际销售前应由企业 PIM/ERP 定时同步价格和状态，并记录数据版本。
