# ServiceFlow Grader SFT

## 唯一最终模型

**Qwen3-8B Grader，种子 42 / epoch 2**。由预登记主种子和验证集选出，真实 Java 调用 5/5 通过。身份和路径以 [final-model.json](configs/final-model.json) 为准。

模型只判断证据充分性和支持集合，不承担全任务客服聊天。旧业务 v1 服务已经停止；没有将 Grader 自动接到全部客服请求。

[实验结果](reports/grader-specialist-exp-01-results.md) · [数据卡和预登记](reports/grader-specialist-exp-01.md) · [恢复指南](checkpoint-guide.md) · [代码结构](src/serviceflow_training/README.md)

## 一个入口

从仓库根目录执行：

```powershell
python -B training/cli.py status
python -B training/cli.py --help
python -B training/cli.py resume-final --verify-state
python -B -m unittest discover -s training/tests -v
```

`status` 检查最终产物身份与是否存在，不代表模型服务正在运行。训练/推理命令使用固定 WSL 环境。所有入口的参数用 `<命令> --help` 查看。

| 阶段 | 命令 |
|---|---|
| 数据格式、隔离、导出、配置 | `data validate/export/lengths/prepare` |
| 官方来源和逐条标签审核 | `fetch-sources`、`build-data`、`audit-data`、`review-data` |
| 新实验训练 | `train` |
| 原最终 checkpoint 校验/恢复 | `resume-final`，详见恢复指南 |
| 合并、模型推理评测、离线评分 | `merge`、`evaluate`、`score` |
| 真实 Java Grader 验收 | `java-acceptance --attempt-name java-acceptance-<新名称>` |
| 环境和依赖 | `preflight`、`download-model model`、`download-wheels`、`verify-environment` |

Java 验收启动并关闭自己的临时服务，不恢复旧 v1。不得覆盖冻结数据、原实验目录或原验收记录。

## 目录职责

```text
training/
  cli.py                      # 公共命令入口
  configs/                    # 最终模型身份、预登记和模板
  data/                       # 来源、标签规则、禁止泄漏注册表
  src/serviceflow_training/
    core/                     # 契约、哈希
    data/                     # 来源获取、样本序列化、审核
    engine/                   # 训练、checkpoint、合并
    evaluation/               # 推理、统一评分、Java 验收
    environment/              # 固定环境准备和核验
    runtime/                  # 最终模型状态
  tests/                      # 离线单元测试
    integration/              # 真实 Trainer CPU 检查
  reports/                    # 最终结果和预登记
```

不再保留平铺的 `scripts/` 或旧 v1/v2/pilot 操作入口。样本序列化、AI 审核范围、历史注册表中的版本字符串属于冻结数据协议，不代表仍保留对应模型部署。

## 本地大文件与清理状态

最终模型和记录：`runtime-data/training/grader-specialist-exp-01/`；冻结数据：`local-datasets/serviceflow/grader-specialist-exp-01/`；原始来源：`local-datasets/serviceflow/raw/grader-specialist-exp-01/`。

保留主模型、基座、主种子最终 adapter / checkpoint、900 条数据、来源快照、双种子对照预测及原验收结果。副种子只需要保留训练参数/执行记录及预测，不保留模型权重。过去的失败日志不作为当前入口。

**旧产物清理已完成**：2026-09-17 本机清理回执记录已删除 193 项旧权重、日志和历史产物。最终模型、基座、checkpoint、来源及必要对照证据保留。

`cleanup-retired.ps1` 仅用于本机一次性清理，依赖被 Git 忽略的本地清单；新克隆无需运行。清理和验证记录在 `runtime-data/maintenance/cleanup-2026-09-17/`，不会上传。

模型、数据集、原始响应和运行记录不随 Git 仓库分发。新机器需单独准备这些本地产物；克隆代码不代表已获得训练权重。原实验报告描述实验完成当时的状态；当前保留范围以本文及最终模型配置为准。
