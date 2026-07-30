# 提交 2882：Flink: Backport add TableCreator interface to set table properties/location to 2.0 and 1.20 (#14607)

## 提交信息

- **序号**：2882 / 4088
- **哈希**：87f0563ff514d3dbec36d7770d478b8aefc4afd1
- **短哈希**：87f0563ff
- **日期**：2025-11-17 21:37:29 +0100
- **作者**：Jordan Epstein
- **提交说明**：Flink: Backport add TableCreator interface to set table properties/location to 2.0 and 1.20 (#14607)
- **PR/Issue**：#14607

## 总体目的

此提交是提交 2879（PR #14578）的 backport，将 `TableCreator` 接口功能从 Flink v2.1 分支移植到 Flink v2.0 和 v1.20 分支。

提交 2879 在 Flink v2.1 中为 DynamicIcebergSink 引入了 `TableCreator` 函数式接口，允许用户在动态创建 Iceberg 表时自定义表属性和存储位置。由于 Iceberg 需要同时支持多个 Flink 版本（v1.20、v2.0、v2.1），同一功能需要在所有维护中的版本中保持一致。此 backport 确保使用 Flink v2.0 和 v1.20 的用户也能使用 `tableCreator()` 配置。

## 如何达成设计目的

将 v2.1 中的全部修改完整复制到 v2.0 和 v1.20 两个版本分支，包括：
1. 新建 `TableCreator` 接口（两个版本各一份）。
2. 修改 `DynamicIcebergSink.Builder` 增加 `tableCreator` 配置。
3. 修改 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator` 传递 `TableCreator`。
4. 修改 `TableUpdater` 使用 `TableCreator` 替代直接 `catalog.createTable`。
5. 更新所有相关测试。

## 修改详情

### Flink v2.0 分支（8 个文件）

与 2879 中 v2.1 的修改完全一致：
- `DynamicIcebergSink.java` (+12/-1)：Builder 新增 `tableCreator` 字段和 setter。
- `DynamicRecordProcessor.java` (+6/-1)：传递 TableCreator。
- `DynamicTableUpdateOperator.java` (+8/-2)：传递 TableCreator。
- `TableCreator.java` (+34/-0, 新文件)：函数式接口定义。
- `TableUpdater.java` (+10/-4)：使用 TableCreator。
- `TestDynamicTableUpdateOperator.java` (+4/-2)：传入 DEFAULT。
- `TestTableMetadataCache.java` (+2/-1)：传入 DEFAULT。
- `TestTableUpdater.java` (+41/-12)：新增自定义 TableCreator 测试。

### Flink v1.20 分支（8 个文件）

与 v2.0 的修改完全一致，同样的 8 个文件做相同修改。

## 总结

该提交是 2879（TableCreator 接口）向 Flink v2.0 和 v1.20 的 backport，确保三个 Flink 版本（v1.20、v2.0、v2.1）都支持通过 `tableCreator()` 自定义动态表创建时的属性和位置。两个版本的修改内容与原始 v2.1 实现完全一致，保证了跨版本的功能一致性。
