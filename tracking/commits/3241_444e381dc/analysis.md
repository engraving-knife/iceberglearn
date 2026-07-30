# 提交 3241：OpenAPI: Remove specific table spec versions from description (#15277)

## 提交信息

- **序号**：3241 / 4088
- **哈希**：444e381dc786d6b61d8325e9526b816f881307cc
- **短哈希**：444e381dc
- **日期**：2026-02-12
- **作者**：Manu Zhang
- **提交说明**：OpenAPI: Remove specific table spec versions from description (#15277)
- **PR/Issue**：#15277

## 总体目的

本提交修改了 REST Catalog OpenAPI 规范文件（`rest-catalog-open-api.yaml`）顶部的描述文字。原描述写道"Implementations should ideally support both Iceberg table specs v1 and v2, with priority given to v2."（实现方应理想地同时支持 Iceberg 表规范 v1 和 v2，优先支持 v2），新描述改为"Implementations should ideally support all Iceberg table spec versions."（实现方应理想地支持所有 Iceberg 表规范版本）。

修改的动机是：Iceberg 表规范正在持续演进，spec v3 已经在开发中（包含行血缘/row lineage 等新特性）。原描述硬编码了"v1 和 v2"两个特定版本，一旦 v3 发布就会过时，需要再次修改。更重要的是，原描述中"with priority given to v2"的表述也已不再准确——随着 v3 的到来，不应继续固化 v2 为优先版本。将描述改为泛化的"all Iceberg table spec versions"使规范文档具有前向兼容性，无需在每次新版本发布后都更新这段文字。

## 如何达成设计目的

直接修改 `open-api/rest-catalog-open-api.yaml` 文件 `info.description` 字段中的一行文字，将具体的版本枚举（"both v1 and v2, with priority given to v2"）替换为泛化表述（"all Iceberg table spec versions"）。改动极小，仅一行一个词的替换。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-1 lines)

**修改目的**：将 REST Catalog OpenAPI 规范描述中的特定表规范版本（v1、v2）替换为泛化表述。

**工作逻辑**：在 `info.description` 字段中，将 `Implementations should ideally support both Iceberg table specs v1 and v2, with priority given to v2.` 改为 `Implementations should ideally support all Iceberg table spec versions.`。该描述位于 OpenAPI 文档的元信息区，用于向 REST Catalog 实现方传达应支持的表规范版本范围。移除硬编码版本号和优先级声明后，描述不再随规范版本演进而需要更新，同时也不再暗示 v2 是当前最优版本（为 v3 留出空间）。

## 总结

本提交是对 REST Catalog OpenAPI 规范描述文字的小幅修正，将硬编码的"Iceberg table specs v1 and v2, with priority given to v2"泛化为"all Iceberg table spec versions"。这一改动使规范描述具有前向兼容性，避免在 spec v3 及未来版本发布后需要反复修改，同时也移除了已过时的"v2 优先"声明。虽改动仅一行，但体现了规范文档维护中对版本演进的前瞻性考虑。
