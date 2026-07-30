# 提交 0655：Core, Data, Flink: Migrate TableTestBase related classes to JUnit5

## 提交信息
- **序号**：0655 / 4088
- **哈希**：ced897ca699d4e91ac8da6ac26bec107ee64ee74
- **短哈希**：ced897ca6
- **日期**：2024-04-03（Wed Apr 3 19:28:29 2024 +0900）
- **作者**：Tom Tanaka
- **提交说明**：Core, Data, Flink: Migrate TableTestBase related classes to JUnit5 (#10080)
- **PR/Issue**：#10080

## 总体目的

本提交是 0653（#10063）的直接后续。0653 完成了"剩余 TableTestBase 子类"从 JUnit 4 到 JUnit 5 的结构性迁移（删除旧基类、切换注解、改用 `ParameterizedTestExtension`、移除构造器、切换临时目录机制），但在那一批文件中，许多断言仍保留为 JUnit 4 的 `org.junit.Assert.*` 与 `org.junit.Assume.*`，以及 AssertJ 的限定写法 `Assertions.assertThat(...)`/`Assertions.assertThatThrownBy(...)`。

本提交的目标是：对这批"与 TableTestBase 相关的"测试类做收尾清理，将其余的 JUnit 4 断言/假设全部替换为 AssertJ 流式断言（`assertThat`/`assertThatThrownBy`/`assumeThat`），并改用静态导入（`import static org.assertj.core.api.Assertions.*`）以提升可读性，从而让这些测试类在断言层面也彻底脱离 JUnit 4，完成 JUnit 5 + AssertJ 的统一风格。

简单说：0653 解决"结构迁移"，0655 解决"断言迁移"。两者共同把 `TableTestBase` 相关测试类完整迁移到 JUnit 5 + AssertJ 体系。

## 如何达成设计目的

整体策略是"机械式断言替换"，跨 core、data、flink（v1.16/v1.17/v1.18）共 16 个文件（其中 flink 三版本各有一组同名文件，实际逻辑文件约 6 个），统一应用如下替换模式：

1. **断言库切换**：
   - `org.junit.Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
   - `org.junit.Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`（消息从第一参数迁移到 `.as(...)` 链）
   - `org.junit.Assert.assertEquals(0, collection.size())` → `assertThat(collection).isEmpty()` 或 `.hasSize(n)`（更语义化）
   - `org.junit.Assert.assertTrue(x)` → `assertThat(x).isTrue()`
   - `org.junit.Assert.assertFalse(x)` → `assertThat(x).isFalse()`
   - `org.junit.Assert.assertNull(x)` → `assertThat(x).isNull()`

2. **假设库切换**：
   - `org.junit.Assume.assumeFalse("msg", cond)` → `assumeThat(x).as("msg").isFalse()`（或按需改写为 `isGreaterThan` 等）

3. **AssertJ 写法规范化**：
   - 限定调用 `Assertions.assertThat(...)` → 静态导入 `assertThat(...)`
   - 限定调用 `Assertions.assertThatThrownBy(...)` → 静态导入 `assertThatThrownBy(...)`
   - 删除不再需要的 `import org.assertj.core.api.Assertions;` 与 `import org.junit.Assert;`/`import org.junit.Assume;`

4. **集合断言语义化**：将 `assertEquals(0, list.size())` 改写为 `assertThat(list).isEmpty()`、`assertEquals(n, list.size())` 改为 `.hasSize(n)`、`assertEquals(expected, map.get(key))` 改为 `assertThat(map).containsEntry(key, expected)`，使断言意图更清晰、失败信息更友好。

5. **跨版本复制**：Flink v1.16/v1.17/v1.18 的同名测试文件应用完全一致的清理，保证各版本断言风格同步。

## 修改详情

### `core/src/test/java/org/apache/iceberg/util/TestTableScanUtil.java`
**修改目的**：将 AssertJ 限定写法改为静态导入。
**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThatThrownBy;`，删除 `import org.assertj.core.api.Assertions;`。方法体内 `Assertions.assertThat(...)` → `assertThat(...)`、`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。该类已用 JUnit 5 `@Test`，仅断言写法需规范化。

### `data/src/test/java/org/apache/iceberg/io/TestPartitioningWriters.java`
**修改目的**：将 JUnit 4 断言替换为 AssertJ，并规范化 AssertJ 写法。
**工作逻辑**：新增 `assertThatThrownBy` 静态导入，删除 `Assertions`/`Assert` import。典型替换：
- `Assert.assertEquals("Must be no data files", 0, writer.result().dataFiles().size())` → `assertThat(writer.result().dataFiles()).isEmpty()`
- `Assert.assertEquals("Must be 3 data files", 3, result.dataFiles().size())` → `assertThat(result.dataFiles()).hasSize(3)`
- `Assert.assertEquals("Records should match", toSet(expectedRows), actualRowSet("*"))` → `assertThat(actualRowSet("*")).isEqualTo(toSet(expectedRows))`
- `Assert.assertFalse(writer.result().referencesDataFiles())` → `assertThat(writer.result().referencesDataFiles()).isFalse()`
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`
消息字符串从第一参数迁移到 `.as(...)`（在需要保留消息处）或省略（在语义化集合断言已自解释处）。

### `data/src/test/java/org/apache/iceberg/io/TestPositionDeltaWriters.java` 与 `TestRollingFileWriters.java`
**修改目的**：同上，JUnit 4 断言替换为 AssertJ。
**工作逻辑**：应用与 `TestPartitioningWriters` 一致的替换模式（`Assert.*` → `assertThat`、集合断言语义化、静态导入规范化）。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/sink/TestDeltaTaskWriter.java`（三份）
**修改目的**：将 JUnit 4 断言替换为 AssertJ。
**工作逻辑**：`Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)` 等。三版本改动一致。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`（三份）
**修改目的**：将 JUnit 4 断言/假设替换为 AssertJ。
**工作逻辑**：新增 `import static org.assertj.core.api.Assumptions.assumeThat;`，删除 `Assert`/`Assume` import。典型替换：
- `Assert.assertTrue(tableDir.delete())` → `assertThat(tableDir.delete()).isTrue()`
- `Assert.assertEquals(className, summary.get("flink.test"))` → `assertThat(summary).containsEntry("flink.test", className)`
- `Assert.assertEquals(1, dataFiles.size())` → `assertThat(dataFiles).hasSize(1)`
- `Assert.assertEquals("Should have committed 2 txn.", 2, snapshots.size())` → `assertThat(table.snapshots()).hasSize(2)`
- `Assert.assertEquals(expectedId, actualId)` → `assertThat(actualId).isEqualTo(expectedId)`

**需特别注意的语义变化（潜在缺陷）**：在 `testDeleteFiles` 与 `testCommitTwoCheckpointsInSingleTxn` 中，原写法为：
```java
Assume.assumeFalse("Only support equality-delete in format v2.", formatVersion < 2);
```
其语义是"当 `formatVersion < 2` 时跳过测试"，即测试仅在 `formatVersion >= 2`（实际为 v2）时运行。本提交将其改为：
```java
assumeThat(formatVersion).as("Only support equality-delete in format v2 or later.").isGreaterThan(2);
```
其语义变为"当 `formatVersion <= 2` 时跳过测试"，即测试仅在 `formatVersion > 2`（v3+）时运行。结合 0653 中 `TestIcebergFilesCommitter` 的参数表只有 `formatVersion ∈ {1, 2}`，新写法会导致这两个测试在所有现有参数组合下都被跳过（1 与 2 均不大于 2）。这与原始意图（v2 应运行）不符，疑似应为 `isGreaterThanOrEqualTo(2)`。回迁时务必关注此处，建议核对上游后续是否已有修复。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`（三份）
**修改目的**：将 JUnit 4 断言替换为 AssertJ，规范化 AssertJ 写法。
**工作逻辑**：删除 `Assertions`/`Assert` import，新增 `assertThatThrownBy` 静态导入。`Assert.assertEquals("should produce 9 splits", 9, expectedSplits.length)` → `assertThat(expectedSplits).hasSize(9)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`；`Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).hasSize(expected)` 等。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingReaderOperator.java`（三份）
**修改目的**：新增 AssertJ 静态导入，将 JUnit 4 断言替换为 AssertJ。
**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`，删除 `Assert` import。`Assert.assertEquals("Should have 10 splits", 10, splits.size())` → `assertThat(splits).hasSize(10)`；`Assert.assertTrue("Should processed 1 split", processor.runMailboxStep())` → `assertThat(processor.runMailboxStep()).as("Should processed 1 split").isTrue()`。

## 小结
- **成效**：基本成功达成目的。将 0653 遗留的 JUnit 4 断言/假设全部替换为 AssertJ 流式写法，统一了 `TableTestBase` 相关测试类的断言风格，完成 JUnit 5 + AssertJ 迁移的最后一公里。
- **影响范围**：core、data、flink（v1.16/v1.17/v1.18）的测试断言。涉及 TestTableScanUtil、TestPartitioningWriters/TestPositionDeltaWriters/TestRollingFileWriters、TestDeltaTaskWriter、TestIcebergFilesCommitter、TestStreamingMonitorFunction/TestStreamingReaderOperator。仅影响测试代码，不影响生产代码。
- **回迁到 1.4.x 的注意事项**：
  1. **与 0653 配套**：本提交是 0653 的收尾，回迁时建议二者一并回迁，避免 1.4.x 上出现"结构已迁移但断言未迁移"的中间态。
  2. **潜在缺陷需复核**：`TestIcebergFilesCommitter` 中 `assumeFalse(formatVersion < 2)` 被改为 `isGreaterThan(2)`（而非 `isGreaterThanOrEqualTo(2)`），导致 `testDeleteFiles`/`testCommitTwoCheckpointsInSingleTxn` 在 formatVersion=2 时也被跳过。回迁时应核对上游是否已修复；若未修复，建议回迁时直接修正为 `isGreaterThanOrEqualTo(2)` 以恢复原语义，否则 v2 的 equality-delete 路径将失去测试覆盖。
  3. **断言消息位置**：JUnit 4 的 `assertEquals(msg, expected, actual)` 消息在第一参数，AssertJ 改为 `.as(msg)` 链式；回迁逐条替换时注意消息不要遗漏或错位。
  4. **多版本同步**：Flink 三版本的清理需同步应用，避免某版本遗留 `Assert.*` 导致编译警告或风格不一致。
