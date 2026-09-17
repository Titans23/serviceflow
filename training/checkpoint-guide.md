# 最终模型恢复与新训练

最终模型是 Grader seed 42 / epoch 2，训练已完成 76 步。完整状态在 `runtime-data/training/grader-specialist-exp-01/seed-42/checkpoints/checkpoint-76/`。

## 校验现有状态

```powershell
python -B training/cli.py resume-final --verify-state
```

检查封存训练源码与原续训契约一致，并验证 checkpoint 文件哈希。不会启动训练。

## 使用原引擎恢复

在固定 WSL 训练环境中，从仓库根目录运行：

```bash
/home/titans/venvs/serviceflow-train-py312/bin/python -B training/cli.py resume-final --run
```

恢复附件位于最终实验目录的 `resume-engine/`，只包含原最终训练所需的 8 个源码文件。它创建指向现有资源的受检查链接，保持原代码哈希和训练参数。原训练已经完成 2 个 epoch；恢复不等于追加 epoch。不要修改原契约或配置来延长训练。

## 新实验

用 `data prepare` 生成新的输出目录，再用 `train --run-dir ... --reviewed ...`。新运行使用整理后的引擎，不能用它直接承接整理前的源码哈希契约。

每个 epoch 完成后保存完整 checkpoint，只保留最新完整状态。adapter 不包含优化器或随机数状态，不能代替完整恢复文件。暂停在对应运行目录创建 `STOP_REQUESTED`，等待运行到 epoch 边界并记录 PAUSED 后再关闭机器。

[真实 Trainer CPU 检查](tests/integration/README.md) 只在更改恢复机制或依赖时运行；本轮未重新训练模型。
