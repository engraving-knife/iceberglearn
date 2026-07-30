# 提交 3092：Kafka Connect: fix table UUID check (#15011)

## 提交信息

- **序号**：3092 / 4088
- **哈希**：b2696b9599df762feb3749eef2383b7698437374
- **短哈希**：b2696b959
- **日期**：2026-01-09
- **作者**：Bryan Keller
- **提交说明**：Kafka Connect: fix table UUID check (#15011)
- **PR/Issue**：#15011

## 总体目的

该提交修复了前一个提交（#14979，序号 3091）引入的表 UUID 校验逻辑中的两个缺陷。#14979 在 `Coordinator.commitToTable` 方法中新增了 UUID 比对逻辑，当 `table.uuid()` 与 `tableReference.uuid()` 不匹配时记录警告日志，但存在两个问题：

第一，校验失败后没有 `return` 语句。虽然代码记录了 "Skipping commits to table" 的警告日志，但实际并没有跳过提交——程序会继续执行后续的 commit 逻辑，将数据文件提交到 UUID 不匹配的表中。这使得 UUID 校验形同虚设，仅产生日志而不具备实际的防护效果。

第二，`tableReference.uuid()` 可能为 null。在 #14979 中，`TableReference` 的旧工厂方法 `of(String catalog, TableIdentifier)`（标记 `@Deprecated`）和旧构造函数会将 uuid 设为 null。此外，从旧版本序列化的事件中反序列化出的 `TableReference` 也会因 Avro schema 中 `table_uuid` 为 optional 而得到 null UUID。使用 `Objects.equals(table.uuid(), tableReference.uuid())` 时，如果 `tableReference.uuid()` 为 null 而 `table.uuid()` 非 null（正常情况下表都有 UUID），`Objects.equals` 会返回 false，导致正常的不携带 UUID 的事件被误判为 UUID 不匹配而跳过提交。这会破坏与旧版本 Worker 的向后兼容性——旧版本 Worker 发出的事件不包含 UUID，新版本 Coordinator 不应拒绝这些事件。

本提交通过两项修改解决上述问题：在 UUID 不匹配的 if 块中添加 `return` 语句使校验真正生效；在比较前增加 `tableReference.uuid() != null` 的前置条件，仅当 TableReference 携带了非 null UUID 时才执行比对，兼容不携带 UUID 的旧事件。

## 如何达成设计目的

修改 `Coordinator.commitToTable` 方法中的 UUID 校验条件，增加 null 检查避免误判，并添加 `return` 使跳过逻辑真正生效。同时修改测试用例，将测试中的 TableReference UUID 从随机值改为 null，验证 null UUID 场景下提交不会被跳过。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+2/-1 lines)

**修改目的**：修复 UUID 校验逻辑，使其真正跳过不匹配的提交并兼容 null UUID。

**工作逻辑**：
原条件 `if (!Objects.equals(table.uuid(), tableReference.uuid()))` 修改为 `if (tableReference.uuid() != null && !tableReference.uuid().equals(table.uuid()))`。第一处变更 `tableReference.uuid() != null &&` 是前置 null 检查：仅当 TableReference 携带了非 null 的 UUID 时才执行比对，当 UUID 为 null（来自旧版本事件或旧工厂方法）时跳过校验，允许提交继续，保证向后兼容。第二处变更是将 `Objects.equals(table.uuid(), tableReference.uuid())` 简化为 `tableReference.uuid().equals(table.uuid())`，由于此时 `tableReference.uuid()` 已确认非 null，调用其 `equals` 方法是安全的。第三处变更是在该 if 块末尾（日志语句之后）添加 `return;` 语句，使校验失败时真正跳过后续的提交逻辑，而非仅记录日志后继续执行。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+1/-1 lines)

**修改目的**：调整测试以验证 null UUID 的兼容场景。

**工作逻辑**：
将构造 `DataWritten` 事件时 `TableReference.of("catalog", TableIdentifier.of("db", "tbl"), UUID.randomUUID())` 改为 `TableReference.of("catalog", TableIdentifier.of("db", "tbl"), null)`，即传入 null UUID。这验证了当 TableReference 不携带 UUID（模拟旧版本 Worker 发出的事件或使用 deprecated 工厂方法的场景）时，Coordinator 不会因 UUID 不匹配而跳过提交，确保向后兼容性。

## 总结

该提交是对 #14979（序号 3091）的快速修复，解决了 UUID 校验逻辑的两个关键缺陷：缺少 `return` 导致校验无效，以及 null UUID 处理不当导致向后兼容性破坏。修复后，UUID 校验仅在 TableReference 携带非 null UUID 时生效，且不匹配时真正跳过提交，同时兼容不携带 UUID 的旧版本事件。这是一个紧跟主功能提交的 bug 修复，确保了表替换检测机制的实际可用性。
