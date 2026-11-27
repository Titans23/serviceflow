# 厂商商品、手册与接口图片

2026-11-27 取自 System76 技术文档和 PINE64 官方资料。包中有 **18 个目录条目、17 份中文文档和 15 份英文原厂手册检索版**。中文 130 节，英文 476 节。65 条可用图像关联对应 54 张不同内容的图片，所有条目都有手册和图片。另有一张厂商图注与规格页不符，单列供追溯。

| 产品 | 收录范围 | 可用资料 |
| --- | --- | --- |
| System76 笔记本 | 15 个硬件代号，涉及 Lemur Pro、Pangolin、Galago Pro、Darter Pro、Oryx Pro、Gazelle、Serval WS、Kudu、Adder WS、Bonobo WS | 每个代号有规格、外观、内部结构、维修四章原文。图像索引按整机、左侧、右侧、后侧四个方向关联。[厂商技术文档](https://system76.com/tech-docs/) |
| PinePhone Beta Edition | 2 GB / 16 GB 普通套装和 3 GB / 32 GB Convergence 套装 | 官方发布的两种配置共用外观与首次使用说明。卡槽示意图标出 microSD 和 micro-SIM 的位置。[厂商配置公告](https://pine64.org/2021/03/19/beta-edition-pre-orders/) · [首次使用手册](https://pine64.org/documentation/PinePhone/Getting_started/) |
| PinePhone Pro | 4 GB / 128 GB | 记录屏幕、容量和 SIM 卡托的批次差异，配有机背、开关区域和包装图片。[厂商规格](https://pine64.org/documentation/PinePhone_Pro/Further_information/Specifications/) · [首次使用说明](https://pine64.org/documentation/PinePhone_Pro/First_time_setup/) |

### 文件怎么用

- [商品目录](catalog.json)供现有管理 API 写入 MySQL。SKU 为本项目的稳定键，System76 按厂商硬件代号登记。内存上限、显示屏选项和实际装机配置在字段中分别注明。店铺报价留空，`OFF_SALE` 表示本地目录状态。
- [清单](manifest.json)登记 65 个来源页面，并为中英文章节指定来源 ID 和原文小节。运行时导入 [knowledge](knowledge/) 中的 Markdown。英文版覆盖规格、外观、内部结构和维修四章的正文、表格和步骤，在检索版中统一标题并用图注和文件名代替内嵌图片。相同 Beta Edition 手册会按两个 SKU 分别绑定，统计独立文档时只算一份。
- [图片索引](media.json)给出本地路径、型号、SKU、手册、适用章节、方向、图注、作者、许可、像素和 SHA-256。59 条可用 System76 图像关联还以 `originalSection` 精确指向厂商原文中的图示。Gazelle 的 15 英寸右侧图注标作 RTX 3060，而 gaze18 规格页列出 RTX 3050，该图保留在 `excludedImages`。图片来自厂商技术文档或官方媒体页，保留原始字节。
- [原版手册正文 ZIP](originals/system76-technical-manuals.zip)含 15 个型号的 60 个未经修改的英文 Markdown/MDX 章节。仓库另放常用的整机图和接口图。每款型号的完整图文源目录还已下载到本工作区的 `local-datasets/retail-official-v1/manuals/<硬件代号>.zip`，15 个压缩包共含 643 个上游文件，另附署名与许可。MDX 是厂商网页的源文件，第三方视频及外链内容仍需联网。
- [手册索引](manuals.json)记录正文的固定下载地址、逐章校验和、完整本地图文包的文件名及校验和。PINE64 两册官方单页 HTML 和一份普通版官方快速入门 PDF 也已下载至同一本地目录。PINE64 原文的公共再分发范围尚未确认，公开包收录出处、中文事实转述及有明确授权的图片。

例如，查询“PinePhone 普通版的 SIM 卡插哪层”时，可从 `PINE64-PINEPHONE-BETA-2-16` 找到 `pinephone-beta` 手册的 `cards` 章节，再取 `pinephone-beta-card-slots` 原图。图中标的是 micro-SIM，Pro 版本的卡托有出货批次差异。[中文章节](knowledge/pinephone-beta.md) · [卡槽原图](images/pinephone-beta-card-slots.png)

### 来源与使用范围

System76 原文和图片来自厂商技术文档仓，许可为 CC BY-SA 4.0。中文硬件说明为节选与翻译，同样以 CC BY-SA 4.0 提供，保留署名并注明修改。PINE64 的 6 张宣传或说明素材在官方媒体页逐图标明作者 Funeral、许可 CC0。各文件使用相应来源的许可。网页链接、媒体下载地址与本地文件均分开登记。[System76 固定版本及许可](https://github.com/system76/tech-docs/blob/1ab5221aa02e3ca9019d91f82f49ee1312b84f2a/LICENSE) · [PINE64 官方图片署名页](https://pine64.org/documentation/PinePhone/Further_information/Press/)

离线执行 `python -B scripts/evaluation/validate_retail_media.py data/retail-official-v1` 可核对商品、文档、图片和公共手册正文。既有华为型号的实拍图另见[华为照片包](../retail-v1.1-media/README.md)。

两包已在隔离 MySQL 与 Milvus 中导入并回读。数据库地址、实际检索结果和重建命令见[本地语料使用说明](local-import.md)。
