# 约5分钟：业务Agent确认、执行与恢复

主线是“客户同意后执行；结果不明时核验；不能为了恢复而重复取消”。演示使用隔离Demo模型和退款沙箱，HTTP、MySQL、Redis及浏览器恢复路径是真实服务。云回答质量使用已有评测证据单独说明，不把Demo静态回答当作真实RAG结果。这里不涉及真实付款或支付渠道。

## 演示前准备（不计入5分钟）

从仓库根目录执行。需要已安装Docker、Python、Node及项目依赖。不要在现场安装依赖、上传知识或跑全套测试。Cloud与Demo共用宿主端口；若Cloud仍运行，先按[运行手册](evaluation-and-refund.md)停止并保留卷。

```powershell
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --build --wait --wait-timeout 300
python -u -B scripts/evaluation/recovery_demo.py --docker $docker --allow-isolated-faults
```

第二条命令会新增独立合成客户与99元已付款订单，在上游提交后丢弃确认响应，删除**该合成动作**的缓存键并重启评测server；还检查权限、并发确认和重复退款回调。它只接受 `serviceflow-eval` / `serviceflow_eval_v2` / Demo+退款沙箱，正常应输出31项PASS及最后的 `REPORT .../summary.json`。不使用 `--baseline`，不清空数据库或Redis。

从本次REPORT路径设置下面的目录，勿挑选旧夹具。该脚本已经验证了取消→沙箱退款成功，另留了浏览器场景尚未执行的订单。

```powershell
$run = 'D:\learn_project\ServiceFlow\quality\evaluation\reports\recovery-<本次runId>'
$env:RECOVERY_FIXTURES = Join-Path $run 'fixtures.json'
$env:DOCKER_BIN = $docker
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:15173'
```

预先打开以下内容：

- 浏览器 `http://127.0.0.1:15173`；API为 `http://127.0.0.1:18080/api`，不是主Compose的5173/8080。
- 本次 `$run/summary.json` 和下面浏览器命令的终端。合成账号从 `fixtures.json` 的 `principals.customer/other` 取，密码为本地夹具的 `Customer123!`；不要在演示或分享中展示令牌。
- [取消恢复验收](../../quality/evaluation/results/recovery-validation-2026-10-25.md)、[刷新与身份隔离验收](../../quality/evaluation/results/recovery-refresh-validation-2026-10-25.md)、[云语义评测报告](../../quality/evaluation/results/cloud-final-evaluation-2026-10-25.md)。历史记录用于对照，不冒充本次运行结果。

浏览器依赖缺失时，在准备阶段进入 `apps/serviceflow-web` 执行 `npm.cmd ci` 和 `npx.cmd playwright install chromium`。每次重新排练浏览器副作用场景，都重新执行准备脚本生成新夹具；不能复用已经取消的订单。

## 现场顺序

| 时间 | 展示动作 | 要解释的证据 |
| --- | --- | --- |
| 0:00–0:40 | 打开现有云语义报告中的屏幕题与来源约束说明 | 规则通过不代表事实正确；模型选择证据，后端校验编号。最新4场景/7轮开发诊断验证商品切换及未知SKU边界，自然二轮1次仍保留资料不足；不代表新独立留出或稳定性。 |
| 0:40–1:15 | 展示本次summary中的未确认、游客/其他客户检查 | 提出取消只生成待确认动作，未确认时操作/退款计数为0；身份与订单归属由后端验证，不靠提示词或前端判断。 |
| 1:15–2:30 | 运行下方3个真实浏览器场景；先看客户确认和提交后缺失done、刷新恢复 | 确认POST只发送1次；故障后请求是GET结果核验。数据库返回 `CANCELLED / version=1 / operation=1 / refund_request=1`。 |
| 2:30–3:20 | 查看浏览器的404与换账号场景 | 404保持未知，不自动补发取消；可以继续咨询。换账号/退出清除本地线索，陈旧标识不能查询或显示前一个客户结果。 |
| 3:20–4:15 | 打开本次summary的 `states.lost-response` 和退款回调检查 | 真实取消创建等额99元退款请求；管理员沙箱回调后为REFUNDED，重复回调仍只有1条事件，恢复查询没有新副作用。 |
| 4:15–5:00 | 对照浏览器输出和现有验收，解释组件职责与边界 | 证据支持这些受测故障路径；不宣称跨设备自动恢复、通用自主Agent、真实支付或生产可靠性。 |

在仓库根目录、同一个已经设置环境变量的PowerShell中运行：

```powershell
Push-Location apps/serviceflow-web
npx.cmd playwright test e2e/recovery-refresh-live.spec.ts --workers=1 --headed --grep 'committed cancellation survives reload|404 stays unknown|account change and logout'
Pop-Location
```

这3例通过浏览器执行真实登录、会话、确认和只读查询；仅在故障边界移除done、断开一次查询或阻止确认发出，不伪造成功业务响应。测试从MySQL读回计数，并将 `browser-refresh-committed.json`、`browser-refresh-unknown.json`、`browser-refresh-identity.json` 写到本次 `$run`。为避免令牌进入产物，这些场景关闭trace、截图和录像。

首例预期 `confirmations=1, reads=2, databaseState=CANCELLED\t1\t1\t1`；后两例没有提交取消，数据库仍为 `PROCESSING\t0\t0\t0`。缺少 `RECOVERY_FIXTURES` 会明确跳过，不能把跳过当作演示成功。若本次失败，展示失败及已有记录的版本，不现场改断言或重放同一副作用来凑成功。

## 面试时说清贡献边界

LangGraph4j提供状态图和Checkpoint，Spring AI提供模型协议适配，Qwen提供意图/证据判定等模型能力；MySQL提供事务与约束，Redis保存会话和待确认线索。项目实现的是这些组件之间的业务协议：授权、显式确认、取消条件更新、操作幂等账本、同事务退款请求、未知结果处理和只读恢复。Checkpoint本身不能证明业务可以安全重放；sessionStorage仅作身份分区的关联线索，不能授权执行。

已有[31项恢复检查](../../quality/evaluation/results/recovery-after-2026-10-25.summary.json)与[13项浏览器验收](../../quality/evaluation/results/recovery-refresh-2026-10-25.summary.json)支持当前展示，其中浏览器验收区分7个真实后端恢复场景与6个mock界面场景；后续[商品上下文修复验收](../../quality/evaluation/results/rag-context-offline-2026-10-25.md)全套扩为16项（7个真实后端、9个mock），历史13项口径保留。关闭标签、清空存储或跨设备的自动恢复不在保证范围内。

另有[一次真实模型多轮业务验收](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.md)：6场景、14轮、79项检查，模型参与意图分类，订单/退款参数和副作用仍由后端约束。现场可展示该证据，不需要临时启动付费批次；它不包含本次云模式的浏览器/服务重启验收。

云质量保留首次新来源留出12/14、已见失败回归1/2及[此前3例屏幕/亮度诊断3/3](../../quality/evaluation/results/rag-cloud-diagnostics-2026-10-25.md)。[最新多轮与自然二轮诊断](../../quality/evaluation/results/rag-context-followup-2026-10-25.md)按固定4场景/7轮一次完成，31次provider尝试；商品切换和未知SKU边界通过，5轮完整段落与引用对应，最后一题自然二轮仍判资料不足并保留部分回答。没有补到新证据，不能说二轮提高了召回；这是代理辅助逐项来源核对，不是新独立人类准确率。后续重跑需要新授权，不在五分钟业务演示中触发。退款渠道是项目沙箱，未实现真实支付签名、资金结算和对账。
