# 提交 2078：Docs: Incorrect property in CREATE CATALOG for Flink

## 提交信息

- **序号**：2078 / 4088
- **哈希**：d2806b4d8831238c79e35e76e0b76e046436d42a
- **短哈希**：d2806b4d8
- **日期**：2025-05-05 15:39:49 +0200
- **作者**：Subhash
- **提交说明**：Docs: Incorrect property in CREATE CATALOG for Flink (#12894)
- **PR/Issue**：#12894

## 总体目的

Iceberg 的 AWS 集成文档 `docs/docs/aws.md` 中展示了如何在 Flink 中创建使用 Glue 作为目录服务的 Iceberg Catalog。文档中存在三处属性名错误：

1. 在 Flink SQL `CREATE CATALOG` 示例中，使用了 `'type'='glue'`，但 `type` 属性已经被设置为 `'iceberg'`（catalog 类型），此处应该是 `'catalog-type'='glue'`（目录实现类型）。
2. 在 Flink YAML 配置示例中，使用了 `catalog-impl: org.apache.iceberg.aws.glue.GlueCatalog`，但此处应与 SQL 示例保持一致，使用 `catalog-type: glue`。
3. 在 Glue Catalog 说明段落中，文字描述提到"setting `type` as `glue`"，应改为"setting `catalog-type` as `glue`"。

这些错误会误导用户使用错误的属性名，导致 Catalog 创建失败。本提交修正这三处错误，将 `type`/`catalog-impl` 统一改为正确的 `catalog-type`。

## 如何达成设计目的

直接修改文档中的三处属性名错误，将 SQL 示例、YAML 示例和文字描述中的错误属性名替换为正确的 `catalog-type`。

## 修改详情

### `docs/docs/aws.md` (修改, +3/-3 lines)

**修改目的**：修正 AWS 集成文档中 Flink 创建 Glue Catalog 的属性名错误。

**工作逻辑**：
- 第一处：Flink SQL 示例中，`'type'='glue'` → `'catalog-type'='glue'`。原示例中 `'type'` 出现两次（一次为 `'iceberg'`，一次为 `'glue'`），后者应为 `catalog-type`。
- 第二处：Flink YAML 配置示例中，`catalog-impl: org.apache.iceberg.aws.glue.GlueCatalog` → `catalog-type: glue`。与 SQL 示例保持一致，使用简化的 catalog-type 配置。
- 第三处：Glue Catalog 说明文字中，`setting type as glue` → `setting catalog-type as glue`。

## 总结

本提交修正 AWS 集成文档中 Flink 创建 Glue Catalog 示例的三处属性名错误：将错误的 `type`/`catalog-impl` 替换为正确的 `catalog-type`，涉及 SQL 示例、YAML 配置示例和文字说明，避免用户因属性名错误导致 Catalog 创建失败。
