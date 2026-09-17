# 维护边界

所有工具通过 `training/cli.py` 运行；不要依赖内部模块的文件路径。包内使用绝对模块导入，子进程继承同一 `PYTHONPATH`。

- `core/contracts.py`：实际 Java 输入契约、数据校验、导出和准备；`core/hashing.py`：大文件哈希。
- `data/samples.py`：从旧数据生成脚本提取的共享序列化函数；没有旧数据批量生成入口。
- `engine/`：训练、模型合并与完整 checkpoint 保存。原最终 checkpoint 使用单独封存的原引擎恢复。
- `evaluation/client.py`：唯一维护的流式评测协议；`metrics.py`：Grader 无序集合评分与来源分组区间；`tasks.py`：其他任务回归代理指标。
- `evaluation/java.py`：固定主候选的临时服务验收；不会启动旧业务模型。
- `environment/`：低频安装、下载和验证工具。公开数据集下载分支已经删除，只保留本轮必要的模型下载。
- `runtime/status.py`：仅检查最终模型清单，不包含业务服务编排。

历史 pilot、业务 v1/v2 数据生成/选型、独立旧压力集生成、旧部署栈和一键双种子实验脚本均已退出维护。原始比较记录保留，因此不会把副种子的成功结果或失败案例从报告中抹去。
