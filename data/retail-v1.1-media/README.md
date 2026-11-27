# 华为手机与耳机实拍图

本包为[已有商品和知识手册](../retail-v1.1/README.md)补入 14 张实拍图，覆盖 8 款型号、17 个 SKU。图片来自摄影者在 Wikimedia Commons 发布的作品。华为中国的型号和规格由原目录提供。照片用于识别外观，同型号各容量共用图片。两款耳机的照片摄于日本卖场，图片本身仅作为产品外观证据。

| 型号 | 已收录视图 |
| --- | --- |
| Pura 80 Pro、Mate 70 Pro+、nova 14、nova 14 Pro、nova 14 Ultra | 各一张正面和一张背面 |
| Mate X6 | 展开与合拢样机同台、红色机身背面 |
| FreeBuds 6、FreeClip | 各一张耳机与开盖充电盒 |

[图片索引](media.json)逐图记录本地路径、SKU、手册 ID、中文图注、摄影者、原作标题、来源页、许可、像素尺寸和 SHA-256。仓库保留由 Wikimedia 提供的 1280 像素宽展示图。原尺寸文件已下载到本次工作区的 `local-datasets/retail-v1.1-media/originals/`，文件名为图片 ID 加 `.jpg`。在其他机器上可用索引的 `original.url` 取回原件，并用 `original.sha256` 核对。每张图按各自注明的 CC 许可使用，转载时保留作者、链接、许可和缩放说明。[FreeBuds 6 原作示例](https://commons.wikimedia.org/wiki/File:HUAWEI_FreeBuds_6.jpg)

Pura 80 普通版及 Mate 70 普通版的 6 个 SKU 登记在 `missingImages`。前者检索到的近似图片属于其他版本，后者的候选照片为多机展台。两组均保留空缺。

在仓库根目录执行 `python -B scripts/evaluation/validate_retail_media.py data/retail-v1.1-media` 可离线检查图片字节、来源字段及 SKU、手册的关联。
