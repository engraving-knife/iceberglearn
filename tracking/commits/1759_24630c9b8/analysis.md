# 提交 1759：Docs: Fix link of catalog in terms.md (#12326)

## 提交信息

- **序号**：1759 / 4088
- **哈希**：24630c9b8c71c719ed30358c9b23a60f46e04c0a
- **短哈希**：24630c9b8
- **日期**：2025-02-19 11:26:52 -0800
- **作者**：wangyinsheng
- **提交说明**：Docs: Fix link of catalog in terms.md (#12326)
- **PR/Issue**：#12326

## 总体目的

本提交旨在修复 `site/docs/terms.md` 文档中一个指向 catalog 配置属性的链接路径错误。

文档中有一个指向 "Iceberg documentation" 的链接，指向 catalog properties 配置说明页面。原链接使用了相对路径 `../docs/latest/configuration.md#catalog-properties`，其中 `../` 前缀导致路径多了一层上级目录引用，使链接指向了错误的位置。修复后改为 `docs/latest/configuration.md#catalog-properties`，去掉了多余的 `../` 前缀，使链接正确指向配置文档中的 catalog-properties 锚点。

## 如何达成设计目的

提交直接修改 Markdown 文件中的链接路径，将 `../docs/latest/configuration.md#catalog-properties` 改为 `docs/latest/configuration.md#catalog-properties`，修正相对路径引用。

## 修改详情

### `site/docs/terms.md`（修改, +1/-1 lines）

**修改目的**：修复 catalog 配置文档链接的路径错误。

**工作逻辑**：在描述 catalog 配置的段落中，有一个指向 Iceberg 配置文档的 Markdown 链接 `[Iceberg documentation](../docs/latest/configuration.md#catalog-properties)`。由于 `terms.md` 位于 `site/docs/` 目录下，而 `configuration.md` 位于 `docs/latest/` 目录下，使用 `../docs/` 路径会先回到 `site/` 的上级目录再进入 `docs/`，但实际上应该从 `site/docs/` 直接进入 `docs/latest/`。修复后的路径 `docs/latest/configuration.md#catalog-properties` 正确指向了目标文档的 catalog-properties 锚点。

## 小结

- **成效**：修复了 terms.md 中指向 catalog 配置文档的链接路径，使链接可以正确跳转到目标页面。
- **影响范围**：仅涉及文档文件 `site/docs/terms.md` 的一处链接修正，不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无代码依赖，可安全回迁。需确认 1.4.x 分支的 `site/docs/terms.md` 中存在相同的链接路径问题。如果 1.4.x 分支的文档目录结构不同，可能需要调整链接路径。
