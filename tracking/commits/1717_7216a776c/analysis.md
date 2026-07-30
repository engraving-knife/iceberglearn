# 提交 1717：Docs: Site update for 1.8.0 release (#12242)

## 提交信息

- **序号**：1717 / 4088
- **哈希**：7216a776caa6daa0b06cc68dd54dfa8fe5278163
- **短哈希**：7216a776c
- **日期**：2025-02-13 13:42:51 +0530
- **作者**：Amogh Jahagirdar
- **提交说明**：Docs: Site update for 1.8.0 release (#12242)
- **PR/Issue**：#12242

## 总体目的

更新 Iceberg 官方网站文档以反映 1.8.0 版本的发布。Apache Iceberg 1.8.0 于 2025 年 2 月 13 日发布，此提交将网站内容更新为 1.8.0 版本，包括：添加 1.8.0 发版说明、更新网站显示的版本号、在导航中添加 1.8.0 文档入口。

发版说明涵盖了 1.8.0 版本包含的所有重要变更，包括规范变更（删除向量、Variant 类型、行血统等）、各模块（Core、Spark、Flink、Hive、AWS、Azure 等）的新功能和修复，以及依赖升级信息。

## 如何达成设计目的

通过修改三个网站配置/文档文件来完成 1.8.0 发布的网站更新：

1. 在 `releases.md` 中添加 1.8.0 的完整发版说明，并将之前的版本归入 "Past releases" 部分。
2. 在 `mkdocs.yml` 中更新 `icebergVersion` 变量从 `1.7.1` 改为 `1.8.0`。
3. 在 `nav.yml` 中添加 1.8.0 文档的导航入口。

## 修改详情

### `site/docs/releases.md`（修改, +73/-2 lines）

**修改目的**：添加 1.8.0 版本发版说明。

**工作逻辑**：

1. 在文件中新增 "1.8.0 release" 小节（73 行），列出该版本的所有变更，按模块分类：
   - **Deprecation/End of Support**：Spark 3.3 弃用、移除 Hive Runtime。
   - **Spec**：删除向量、Variant 类型、行血统、快照新增 added-rows 字段等。
   - **API**：Variant 数据类型定义、UnknownType。
   - **Core**：删除向量读写、表工具 API 增强、分区规范过期清理、Variant 编码实现、REST 认证管理重构等。
   - **Parquet/Avro**：默认值支持、内部对象模型写入器。
   - **AWS/Azure**：KMS 重试模式、WASB 协议支持。
   - **Spark**：RewriteTablePath 过程、Comet 向量化读取、ComputeTableStats 过程、删除向量写入、View 支持等。
   - **Kafka Connect**：控制消费者组前缀配置。
   - **Flink**：快照过期、Parquet 默认值读取、NPE 修复。
   - **Hive**：元数据文件删除修复、tableExists 优化。
   - **Dependencies**：AWS SDK 2.30.11、Netty 4.1.117、Kafka 3.9.0、Nessie 0.102.2、ORC 1.9.5、Jackson 2.18.2 等。

2. 在 1.8.0 和 1.7.1 之间插入 "## Past releases" 标题，将旧版本归入历史发布部分。

### `site/mkdocs.yml`（修改, +1/-1 line）

**修改目的**：更新网站显示的 Iceberg 版本号。

**工作逻辑**：将 `extra` 部分的 `icebergVersion` 从 `'1.7.1'` 改为 `'1.8.0'`，使网站各处引用此变量的地方显示最新版本号。

### `site/nav.yml`（修改, +1 line）

**修改目的**：在网站导航中添加 1.8.0 文档入口。

**工作逻辑**：在 Docs 导航部分添加 `1.8.0: '!include docs/docs/1.8.0/mkdocs.yml'`，位于 latest 和 1.7.1 之间，使网站可以访问 1.8.0 版本的文档。

## 小结

- **成效**：网站文档更新为 1.8.0 版本，包含完整的发版说明和导航入口。
- **影响范围**：仅影响网站文档，不影响项目代码。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是 1.8.0 版本的网站发版说明，与 1.4.x 分支无关。1.4.x 分支有自己的版本发布周期和发版说明，不应包含 1.8.0 的内容。
