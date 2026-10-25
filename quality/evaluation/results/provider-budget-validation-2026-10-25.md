# 隔离评测 provider 请求上限：离线验证

此检查点没有新付费调用，没有重新入库或修改历史云分数。前一RAG诊断提交 `4413f438` 的 [CI已成功](https://github.com/Titans23/serviceflow/actions/runs/40167308759)。

## 实现边界

显式叠加 `deploy/evaluation-provider-budget.yml` 后，一个隔离Java进程内的Chat、Embedding、Reranker共用27次出站额度。发送前同步准入，失败不退还，超过上限在本机拒绝。只记录累计次数，不记录请求正文、URL、凭据或响应；没有重置API、查询API或持久化预算系统。

普通模式不创建预算对象，仍使用原有HTTP配置及两次重试。预算模式使用项目已有JDK HTTP客户端，固定HTTP/1.1、禁止跟随重定向，并要求Java 21启动参数 `-Djdk.httpclient.redirects.retrylimit=1`。该参数在JDK内部限制重试/重定向交换次数，不能在客户端初始化后再设置。Spring AI与三个Resilience4j实例均设置 `max-attempts=1`。启动时检查这些条件以及Rabbit simple/direct消费者关闭，否则拒绝启动。

共享RestClient同时用于Milvus健康检查；只对实际配置的模型服务origin计数，并要求Milvus独立origin。模型路径、失败和客户端重订阅都不能通过清零绕过额度。WebClient过滤器在订阅时准入；同步RestClient在执行前准入。Reranker保留预算模式的JDK工厂，不再被原来的超时工厂覆盖。没有引入HTTP依赖、代理或后台模型探测。

这是**单进程请求次数上限**，不是金额或token上限。重启会重置内存计数，因此该轮禁止重启、扩容或另起评测；Compose禁用自动重启。执行前后记录同一个容器ID、StartedAt、RestartCount，并保存其预算日志。未来更换JDK主版本或HTTP实现时必须重新验证，不能直接沿用这个证明。

## 本地HTTP证据

`EvaluationProviderBudgetTest`在新的Java进程中设置真实启动参数，加载应用的RestClient配置、Spring AI Chat自动配置和Resilience4j Retry切面；实际调用CloudAiGateway、自有OpenAiCompatibleEmbeddingModel/OpenAiEmbeddingClient及OpenAiRerankerClient。所有服务地址固定为loopback，使用合成输入与假密钥。

| 检查 | 实际结果 |
| --- | --- |
| 容器/数据库/付费模型 | 均不需要；没有外部模型调用 |
| Spring上下文启动后的模型请求 | 0 |
| 已接收的模型请求 | 27，27个唯一标记，每个只出现1次 |
| 请求组成 | Chat 9（6流式、3同步SDK），Embedding 9，Reranker 9 |
| 故障注入 | 9成功、6 HTTP 500、6响应前断连、6 HTTP 307 |
| 重定向目标收到的请求 | 0 |
| 第28次及另两种客户端的后续尝试 | 均在本机拒绝，接收总量仍为27 |
| 重试配置 | 三个真实Retry代理均为1；同步Spring AI 500/断连/307路径均无额外请求 |
| 非法启动配置 | 缺少JVM参数，以及非隔离模式、非正数额度、任一重试值为2、任一后台消费者开启、冲突HTTP定制均拒绝启动 |
| 普通模式 | 无预算对象，实际RetryRegistry仍为2；不修改JVM系统属性 |

自动测试输出只保留 `BUDGET_STUB_PASS received=27 unique=27 blocked=3 redirects=0 startup=0`。测试使用Java参数文件解决Windows类路径长度限制，不修改项目全局测试JVM设置。

完整后端 `mvn -DskipITs verify` 通过：117项中112通过、5个可选本地Grader测试跳过、0失败/错误；格式、打包、覆盖率门禁通过。3例诊断suite离线校验及Compose合并配置静默校验通过。[机器可读摘要](provider-budget-2026-10-25.summary.json)保留本检查点口径。Python采集器未改动，复用上一检查点32/32及逐步trace缺失测试；前端和数据库行为未改动，复用已通过的相关验收。

```powershell
Push-Location apps/serviceflow-server
mvn -Dtest=EvaluationProviderBudgetTest test
mvn -DskipITs verify
Pop-Location
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/datasets/rag-diagnostics-v1.json --package data/retail-holdout-v2
```

## 待授权的三例云诊断命令

[计划](rag-cloud-validation-plan-2026-10-25.json)和[可执行用例](../datasets/rag-diagnostics-v1.json)固定为1个已见屏幕问题、2个新措辞；均属于开发诊断，不能称为独立留出。此处命令仅供批准后执行，本检查点没有执行它们。

```powershell
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
# 先构建代码；停Demo但保留所有卷。不上传资料、不重建索引。
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml build server
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml stop
$cloud = @('--env-file','.env','-p','serviceflow-cloud-eval','-f','deploy/evaluation-cloud-compose.yml','-f','deploy/evaluation-provider-budget.yml')
& $docker compose @cloud config --quiet
& $docker compose @cloud up -d --no-build --wait --wait-timeout 300
$container = & $docker compose @cloud ps -q server
& $docker inspect --format '{{.Id}} {{.State.StartedAt}} {{.RestartCount}}' $container
# 只保存非秘密的模型名与运行标识，不输出完整inspect或展开后的Compose环境。
$metadata = (& $docker inspect $container | ConvertFrom-Json)[0]
$modelNames = @($metadata.Config.Env | Where-Object { $_ -match '^(OPENAI_MODEL|OPENAI_EMBEDDING_MODEL|OPENAI_RERANKER_MODEL)=' })
@{ commit = (git rev-parse HEAD); containerId = $metadata.Id; imageId = $metadata.Image; startedAt = $metadata.State.StartedAt; restartCount = $metadata.RestartCount; models = $modelNames } | ConvertTo-Json | Set-Content quality/evaluation/reports/provider-budget-runtime.json
# 只读列出现有文档活动版本；登录不触发模型，不调用上传/重试/入库接口。
@'
import sys
sys.path.insert(0, 'scripts/evaluation')
from eval_v2 import Client, write_json
client = Client('http://127.0.0.1:18080/api')
response = client.request('GET', '/admin/knowledge-documents', principal='admin')
assert response['status'] == 200
fields = ('publicId', 'title', 'documentType', 'productId', 'activeVersionId', 'latestStatus')
versions = [{k: row.get(k) for k in fields} for row in response['body']]
write_json('quality/evaluation/reports/provider-budget-active-versions.json', versions)
print('Recorded existing document versions:', len(versions))
'@ | python -B -
# 确认预算启用且used=0；仅检查日志和既有活动版本，不发送模型探测。
& $docker logs $container 2>&1 | Select-String 'Evaluation provider'
python -B scripts/evaluation/eval_v2.py run --suite quality/evaluation/datasets/rag-diagnostics-v1.json --package data/retail-holdout-v2 --mode cloud --allow-cloud --max-chat-turns 3 --workers 1
# 将$run设为刚刚输出RUN对应目录的responses.raw.json；不可复用旧运行。
python -B scripts/evaluation/capture_evidence.py --run $run --docker $docker --trace-directory quality/evaluation/reports/rag-traces --require-trace-steps seen-screen:0 unseen-screen-wording:0 unseen-brightness-control:0
& $docker inspect --format '{{.Id}} {{.State.StartedAt}} {{.RestartCount}}' $container
& $docker logs $container 2>&1 | Select-String 'Evaluation provider' | Set-Content quality/evaluation/reports/provider-budget-cloud.log
& $docker compose @cloud stop
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --no-build --wait --wait-timeout 180
```

在请求失败、额度用尽或trace缺失后保留失败证据，不重启补额度、不自动重跑。三个指定步骤缺少任何trace时，采集器会写incomplete并失败；不得把仅有一份trace当作完整。最后结合原问题、每阶段候选和实际引用正文做语义复核。未经实际运行，不宣称原屏幕题已经修复，也不改变12/14首次新来源留出与1/2已见回归成绩。
