# 提交 3948：Flink: Improve error message for unsupported table kinds in createTable (#16079)

## 提交信息

- **序号**：3948 / 4088
- **哈希**：33bd1019a3544ad005979c64b73ee0a8da2d50e4
- **短哈希**：33bd1019a
- **日期**：2026-06-25 13:27:38 +0200
- **作者**：Robin Moffatt
- **提交说明**：Flink: Improve error message for unsupported table kinds in createTable (#16079)
- **PR/Issue**：#16079

## 总体目的

这次提交改进了 Flink Iceberg catalog 在 `createTable` 操作中遇到不支持的表类型时的错误消息。原本当用户尝试创建 Iceberg 不支持的表类型（如 Flink 的物化表 `CatalogMaterializedTable`）时，错误消息仅为简单的 "table should be resolved"，没有解释为什么失败、什么类型不被支持，以及用户应该做什么。

这种模糊的错误消息对用户体验不友好：用户可能不理解 "resolved" 的含义，不知道物化表不被支持，也不知道如何修正。改进后的错误消息明确指出了期望的类型（`ResolvedCatalogTable`）、实际收到的类型（通过反射获取类名），并说明 Iceberg Flink catalog 只支持 resolved catalog table，物化表和其他表类型不被支持。

## 如何达成设计目的

修改 `FlinkCatalog.createTable` 中的 `Preconditions.checkArgument` 调用，将简短的错误消息扩展为包含实际类型信息和解决指引的详细消息。同时添加测试验证物化表创建时会抛出包含说明信息的 `IllegalArgumentException`。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+5/-1 line)

**修改目的**：改进 createTable 中不支持的表类型的错误消息。

**工作逻辑**：
将：
```java
Preconditions.checkArgument(table instanceof ResolvedCatalogTable, "table should be resolved");
```
改为：
```java
Preconditions.checkArgument(
    table instanceof ResolvedCatalogTable,
    "Expected a ResolvedCatalogTable but got: %s. "
        + "Iceberg Flink catalog only supports resolved catalog tables "
        + "(Materialized tables and other table kinds are not supported).",
    table == null ? "null" : table.getClass().getName());
```
使用 `%s` 占位符插入实际类型名（处理 null 情况），消息明确说明期望类型、实际类型和支持范围。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+27/-0 lines)

**修改目的**：验证物化表创建时抛出有意义的错误。

**工作逻辑**：
新增 `testCreateMaterializedTableIsUnsupported` 测试，构造一个 `CatalogMaterializedTable`（包含 schema、definition query、freshness、refresh mode 等属性），调用 `createTable` 并断言抛出 `IllegalArgumentException`，消息包含 "Materialized tables and other table kinds are not supported"。

## 总结

这次提交改进了 Flink Iceberg catalog 在遇到不支持的表类型时的错误消息，从模糊的 "table should be resolved" 改为包含实际类型名、期望类型和支持范围说明的详细消息。这显著提升了用户体验，帮助用户快速理解问题原因并采取正确的操作。该改进随后在 #16959 中被 backport 到 Flink 1.20 和 2.0。
