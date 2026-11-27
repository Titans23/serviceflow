# 在本地读写商品与手册语料

专用 Compose 项目是 `serviceflow-retail-corpus`，应用 API 监听 `127.0.0.1:28480/api`。MySQL 数据库为 `serviceflow_eval_retail_corpus`，Milvus 集合为 `retail_manuals_bge_zh_v15`。两个存储只在此项目的容器网络内开放，数据保存在独立卷中。图片由各包的 `media.json` 关联，以本地文件提供给后续多模态问答。

本机已实际导入华为 v1.1 与新增厂商包。逐字段回读匹配 **41 个商品条目**。**28 份独立文档、218 个独立章节**按 SKU 绑定后，产生 **42 个活动文档和 327 个 Milvus 分块**。本地生成 512 维查询向量并限定商品、活动版本，PinePhone 卡槽、Pangolin 屏幕、FreeBuds 6 音量三次均在第一位命中指定章节。这三个样例用于验证数据能取回。

从仓库根目录运行下列只读检查，可核对这两包的 MySQL 事实字段、每个活动版本的章节和三个真实向量检索结果。

```powershell
python -B scripts/evaluation/check_retail_corpus.py
```

### 在另一台机器恢复

1. 准备 Docker 与 Python。下载公开的 BAAI 中文嵌入模型到工作区的 `local-datasets/retail-official-v1/model/`。下例使用 Hugging Face CLI，固定模型版本。模型权重、许可证信息和配置保留在本机。[模型来源](https://huggingface.co/BAAI/bge-small-zh-v1.5/tree/7999e1d3359715c523056ef9478215996d62a620)

   ```powershell
   hf download BAAI/bge-small-zh-v1.5 --revision 7999e1d3359715c523056ef9478215996d62a620 --include "*.json" "vocab.txt" "model.safetensors" "README.md" --local-dir local-datasets/retail-official-v1/model
   Get-FileHash local-datasets/retail-official-v1/model/model.safetensors -Algorithm SHA256
   ```

   `model.safetensors` 的 SHA-256 应为 `354763b9b1357bc9c44f62c6be2276321081ed2567773608c0d0785b61d5a026`。CPU 嵌入服务从只读目录载入权重。

2. 启动专用环境，核对运行信息。聊天为 Demo，真实 RAG 适配器的模式名为 `cloud`。此配置将嵌入地址固定为容器内的 `http://embedding:80/v1`，运行时不需要云密钥。首次构建和镜像下载需要联网。[Compose 配置](../../deploy/retail-corpus-compose.yml)

   ```powershell
   docker compose -p serviceflow-retail-corpus -f deploy/retail-corpus-compose.yml up -d --build --wait --wait-timeout 300
   Invoke-RestMethod http://127.0.0.1:28480/api/actuator/info
   ```

3. 使用现有导入器，分别保存两包的回执。这里的 `--allow-cloud-ingestion` 是现有导入器对真实 RAG 适配器要求的开关，下面两个命令的目标均为此本机环境。首次导入后再做回读验证。对同一实例重复导入时保留原回执，新建实例应使用对应的新回执路径。

   ```powershell
   python -B scripts/evaluation/import_retail.py --base-url http://127.0.0.1:28480/api --package data/retail-official-v1 --receipt quality/evaluation/reports/local-corpus-official-v1.json --allow-cloud-ingestion --wait-seconds 300
   python -B scripts/evaluation/import_retail.py --base-url http://127.0.0.1:28480/api --package data/retail-v1.1 --receipt quality/evaluation/reports/local-corpus-huawei-v1.1.json --allow-cloud-ingestion --wait-seconds 300
   python -B scripts/evaluation/check_retail_corpus.py
   ```

暂时停用时运行 `docker compose -p serviceflow-retail-corpus -f deploy/retail-corpus-compose.yml stop`。使用相同项目名重新启动会载入之前的数据库卷。
