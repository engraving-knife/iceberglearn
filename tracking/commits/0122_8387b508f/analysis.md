# 提交 0122：Spark 3.4: Remove usage of AssertHelpers (#8963)

## 提交信息

- **序号**：0122 / 4088
- **哈希**：8387b508f96e9f38d45a982f75c262fdd8e2b621
- **短哈希**：8387b508f
- **日期**：2023-11-01
- **作者**：Ashok
- **提交说明**：Spark 3.4: Remove usage of AssertHelpers (#8963)
- **PR/Issue**：#8963

## 总体目的

Iceberg 早已将 `org.apache.iceberg.AssertHelpers` 标注为 `@Deprecated`，并推荐在测试代码中直接使用 AssertJ 的 `Assertions.assertThatThrownBy(...)` 流式断言。`AssertHelpers` 只是对 AssertJ 的薄封装（内部就是调 `Assertions.assertThatThrownBy(...).as(message).isInstanceOf(...).hasMessageContaining(...)`），保留它会让测试代码绕一道间接调用，也无法用上 AssertJ 更丰富的断言链（如 `hasMessageStartingWith`、`cause()` 等）。

本提交针对 Spark 3.4 模块（`spark/v3.4/spark-extensions`）下的扩展测试用例，把所有对 `AssertHelpers.assertThrows` / `AssertHelpers.assertThrowsCause` 的调用替换为直接的 `Assertions.assertThatThrownBy(...)` 链式断言，并移除对应的 `import org.apache.iceberg.AssertHelpers`、新增 `import org.assertj.core.api.Assertions`。这是 Iceberg 测试基础设施现代化迁移的一部分——之前的提交已经在 Spark 3.3 等其他模块做过同样的清理，本提交把 Spark 3.4 模块的剩余用法也清掉。

顺带地，由于部分测试断言的字符串是依赖 Spark 自身错误信息文案的，作者借此机会把若干错误消息断言更新到与当前 Spark 3.4 实际输出一致（例如把 `"a single row from the target table with multiple rows of the source table"` 改成完整 Spark 错误消息并改用 `hasMessageContaining`，把 `"Encountered records that belong to already closed files"` 改成 `"Incoming records violate the writer assumption ..."` 并配合 `.option(SparkWriteOptions.FANOUT_ENABLED, "false")` 触发新路径）。这些消息更新属于使迁移后的断言与 Spark 3.4 行为对齐，而非纯机械替换。

## 如何达成设计目的

改动只发生在 Spark 3.4 测试目录下，按文件分别完成机械替换与必要消息对齐：

1. 移除 `import org.apache.iceberg.AssertHelpers`，新增 `import org.assertj.core.api.Assertions`。
2. 把 `AssertHelpers.assertThrows(msg, Exc.class, containedMsg, () -> {...})` 改写为：
   ```java
   Assertions.assertThatThrownBy(() -> {...})
       .isInstanceOf(Exc.class)
       .hasMessageContaining(containedMsg);
   ```
3. 把 `AssertHelpers.assertThrowsCause(msg, Exc.class, containedMsg, () -> {...})` 改写为：
   ```java
   Assertions.assertThatThrownBy(() -> {...})
       .cause()
       .isInstanceOf(Exc.class)
       .hasMessageContaining(containedMsg);
   ```
4. 个别用例依据 Spark 3.4 当前实际行为调整了触发条件与断言文案（如 `TestRequiredDistributionAndOrdering` 中显式关闭 fanout writer；`TestMerge`/`TestUpdate`/`TestTagDDL` 中校对完整错误消息文案），保证迁移后测试仍然反映真实预期。

## 修改详情

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestChangelogTable.java`

**修改目的**：移除 `AssertHelpers` 用法，改用 AssertJ 原生断言。

**工作逻辑**：将 `assertThrows("Should fail if start time is after end time", IllegalArgumentException.class, () -> changelogRecords(...))` 替换为 `Assertions.assertThatThrownBy(() -> changelogRecords(...)).isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot set start-timestamp to be greater than end-timestamp for changelogs")`，同时把断言从"包含"升级为"等于"完整消息，更精确表达 changelog 起止时间倒置时的约束。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：本文件改动量最大（918 行变动），把 `TestMerge` 中大量的 `AssertHelpers.assertThrowsCause` 调用统一迁移到 `Assertions.assertThatThrownBy(...).cause().isInstanceOf(SparkException.class).hasMessageContaining(errorMsg)`，并校对多匹配错误消息文案。

**工作逻辑**：所有 MERGE 多匹配用例原本断言 `"a single row from the target table with multiple rows of the source table"`，迁移时把 `errorMsg` 升级为完整 Spark 文案 `"MERGE statement matched a single row from the target table with multiple rows of the source table."` 并继续用 `hasMessageContaining`。对于带 `withSQLConf(...)` 包裹的用例，把内部 `AssertHelpers.assertThrowsCause` 块改写为同等语义的 AssertJ 链；其余结构（assertEquals 验证 target 不变）保留。这样既完成了去 `AssertHelpers` 化，又保证了对 Spark 3.4 错误信息文案的准确断言。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRequiredDistributionAndOrdering.java`

**修改目的**：迁移 `assertThrowsCause` 并修正触发路径与错误消息文案以匹配 Spark 3.4 的实际行为。

**工作逻辑**：原用例关闭表分布与排序后，期望写入失败在 `"Encountered records that belong to already closed files"`。新写法显式追加 `.option(SparkWriteOptions.FANOUT_ENABLED, "false")`，并改断言为 `cause().isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Incoming records violate the writer assumption that records are clustered by spec and by partition within each spec. Either cluster the incoming records or switch to fanout writers.")`。这一改动反映了 Spark 3.4 中 Iceberg 写入校验路径文案的变化，并通过显式禁用 fanout 让错误路径稳定触发。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

**修改目的**：把 `TestTagDDL` 中关于 tag 创建/删除/替换的所有 `AssertHelpers.assertThrows` 调用迁移到 AssertJ，并校对若干错误消息文案。

**工作逻辑**：对每条 `ALTER TABLE ... CREATE TAG ...` 非法语法用例，从 `AssertHelpers.assertThrows("Illegal statement", IcebergParseException.class, "mismatched input", () -> sql(...))` 改为 `Assertions.assertThatThrownBy(() -> sql(...)).isInstanceOf(IcebergParseException.class).hasMessageContaining("mismatched input")`。对于 `unknown snapshot`、`already exists` 等用例，部分把断言升级为完整 `hasMessage(...)`（如 `"Cannot set " + tagName + " to unknown snapshot: -1"`），更精确反映 Iceberg 校验抛出的消息。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java`

**修改目的**：把 `TestUpdate` 中所有对 `AssertHelpers.assertThrows` 的调用迁移到 AssertJ，并校对冲突与写入校验相关错误文案。

**工作逻辑**：例如把对 `UPDATE %s SET a.c1 = 1` 期望 `AnalysisException` 含 `"Updating nested fields is only supported for structs"` 的断言改写为 `Assertions.assertThatThrownBy(() -> sql(...)).isInstanceOf(AnalysisException.class).hasMessageContaining(...)`；把列更新冲突用例的消息断言从 `hasMessageContaining("Updates are in conflict")` 收紧为 `hasMessageStartingWith("Updates are in conflict for these columns")`，与 Spark 3.4 实际输出对齐。整文件共调整 133 行，全部围绕断言风格替换与文案更新。

## 小结

本提交是 Iceberg 测试基础设施去 `AssertHelpers` 化迁移在 Spark 3.4 模块的收尾，把扩展测试统一改为直接使用 AssertJ 流式断言，并顺带把若干与 Spark 3.4 行为相关的错误消息断言对齐到当前实际输出，保持测试的精确性与可维护性。
