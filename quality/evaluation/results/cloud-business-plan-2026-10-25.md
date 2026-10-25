# 真实模型多轮取消→退款：固定验收范围

用户授权使用项目 `.env` 的实际模型继续验证业务链。本批次固定[6个开发场景](../datasets/cloud-business-recovery-v1.json)、14个新聊天回合、单并发；重复clientRequestId、确认、回调和恢复GET另行计数。共用既有27次provider尝试硬上限，失败扣次、禁自动重试/后台入库/重启补额度，不自动扩大样本。

云模型负责意图分类，订单号、客户、会话、待确认动作和金额由现有后端代码绑定及核验。本批次不把这些参数写成模型生成工具参数的能力。

| 场景 | 回合 | 核验 |
| --- | --- | --- |
| happy-replay | 4 | 查询→指代取消→退款处理中→沙箱成功后查询；重复聊天不再调用模型；并发确认/重复回调各仅一次副作用 |
| order-switch | 3 | 查询A→查询B→指代取消B；确认请求夹带A、客户ID和金额不能改写服务端参数 |
| permissions | 3 | 所有者待确认；其他客户自己的会话和游客尝试访问；外人会话、确认和结果查询被拒，零越权副作用 |
| rejected | 1 | 显式拒绝、失效后确认、404核验均不能执行 |
| expired | 1 | 只删除该合成动作键，失效后只读核验与确认不执行 |
| lost-response | 2 | 查询→指代取消；真实上游提交后丢弃整个HTTP响应，丢动作键，重复GET恢复仍仅1个取消/99元退款 |

调用前从Compose内存展开结果核对运行容器与 `.env` 的模型/凭据一致，仅保存非秘密模型名。启动检查要求全新未使用预算、Cloud模式、独立数据库、退款沙箱开启、后台消费者关闭及零重启。工具参数逐项与JWT身份、会话及订单比对；JWT和原始响应不进入公共产物。

脚本逐请求保存本地原始尝试、错误类型、耗时和请求计数；每个场景完成立即落盘。安全不变量失败或基础设施异常时停止剩余场景，未尝试不算通过；token与费用未知保留null。业务故障注入不重启server，防止内存预算重置。已有Demo服务重启验收另行保留。

```powershell
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
# 停Demo，保留卷；启动前清除当前Shell中OPENAI_*覆盖，确保只取项目.env配置。
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml stop
& $docker compose --env-file .env -p serviceflow-cloud-eval -f deploy/evaluation-cloud-compose.yml -f deploy/evaluation-provider-budget.yml -f deploy/evaluation-cloud-refund.yml up -d --no-build --wait --wait-timeout 300
python -u -B scripts/evaluation/cloud_business_recovery.py --docker $docker --allow-cloud --allow-isolated-faults
```

首次使用新后端代码时须先构建server镜像。脚本自行追加全新合成客户/订单；不会上传资料、清库或清空Redis。运行后停止Cloud并按运行手册恢复Demo。一次失败不会自动触发第二个付费批次；需要先保存结果、定位并做必要离线回归。

准备检查点：38项Python工具测试通过，CLI和Compose合并配置校验通过，未进行云调用。Demo真实HTTP预检发现不存在动作返回SSE `INTERNAL_ERROR`、`retryable=true`；这不是有效的失效/权限拒绝，验收脚本明确不能将其算通过。先修正该错误映射并验证，再开始固定云批次。
