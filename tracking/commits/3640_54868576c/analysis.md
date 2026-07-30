# 提交 3640：Docs: Document general REST catalog properties (#15871)

## 提交信息

- **序号**：3640 / 4088
- **哈希**：54868576cd7458fe7f29ec5e1395c0b9b2e936c5
- **短哈希**：54868576c
- **日期**：2026-05-04 17:51:24 +0200
- **作者**：gaborkaszab
- **提交说明**：Docs: Document general REST catalog properties (#15871)
- **PR/Issue**：#15871

## 总体目的

这个提交为 REST Catalog 文档补充了一直缺失的"通用 REST catalog 属性"文档。此前 catalog-properties.md 中只有 REST Catalog 的认证属性（Basic、OAuth2、SigV4、Google），但缺少对 REST Catalog 客户端行为属性的说明，例如快照加载模式、指标上报、分页大小、命名空间分隔符、扫描计划模式、表缓存等。这些属性对用户配置和调优 REST Catalog 行为很重要，但此前未在文档中记录。

此外，本次提交还对文档结构进行了调整，将原有的"REST Catalog auth properties"章节降级为"Auth properties"子节，置于新增的"REST catalog properties"章节之下，使文档层次更合理。

## 如何达成设计目的

在 `catalog-properties.md` 中新增 "REST catalog properties" 主章节，包含两个属性表（通用 REST 属性、表缓存属性），并将原有认证属性章节重组为该主章节下的 "Auth properties" 子节（相应降低标题层级）。

## 修改详情

### `docs/docs/catalog-properties.md` (+26/-4 lines)

**修改目的**：新增通用 REST catalog 属性文档，并重组认证属性章节层级。

**工作逻辑**：
1. 将原 "## REST Catalog auth properties" 改为 "## REST catalog properties"，并在其下新增通用属性表，包含：
   - `snapshot-loading-mode`（默认 `ALL`）：控制快照加载方式，支持 `ALL`（加载所有快照）和 `REFS`（仅加载引用的快照）。
   - `rest-metrics-reporting-enabled`（默认 `true`）：是否向 REST 服务器上报指标。
   - `view-endpoints-supported`（默认 `false`）：用于向后兼容旧 REST 服务器，当服务器支持 view 端点但未在 ConfigResponse 中发送 `endpoints` 字段时设为 `true`。
   - `rest-page-size`（默认 null）：列出命名空间、表等分页资源时的页大小。
   - `namespace-separator`（默认 `%1F`）：与 REST 服务器通信时命名空间层级的分隔符。
   - `scan-planning-mode`（默认 `CLIENT`）：控制扫描计划执行位置，支持 `CLIENT`（客户端计划）和 `SERVER`（服务端计划），可被服务器在 LoadTableResponse 中按表覆盖。
2. 新增 "### Table cache properties" 子节，包含：
   - `rest-table-cache.expire-after-write-ms`（默认 300000 即 5 分钟）：缓存表条目的过期时间。
   - `rest-table-cache.max-entries`（默认 100）：缓存表条目的最大数量。
   - 并说明此缓存与 catalog 级别的通用缓存不同。
3. 将原认证相关章节标题降级一级："### REST auth properties"、"### OAuth2 auth properties"、"### Google auth properties" 改为 "####"，并新增 "### Auth properties" 子节作为它们的父级。

## 总结

这是一个纯文档提交，补全了 REST Catalog 通用客户端属性的文档记录，涵盖快照加载、指标上报、分页、扫描计划、表缓存等重要配置。同时通过重组章节层级，使 REST catalog 属性（通用 + 缓存 + 认证）形成更清晰的文档结构。这对用户正确配置和调优 REST Catalog 行为很有帮助。
