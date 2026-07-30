# 提交 0552：修复 OpenAPI 规范中指向 catalog properties 的 URL

## 提交信息

- **序号**：0552 / 4088
- **哈希**：50af8c070321ce187b0c8654e0bdcd49251f1631
- **短哈希**：50af8c070
- **日期**：2024-02-28（Wed Feb 28 22:18:48 2024 +0530）
- **作者**：Alok Thatikunta <athatikunta@confluent.io>
- **提交说明**：OpenAPI: Fix URL pointing to catalog properties (#9825)
- **PR/Issue**：#9825

## 总体目的

Iceberg 官网在改版后，文档的 URL 结构发生了变化。旧版的页面地址 `https://iceberg.apache.org/configuration/#catalog-properties` 已不再可用（页面位置变更），访问者会找不到关于 catalog 配置属性的文档。OpenAPI 规范文件 `rest-catalog-open-api.yaml` 在 `GET /v1/config` 端点的描述中引用了该链接，导致生成的 API 文档和阅读规范的开发者都被引导到一个无效或不再更新的页面。

本提交的目的是把该 URL 更新为新的有效地址，确保 API 文档使用者能够通过该链接正确跳转到当前官网的 catalog properties 文档。

## 如何达成设计目的

整体设计非常简单：仅替换一个 URL 字符串。

实现路径：
1. 找到 `open-api/rest-catalog-open-api.yaml` 中描述 `GET /v1/config` 端点的位置。
2. 把 "Common catalog configuration settings are documented at" 后面的链接从 `https://iceberg.apache.org/configuration/#catalog-properties` 改为 `https://iceberg.apache.org/docs/latest/configuration/#catalog-properties`。
3. 该改动只涉及一行字符串，不修改任何 schema、response、parameter 等 OpenAPI 结构性内容。

这种纯 URL 修复既能保证文档可用性，又不会影响任何 API 行为或客户端生成代码。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：修正 `GET /v1/config` 端点描述中失效的文档链接，使其指向当前官网最新的 catalog properties 文档页面。

**工作逻辑**：`GET /v1/config` 是 REST Catalog API 中用于获取服务端配置的端点。其 `description` 字段告诉客户端哪些配置项可以通过该端点获取，并附带一个指向官网"Catalog Properties"章节的外链，供开发者了解常见 catalog 配置项（如 warehouse、s3.access-key-id 等）的语义。

新 URL 在路径中增加了 `/docs/latest/`，对应官网文档站点改版后的"最新版文档"路由约定；锚点 `#catalog-properties` 保持不变，依然指向同名的章节。修复后链接可达、定位准确。

## 小结

这是一次单行文档链接修复，对 API 行为、客户端代码生成、协议兼容性均无任何影响，仅改善文档可用性。

**回迁到 1.4.x 的注意事项**：
- 改动极其安全，无任何运行时风险。
- 回迁前应确认 1.4.x 分支的 `open-api/rest-catalog-open-api.yaml` 中仍存在该链接（链接位于 `GET /v1/config` 端点的描述中）。
- 若 1.4.x 中此处的 URL 与 main 分支改前不同（例如已经是 `/docs/1.4.x/...`），可酌情保留 1.4.x 风格的版本化路径，不必强制与 main 一致；但若 1.4.x 仍使用旧版失效链接，则应直接回迁本修复。
- 由于仅修改一行字符串，不会产生合并冲突。
