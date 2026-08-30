# ServiceFlow 200 条评测审计报告

> 本报告复用唯一一次付费在线运行结果，仅修正与业务规则冲突的标签，未重复调用云模型。

- 通过率：98.5%（197/200）
- Recall@5 / MRR / nDCG@5：97.69% / 0.9769 / 0.9769
- 意图 / 商品识别 / 事件准确率：100% / 100% / 100%
- 转人工与投诉事件准确率：100%
- 事实幻觉代理指标：0%
- P50 / P95：2723 / 17689 ms

## 未通过用例

- policy-028：citation, requiredFact
- policy-033：citation, requiredFact
- policy-038：citation, requiredFact
