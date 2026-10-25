# CI后续：等待每次404恢复响应再断言

`56c0cb62` 的[CI运行](https://github.com/Titans23/serviceflow/actions/runs/40173535275)中，数据契约、前端、密钥扫描、后端单元/门禁、后端集成均成功；最后Playwright失败。失败场景为 `404 stays unknown across reload and navigation without blocking consultation`，预期reads=3，断言时收到2。该次浏览器结果是9通过、1失败、3项串行后续未运行，保留为失败，不写成全通过。

旧测试在缺失done、刷新和返回客服页之后，仅等待“再次核验结果”按钮可见；这个按钮可在只读GET完成前出现，导航或计数断言可能先执行。修复只在三个触发点前建立 `page.waitForResponse`，等待各自真实GET `/result` 返回404，再进行下一步。仍要求总reads=3、确认仅1次、数据库 `PROCESSING / version=0 / operation=0 / refund_request=0`，未降低次数或放宽副作用断言，也未增加固定sleep。

本地重新生成独立Demo夹具 `202610251548576640`，真实HTTP/Redis/server重启恢复31/31通过；然后同一批新夹具下4 workers执行13项浏览器，**13/13通过**，区分7个真实后端恢复场景和6个mock界面场景。此前未运行的3项也实际执行。404场景记录reads=3、confirmations=1、数据库零副作用。修改文件ESLint/Prettier检查通过。

没有改应用代码或重跑任何付费实验。[真实云业务批次](cloud-business-recovery-2026-10-25.md)的6/6、79项、14次provider记录保持原样；这是验收脚本异步等待缺口的后续修复。原CI日志、本地browser输出和新夹具证据只保存在忽略的reports目录。
