# 提交 2334：API, Flink: fix typos in javadoc (#13503)

## 提交信息

- **序号**：2334 / 4088
- **哈希**：c71e3eb4d0b4674758d1ed3c196fac9aed3c9ac2
- **短哈希**：c71e3eb4d
- **日期**：2025-07-09 13:23:03 -0700
- **作者**：Laurent Goujon
- **提交说明**：API, Flink: fix typos in javadoc (#13503)
- **PR/Issue**：#13503

## 总体目的

本提交修复了多个 Javadoc 注释中的拼写错误。具体来说，在三处 Javadoc 注释中，注释开头多了一个 `/**` 字符串，导致生成的 Javadoc 文档中出现格式错误。

Javadoc 注释以 `/**` 开头、`*/` 结尾。如果在注释内容的第一行又出现 `/**`，Javadoc 工具会将其作为普通文本处理，导致生成的 API 文档中出现多余的 `/**` 字符串，影响文档的可读性和专业性。

## 如何达成设计目的

直接删除 Javadoc 注释内容中多余的 `/**` 前缀，保留正确的注释内容。涉及三个文件：API 模块的 `Catalog.java` 和 Flink 模块的两个版本（v1.20 和 v2.0）的 `FlinkSource.java`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/Catalog.java` (+1/-1 lines)

**修改目的**：移除 `buildTable` 方法 Javadoc 中多余的 `/**`。

**工作逻辑**：将 `* /** Instantiate a builder...` 改为 `* Instantiate a builder...`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSource.java` (+1/-1 lines)

**修改目的**：移除类级 Javadoc 中多余的 `/**`。

**工作逻辑**：将 `* /** Flink source builder...` 改为 `* Flink source builder...`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSource.java` (+1/-1 lines)

**修改目的**：同 v1.20，保持版本间一致。

**工作逻辑**：与 v1.20 完全相同的修改。

## 总结

本提交是纯文档修复，移除了三处 Javadoc 注释中多余的 `/**` 字符串，确保生成的 API 文档格式正确、专业。修改涉及 API 模块和 Flink 模块的两个版本（v1.20、v2.0）。
