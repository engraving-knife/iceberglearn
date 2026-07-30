# 提交 2805：Core: Fix failure when not finding a column during time travel (#14438)

## 提交信息

- **序号**：2805 / 4088
- **哈希**：92ae3d9da2afba3fa6fa0f74af6d78d773a6ab26
- **短哈希**：92ae3d9da
- **日期**：2025-10-29 23:39:23 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Fix failure when not finding a column during time travel (#14438)
- **PR/Issue**：#14438

## 总体目的

本提交修复了提交 2795（#13301）引入的一个问题：当时间旅行扫描时，使用历史快照 schema 绑定分区规范，如果当前 schema 新增了列（该列在历史快照中不存在），分区规范绑定会失败。

在 2795 中，`SnapshotScan.specs()` 方法在时间旅行时使用历史快照的 schema 重新绑定分区规范，调用了 `toUnbound().bind(snapshotSchema)`。`bind` 方法默认严格模式，要求分区规范引用的所有字段都在 schema 中存在。然而，当表在历史快照之后新增了分区列时，当前表的分区规范可能引用了新列，而历史快照的 schema 中没有该列，导致绑定失败。

这是一个紧跟 2795 的修复。2795 解决了"用当前 schema 绑定历史分区规范"的问题，但引入了"用历史 schema 绑定包含新列的分区规范"的问题。本提交通过使用宽松绑定模式（`bind(snapshotSchema, true)`）来解决：允许分区规范中引用的字段在 schema 中不存在时跳过，而非抛出异常。

## 如何达成设计目的

将 `SnapshotScan.specs()` 方法中的 `bind(snapshotSchema)` 改为 `bind(snapshotSchema, true)`，启用宽松绑定模式。第二个参数 `true` 表示当分区规范引用的字段在 schema 中不存在时，不抛出异常而是跳过该分区字段。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotScan.java` (+1/-1 lines)

**修改目的**：修改分区规范绑定模式为宽松模式。

**工作逻辑**：将 `entry.getValue().toUnbound().bind(snapshotSchema)` 改为 `entry.getValue().toUnbound().bind(snapshotSchema, true)`。`bind` 方法的第二个布尔参数控制是否严格检查字段存在性：`true` 表示宽松模式，允许分区规范引用的源字段在 schema 中不存在（跳过），`false`（默认）表示严格模式，找不到字段时抛出异常。这样，当当前 schema 新增了分区列（如 "hour"），而历史快照 schema 中没有该列时，绑定不会失败。

### `core/src/test/java/org/apache/iceberg/TestScansAndSchemaEvolution.java` (+70/-0 lines)

**修改目的**：添加新增列场景的时间旅行测试。

**工作逻辑**：
- **`testPartitionSourceAdd`**：创建表并提交数据后记录快照 ID，然后新增分区列 "hour" 并将其添加到分区规范。先用新列过滤当前快照（验证新列可用），再时间旅行到旧快照用旧列名 "part" 过滤（验证旧快照仍可正常扫描）。这覆盖了"新增分区列后时间旅行"的场景。
- **`testColumnAdd`**：创建表并提交数据后记录快照 ID，然后新增普通列 "hour"（非分区列）。先用新列过滤当前快照，再时间旅行到旧快照用旧列 "data" 过滤。这覆盖了"新增普通列后时间旅行"的场景。

## 总结

本提交修复了 2795 引入的一个回归问题：时间旅行扫描时，如果当前表的分区规范引用了历史快照中不存在的列（新增列），严格绑定模式会失败。修复方案是将绑定模式改为宽松模式（`bind(snapshotSchema, true)`），允许跳过不存在的字段。新增了两个测试覆盖新增分区列和新增普通列后的时间旅行场景。
