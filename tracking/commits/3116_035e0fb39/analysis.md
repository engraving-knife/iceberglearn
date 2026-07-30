# 提交 3116：REST Spec: clarify uniqueness of ETags for table metadata responses (#15045)

## 提交信息

- **序号**：3116 / 4088
- **哈希**：035e0fb39d2a949f6343552ade0a7d6c2967e0db
- **短哈希**：035e0fb39
- **日期**：2026-01-15 11:44:25 -0800
- **作者**：Daniel Weeks
- **提交说明**：REST Spec: clarify uniqueness of ETags for table metadata responses (#15045)
- **PR/Issue**：#15045

## 总体目的

Iceberg 的 REST Catalog 规范通过 `ETag` 头来标识表元数据的一个唯一版本，主要用于乐观并发控制（commit 时的冲突检测）。然而规范中对 ETag 唯一性的描述过于宽泛——原先只有一句话 "Identifies a unique version of the table metadata."，这在实际使用中产生了歧义。

问题的核心在于：当客户端通过 `snapshots` 查询参数（取值为 `refs` 或 `all`）请求加载表元数据时，服务端返回的元数据内容可能不同，但它们代表的是同一个版本的表元数据。如果两个不同查询参数返回了不同的内容，那么它们是否应该共享同一个 ETag？旧描述无法明确回答。

本提交通过扩充 ETag 头的描述来澄清这一点：当响应返回不同的元数据内容时，实现应当为它们生成不同的 ETag，即使它们代表同一版本的表元数据。这样客户端在缓存或做条件请求时能够正确区分因查询参数差异而产生的不同表示。这是一次纯规范文档的完善，目的是消除实现方和客户端对 ETag 唯一性语义的误解，避免出现缓存命中错误或冲突检测失准的情况。

## 如何达成设计目的

直接在 OpenAPI 规范文件 `open-api/rest-catalog-open-api.yaml` 中扩充 `etag` 请求头参数的 `description` 字段，增加一段说明文字，明确指出支持 ETag 的实现应为返回不同元数据内容（如因 `snapshots` 查询参数 `refs`/`all` 不同）的响应生成不同的 ETag。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+7/-1 lines)

**修改目的**：澄清 REST Catalog 规范中 ETag 头的唯一性语义。

**工作逻辑**：
该文件是 REST Catalog 的 OpenAPI 规范定义。改动位于 `components/parameters/etag` 中 `etag` 头参数的 `description`。原描述只有一句 `Identifies a unique version of the table metadata.`，现在在其后追加一段：

> Implementations that support ETags should produce unique tags for responses that return different metadata content but represent the same version of table metadata. For example, the `snapshots` query parameter may result in different metadata representations depending on whether `refs` or `all` is provided, therefore should have distinct ETags.

这段说明把 ETag 的唯一性粒度从"版本"细化到"内容表示"，并以 `snapshots` 查询参数为例给出具体场景：`refs` 与 `all` 会产生不同的元数据表示，因此应使用不同的 ETag。这对客户端的缓存行为和条件请求（如 `If-Match`/`If-None-Match`）有直接影响——同一版本但内容不同的响应不能用同一个 ETag，否则会导致错误地复用缓存。

## 总结

本次提交是 REST Catalog 规范文档的一次语义澄清，将 ETag 唯一性的定义从"表元数据版本"细化到"表元数据内容表示"，并明确以 `snapshots` 查询参数作为典型场景。这为各 REST Catalog 实现正确生成 ETag 提供了明确指引，有助于避免缓存与并发冲突检测中的歧义，提升了规范的精确性和可互操作性。
