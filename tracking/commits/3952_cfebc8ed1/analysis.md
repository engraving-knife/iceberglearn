# 提交 3952：Flink: Backport improved createTable error message to 1.20 and 2.0 (#16959)

## 提交信息

- **序号**：3952 / 4088
- **哈希**：cfebc8ed16fcdee7a28ad92635adbb744b0258bc
- **短哈希**：cfebc8ed1
- **日期**：2026-06-25 13:46:20 -0700
- **作者**：Robin Moffatt
- **提交说明**：Flink: Backport improved createTable error message to 1.20 and 2.0 (#16959)
- **PR/Issue**：#16959（backport #16079，即提交 3948）

## 总体目的

这次提交是 #16079（提交 3948）的 backport，将 Flink `FlinkCatalog.createTable` 中不支持的表类型错误消息改进同步到 Flink 1.20 和 2.0 分支。

原 bug：当用户尝试创建 Iceberg 不支持的表类型（如 `CatalogMaterializedTable`）时，错误消息仅为 "table should be resolved"，不提供实际类型信息或支持范围说明。

改进后：错误消息明确指出期望的 `ResolvedCatalogTable` 类型、实际收到的类型（通过反射获取类名），并说明 Iceberg Flink catalog 只支持 resolved catalog table，物化表和其他表类型不被支持。

## 如何达成设计目的

与 #16079 相同的修改方式，将 `FlinkCatalog.java` 中的 `Preconditions.checkArgument` 错误消息改进应用到 Flink 1.20 和 2.0，并添加对应的物化表拒绝测试。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+5/-1 line)

**修改目的**：改进 Flink 1.20 的 createTable 错误消息。

**工作逻辑**：将 "table should be resolved" 扩展为包含实际类型名和支持范围说明的消息。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+27/-0 lines)

**修改目的**：验证 Flink 1.20 物化表创建被拒绝。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+5/-1 line)

**修改目的**：改进 Flink 2.0 的 createTable 错误消息。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+27/-0 lines)

**修改目的**：验证 Flink 2.0 物化表创建被拒绝。

## 总结

这次提交将 #16079 的 createTable 错误消息改进 backport 到 Flink 1.20 和 2.0，确保所有受支持的 Flink 版本在遇到不支持的表类型时都能提供清晰、有帮助的错误消息。
