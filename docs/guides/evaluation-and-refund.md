# 评测 v2 与取消—退款运行手册

本轮把公开商品资料、可复现的多轮回归和退款状态流转连接起来。付款渠道仍是显式启用的管理员沙箱，没有真实资金转移。当前验证记录见 [2026-10-24 Docker 验收](../../quality/evaluation/results/v2-docker-verification-2026-10-24.md)，此前离线检查也保留在结果目录。

2026-10-25 已完成[真实 RAG 小规模云验证](../../quality/evaluation/results/cloud-rag-smoke-2026-10-25.md)，范围与复现方式见第 7 节。

同日完成[完整开发集、冻结留出与修复报告](../../quality/evaluation/results/cloud-final-evaluation-2026-10-25.md)，批次为 175 + 25 + 2 个场景。完整开发集需新建订单夹具，允许 192 轮聊天；留出集有 28 轮。`--workers 3` 可限制并发，`--case-ids` 可选择已知失败回归。原始留出已用于缺陷修复，后续重跑应标记为已见回归，不再视为独立留出成绩。

## 1. 不依赖 Docker 的检查

在仓库根目录执行，需 Python 3.10+、JDK 21、Node.js 与已安装的项目依赖：

```powershell
python -B scripts/evaluation/eval_v2.py validate
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/datasets/refund-sandbox-v1.json
python -B -m unittest discover -s scripts/evaluation/tests
python -B -m unittest discover -s training/tests

Push-Location apps/serviceflow-server
mvn -DskipITs verify
Pop-Location
Push-Location apps/serviceflow-web
npm run lint
npm test -- --run
npm run build
npm run test:e2e
Pop-Location
```

本地 Maven 缓存齐全时可加 `-o`。Playwright 需要已安装 Chromium；这 6 条测试主要使用 API mock，不能代替后面的真实服务验证。无 Docker 时不要把 `-DskipITs` 的通过写成完整集成测试通过。

## 2. Docker 可用后启动独立环境

以下命令全部在仓库根目录运行。配置只使用公开演示凭据，不继承 `.env` 的云模型密钥；使用独立 project/volume、数据库 `serviceflow_eval_v2` 和 loopback 端口，MySQL/Redis/RabbitMQ 不暴露宿主端口。

```powershell
# Docker 安装在 D 盘且当前终端尚未更新 PATH 时：
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml config --quiet
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --build --wait --wait-timeout 300
Invoke-RestMethod http://127.0.0.1:18080/api/actuator/info
```

Web 地址为 `http://127.0.0.1:15173`，API 为 `http://127.0.0.1:18080/api`。info 应为 `aiMode=demo`、`ragMode=demo`、`evaluationEnabled=true`、`refundSandboxEnabled=true`。不要用主环境的数据库执行下面的夹具 SQL。

服务器镜像使用 `build-settings.xml` 限定当前发布版依赖到 Maven Central，并用 BuildKit 缓存 Maven 下载；首次仍需联网。内存紧张时先完成构建/启动，再运行测试，避免同时启动两套 MySQL 和 Java 构建进程。仅运行数据库集成测试可在后端目录执行 `mvn -DforkCount=0 failsafe:integration-test failsafe:verify`，前提是已编译当前主代码和测试；这不等价于全量 verify。

## 3. 导入公开商品与知识资料

```powershell
python -B scripts/evaluation/import_retail.py --base-url http://127.0.0.1:18080/api --wait-seconds 180
```

成功条件是包内文档最新版本均 READY 且成为活动版本。导入不会清空既有商品；新包售价未知，因此界面显示“暂无报价”。同型号容量 SKU 的说明书复用原文，不计算为独立知识增长。Demo 不创建真实 Milvus 向量，不能据此宣称检索质量通过。

## 4. 新建合成订单并执行真实退款场景

```powershell
$suite = 'quality/evaluation/datasets/refund-sandbox-v1.json'
python -B scripts/evaluation/prepare_v2_fixtures.py --suite $suite
# 把下面路径替换为生成命令打印的本次目录，不要复用已被测过的订单。
$fixtureDir = 'quality/evaluation/reports/fixtures-<本次编号>'
$savedEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    Get-Content -LiteralPath "$fixtureDir/fixtures.sql" -Encoding UTF8 |
        & $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml exec -T mysql mysql --default-character-set=utf8mb4 -userviceflow -peval-only-password serviceflow_eval_v2
    if ($LASTEXITCODE -ne 0) { throw '夹具导入失败，不得继续评测' }
} finally {
    $OutputEncoding = $savedEncoding
}
python -B scripts/evaluation/eval_v2.py run --suite $suite --fixtures "$fixtureDir/fixtures.json" --mode demo
```

生成器只生成 SQL/JSON，不连接数据库；SQL 仅追加本次专属的两个客户账号与订单，先校验数据库名必须以 `serviceflow_eval_` 开头，事务内写入。不要加 mysql 的 `--force`，否则可能跳过保护错误。重复导入同一份夹具会因唯一约束失败，应生成新的一份。

退款专项通过需满足 10 个场景全部通过，并核对摘要里每项断言的分母。它通过真实 HTTP 执行聊天、确认、订单读取、管理员回调和重试；与浏览器 API mock 测试是两组证据。本机已通过 10/10，CI 已配置这套流程，远程 CI 尚未触发。

## 5. 主评测与真实模型

主集仍需单独生成夹具：将 `--suite` 换为 `quality/evaluation/datasets/serviceflow-eval-v2.json`，在同一独立数据库追加导入新 SQL。运行默认 `--split development`，冻结实现后再跑 `--split holdout`。

Demo 可检查业务/SSE/权限连通性，知识题失败应如实保留。真实 RAG 和回答质量需要另一个经批准的隔离 Cloud 环境，包含 Milvus、活动文档向量和模型配置；不要直接把 Demo 的导入回执视为云索引完成。上传资料和运行模型前应确定公开数据范围、预估调用数和预算，分别显式传入 `--allow-cloud-ingestion`、`--allow-cloud --max-chat-turns N --mode cloud`。本轮没有调用收费模型。

主集全部有 220 个聊天步骤。`max-chat-turns` 只校验预定聊天数量，不控制单轮内部模型请求数或费用。数据来源与评分口径见[评测说明](../../quality/evaluation/README.md)。参考答案仍需独立人工语义复核。

定向3例RAG诊断另有[已离线验证的27次provider出站上限](../../quality/evaluation/results/provider-budget-validation-2026-10-25.md)，需显式叠加 `deploy/evaluation-provider-budget.yml`。该模式关闭自动重试及后台入库；预算仅属于一个Java进程，运行期间禁止重启/扩容。报告包含批准后执行的命令与逐步骤trace采集要求；没有预算查询/重置API，也不代表已获得新付费调用授权。

## 6. 业务边界与演示

客户页 `/orders` 展示订单、退款金额、失败原因和处理次数，查询受订单所有者权限控制。客服聊天出现取消确认卡片后，由用户明确确认；拒绝确认、换订单、重复确认和发货后取消都有回归场景。

管理员 `/admin/refunds` 默认只读。设置 `SERVICEFLOW_REFUND_SANDBOX_ENABLED=true` 才会创建回调/重试接口并显示按钮；必须同时具有 ADMIN 权限。流程：

```text
已付款且可取消订单 → 用户确认 → CANCELLED + refund PROCESSING（同一事务）
PROCESSING → 模拟成功 → SUCCEEDED / payment REFUNDED
PROCESSING → 模拟失败 → FAILED / payment FAILED
FAILED → 管理员重试 → PROCESSING，attemptNo + 1
未付款订单 → 用户确认 → CANCELLED，不创建退款任务
```

退款金额取原订单金额，每单唯一请求。回调记录唯一 eventId，校验退款、金额、次数、结果与原因；同事件相同内容重放不重复写状态，同 ID 不同内容拒绝。旧尝试的迟到结果只记审计，未来次数与同次冲突终态拒绝。请求、支付状态与事件在一个事务中提交，任一步失败一起回滚。重试使用 `expectedAttemptNo` 防止重复点击多增次数。

V6 迁移允许未知商品报价；V7 增加退款请求/事件及支付 FAILED 状态。历史 `PROCESSING` 付款不自动伪造为新退款请求，升级已有数据库需另行核对历史记录。本机 8 个 Testcontainers 测试已通过，包括重复并发回调和事务回滚；完整检查仍可执行 `mvn verify`。

真实支付接入仍需渠道请求、签名验证、对账、资金金额与币种校验、未知结果查询及补偿机制。当前全额退款沙箱不表示已具备这些能力，也不支持部分退款或退货逆向物流。

停止独立环境可执行 `docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml down`，不加 `-v` 保留验证数据。删除卷属于清空数据库，应在确认不再需要后单独处理。

## 7. 小规模真实 RAG 与 Cloud 验证

此流程会调用收费的百炼服务。以下是已执行流程的复现说明；后续新运行仍需事先确认数据与费用范围。只使用公开资料和项目编写问题，不运行整个 200 场景集合。

```powershell
python -B scripts/evaluation/prepare_cloud_smoke.py
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/reports/cloud-smoke-inputs/suite.json --package quality/evaluation/reports/cloud-smoke-inputs/package

$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
# 两套隔离环境使用相同宿主端口；停止 Demo，不删除数据卷。
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml stop
# .env 使用已有百炼配置；仅校验，禁止打印展开后的含密钥配置。
& $docker compose --env-file .env -p serviceflow-cloud-eval -f deploy/evaluation-cloud-compose.yml config --quiet
& $docker compose --env-file .env -p serviceflow-cloud-eval -f deploy/evaluation-cloud-compose.yml up -d --build --wait --wait-timeout 300

python -B scripts/evaluation/import_retail.py --package quality/evaluation/reports/cloud-smoke-inputs/package --receipt quality/evaluation/reports/cloud-smoke-import.json --allow-cloud-ingestion --wait-seconds 180
python -B scripts/evaluation/eval_v2.py run --suite quality/evaluation/reports/cloud-smoke-inputs/suite.json --package quality/evaluation/reports/cloud-smoke-inputs/package --mode cloud --allow-cloud --max-chat-turns 10
```

子包包含 Pura 80、nova 14 各一个 SKU 和一份手机保修资料，共 3 个文档 / 24 个章节；9 个场景共 10 轮对话，并要求无降级、正确引用。若只复测未知报价，用 `price-followup.json` 替换 suite，传 `--max-chat-turns 2`，不重跑其他场景。

云环境数据库和向量卷独立于 Demo；不要把 Demo 的 READY 状态或导入回执直接当作云向量已就绪。入库失败时先查原因，再通过管理员知识库的重试功能重试原版本；不要通过重复上传掩盖失败。修复后的初始化逻辑可在全新集合上直接导入。

完成后可停止云服务并恢复 Demo，保留全部云验证数据：

```powershell
& $docker compose --env-file .env -p serviceflow-cloud-eval -f deploy/evaluation-cloud-compose.yml stop
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --no-build --wait --wait-timeout 180
```

本次云环境使用 Milvus 官方单机内置 etcd / 本地存储模式，免去外置 MinIO；没有迁移或替换主 Compose 的既有向量存储数据。各运行的输入和报告摘要保留 SHA-256，原始回答只存本机。

## 8. 取消结果不确定与只读恢复（不调用云模型）

`POST /api/chat/actions/{actionId}/confirm` 仍要求本人、未过期的待确认动作和显式确认。事务成功提交后，`order_operation`、订单/支付状态和唯一退款请求是执行事实；Redis缓存不是提交结果的唯一载体。

新增 `GET /api/chat/actions/{actionId}/result` 只读查询，返回 `operation` 原结果及 `order` 当前状态（含退款进度）。仅登录客户可用；SQL通过操作→订单→客户关系检查所有者。游客/管理员403；其他客户、无已提交结果或未确认/拒绝/过期未执行的动作404。**404只表示当前没有可访问的已提交结果，不能据此判定未执行，更不能自动重发取消。** 查询接口不会确认动作、创建退款或重试退款。

取消确认的浏览器路径必须收到终止 `done`；连接错误或提前EOF时保留动作标识，进入待核验状态并进行一次只读查询。查询失败或404时可明确再次查询、继续其他咨询或在新标签查看订单。未知结果期间暂缓新的确认，避免覆盖未核验动作；没有自动副作用重试。跨会话核验结果放在关联订单的独立结果卡，不插入其他会话消息。

待核验标识在确认请求发出前写入当前标签的 `sessionStorage`，仅包含版本、登录身份分区、actionId、订单号和确认时会话ID，不保存令牌、完整问题或回答。同一标签刷新或离开后返回客服页时，只读核验一次；404/网络错误保留未知，查询成功清除标识。退出或换账号时清除，旧账号回包不能清除新账号记录或展示旧卡。缓存只是关联线索，不是执行授权。

当前边界：关闭标签、清理浏览器存储、退出或换账号后不保证继续自动恢复，不是跨设备/跨浏览器持久任务系统。损坏或无法读取待核验记录时显示明确提示，不使用其内容执行动作；保存失败时说明不能保证刷新后恢复；已核验但清理失败时说明可能再次只读查询。测试中的存储故障针对待核验键，不代表整个浏览器认证存储被禁用时仍能登录。退款仍是沙箱，不涉及支付渠道超时查询、签名或对账。

复现故障→恢复（会新增独立合成客户/订单，只删除这些动作的Redis键并重启评测server；不清空数据库或Redis）：

```powershell
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --build --wait --wait-timeout 300
python -u -B scripts/evaluation/recovery_demo.py --docker $docker --allow-isolated-faults
# 将下面路径替换为上一步输出目录中的 fixtures.json；每次浏览器复测使用新生成的夹具。
$env:RECOVERY_FIXTURES = 'D:\learn_project\ServiceFlow\quality\evaluation\reports\recovery-<runId>\fixtures.json'
$env:DOCKER_BIN = $docker
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:15173'
Push-Location apps/serviceflow-web
npx.cmd playwright test e2e/recovery-live.spec.ts e2e/recovery-refresh-live.spec.ts --workers=1
Pop-Location
```

`recovery_demo.py` 的代理先接收真实后端提交后的响应，再断开下游连接，证明客户端未获结果而数据库已提交；随后丢失该动作缓存并实际重启server。它验证所有者查询、游客/其他客户拒绝、未确认/拒绝/过期不执行、同动作并发确认、重复回调和最终状态金额。`--baseline` 只用于未修复版本复现原缺口，不能对新版本期望失败。

两个浏览器验收分别覆盖：真实提交后移除done且首次查询断网，手动再查走真实API；确认请求未到服务端且真实查询404，保留未知状态并可继续咨询。均从MySQL读回操作/退款计数，不能用全部API mock替代。浏览器trace/截图关闭，避免将测试登录令牌放入产物。未设置 `RECOVERY_FIXTURES` 时这两个用例明确跳过；CI会先生成夹具再实际执行。

刷新专项另覆盖5例：提交后刷新成功核验并清理、404跨刷新/导航保留、实际登录换账号/退出与陈旧记录隔离、损坏/读取不可用提示、写入失败时仅保留当前页核验。CI会通过同一 `RECOVERY_FIXTURES` 执行全部7个真实恢复用例；不设置夹具时明确跳过，不能计为通过。

## 9. 真实模型多轮业务验收

[已完成的一次固定批次](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.md)使用项目 `.env`：6个合成场景、14轮，79项检查通过，14/27次provider请求；记录参数绑定、权限、聊天/确认/回调重放及提交后丢响应恢复。模型只负责分类，确认和退款使用后端参数；没有真实资金。原始响应留在本机，预期拒绝、未知token/费用与耗时如实保留。

复现入口与边界见[批次计划](../../quality/evaluation/results/cloud-business-plan-2026-10-25.md)。显式叠加 `evaluation-cloud-compose.yml`、`evaluation-provider-budget.yml`、`evaluation-cloud-refund.yml`；最后一层仅为独立Cloud评测开启管理员退款沙箱。脚本 `cloud_business_recovery.py --allow-cloud --allow-isolated-faults` 需要未使用的预算进程，自动生成全新合成夹具，禁止后台入库、重启补额度或自动付费重跑。不要修改默认Cloud配置的沙箱关闭值来代替显式profile。
