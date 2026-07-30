# 提交 2115：Docs: Fix links to javadoc

## 提交信息

- **序号**：2115 / 4088
- **哈希**：6c29507008f3cdda206709dc2be16cb40c1c6173
- **短哈希**：6c2950700
- **日期**：2025-05-13 15:04:53 +0800
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix links to javadoc (#12880)
- **PR/Issue**：#12880

## 总体目的

本提交修复了 Iceberg 文档中指向 Java API 文档（javadoc）的链接格式。原来的链接使用了 `index.html?org/apache/iceberg/...` 的格式，包含 `index.html?` 前缀和查询参数形式，这种格式可能在新版 javadoc 生成工具或站点配置下无法正确跳转到目标类页面。修改后直接使用 `org/apache/iceberg/...` 的路径格式，更加简洁且兼容性更好。这属于文档维护类的小修复。

## 如何达成设计目的

1. 在 `docs/docs/api.md` 中修复了 4 处 javadoc 链接，分别指向 Table、PendingUpdate、org.apache.iceberg.types 包和 Expressions
2. 在 `docs/docs/hive.md` 中修复了 2 处 javadoc 链接，分别指向 Catalog 和 Tables
3. 在 `docs/docs/java-api-quickstart.md` 中修复了 2 处 javadoc 链接，分别指向 Catalog 和 Tables
4. 统一移除链接路径中的 `index.html?` 前缀，直接使用相对路径

## 修改详情

### `docs/docs/api.md` (修改, +4/-4 lines)

**修改目的**：修复指向 Table 接口、PendingUpdate、types 包和 Expressions 的 javadoc 链接。

**工作逻辑**：将 `../../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/Table.html` 格式的链接改为 `../../javadoc/{{ icebergVersion }}/org/apache/iceberg/Table.html`，移除了 `index.html?` 前缀。共修改 4 处链接。

### `docs/docs/hive.md` (修改, +1/-1 lines)

**修改目的**：修复指向 Catalog 和 Tables 接口的 javadoc 链接。

**工作逻辑**：同样移除 `index.html?` 前缀，修改了 1 处包含两个链接的行。

### `docs/docs/java-api-quickstart.md` (修改, +1/-1 lines)

**修改目的**：修复指向 Catalog 和 Tables 接口的 javadoc 链接。

**工作逻辑**：移除 `index.html?` 前缀，修改了 1 处包含两个链接的行。

## 总结

本提交是一个文档修复，将 javadoc 链接从带有 `index.html?` 查询参数的格式改为直接路径格式，提升了文档链接的兼容性和正确性。属于低风险的维护性改动。
