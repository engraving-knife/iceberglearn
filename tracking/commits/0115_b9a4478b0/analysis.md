# 提交 0115：Spark 3.5: Remove AssertHelpers usage (#8948)

## 提交信息

- **序号**：0115 / 4088
- **哈希**：b9a4478b0f8f5eeae553eb3900b08a7a08dbdb40
- **短哈希**：b9a4478b0
- **日期**：2023-10-31 15:33:31 +0530
- **作者**：Ashok
- **提交说明**：Spark 3.5: Remove AssertHelpers usage (#8948)
- **PR/Issue**：#8948

## 总体目的

这个提交要解决的是 Spark 3.5 测试代码仍在使用已废弃的 `AssertHelpers` 工具类的问题。Iceberg 的 [`AssertHelpers`](../../../../api/src/test/java/org/apache/iceberg/AssertHelpers.java) 是一个早期提供的异常断言辅助类，它把 `assertThrows(msg, ExceptionClass, "containedMsg", () -> ...)`、`assertThrowsCause(...)` 等模式包装在静态方法里。但该类已被整体标记 `@Deprecated`，其 JavaDoc 明确建议：直接使用 AssertJ 的 `Assertions.assertThatThrownBy(...)` 流式 API，因为它"提供了更流畅的异常断言方式"（`provides a more fluent way of asserting on exceptions`）。`AssertHelpers` 内部本身也只是 AssertJ 的薄包装。

本提交由 Ashok 在 PR #8948 中，针对 Spark 3.5 的 5 个扩展测试文件，把所有 `AssertHelpers.assertThrows` / `AssertHelpers.assertThrowsCause` 调用点逐一改写为等价的 `Assertions.assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`（或 `.cause().isInstanceOf(...)` 对应 `assertThrowsCause`）链式写法，并移除 `AssertHelpers` 的 import。这是 Iceberg 持续进行的"去废弃 API"测试代码清理工作的一部分，与此前其他模块/版本的同类清理一脉相承。

对 Iceberg 演进的意义在于：消除对废弃工具类的依赖，使测试代码统一到社区推荐的 AssertJ 原生写法，降低长期维护成本，并为最终移除 `AssertHelpers` 类本身铺路。属于测试基础设施的现代化治理，不改变任何生产代码行为。

## 如何达成设计目的

整体设计思路是"机械等价改写"：对每一处 `AssertHelpers` 调用，用 AssertJ 原生 API 表达相同语义，保证测试覆盖的异常类型与消息断言不变。改写规则有两条：

1. `AssertHelpers.assertThrows(msg, ExClass, containedMsg, () -> action)` → `Assertions.assertThatThrownBy(() -> action).isInstanceOf(ExClass.class).hasMessageContaining(containedMsg)`（描述用 `msg` 通过 `.as(msg)` 传递的语义被丢弃，因 AssertJ 链式写法本身可读性足够）。

2. `AssertHelpers.assertThrowsCause(msg, ExClass, containedMsg, () -> action)` → `Assertions.assertThatThrownBy(() -> action).cause().isInstanceOf(ExClass.class).hasMessageContaining(containedMsg)`，即多一个 `.cause()` 来定位被包装异常的根本原因。

同时，少数断言的匹配方式或消息文案做了精确化调整以贴合实际抛出的异常（例如把 `hasMessageContaining` 改为 `hasMessage` 精确匹配，或更新错误消息字符串以匹配 Spark 3.5 实际文案）。改动集中在 5 个测试文件，共 629 行新增、747 行删除（净减 118 行，因 AssertJ 链式写法更紧凑、且移除了 `AssertHelpers` 调用的样板缩进）。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestChangelogTable.java`

**修改目的**：移除该文件对 `AssertHelpers` 的使用，改用 AssertJ 原生断言。

**工作逻辑**：移除 `import static org.apache.iceberg.AssertHelpers.assertThrows;`，新增 `import org.assertj.core.api.Assertions;`。在 `changelogRecords` 起止时间校验用例中，把 `assertThrows("Should fail if start time is after end time", IllegalArgumentException.class, () -> changelogRecords(...))` 改写为 `Assertions.assertThatThrownBy(() -> changelogRecords(...)).isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot set start-timestamp to be greater than end-timestamp for changelogs")`。这里把消息匹配从"包含"精确化为完整 `hasMessage`，明确断言 changelog 起止时间倒置时的具体错误文案。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRequiredDistributionAndOrdering.java`

**修改目的**：把 `AssertHelpers.assertThrowsCause` 改写为 AssertJ `.cause()` 链式断言。

**工作逻辑**：替换 import。在"关闭 ordering 应拒绝写入"用例中，原 `AssertHelpers.assertThrowsCause("Should reject writes without ordering", IllegalStateException.class, "Encountered records that belong to already closed files", () -> { try { inputDF.writeTo(...)... } catch (NoSuchTableException e) { throw new RuntimeException(e); } })` 改写为 `Assertions.assertThatThrownBy(() -> inputDF.writeTo(tableName).option(...).option(...).append()).cause().isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Incoming records violate the writer assumption that records are clustered by spec and by partition within each spec. Either cluster the incoming records or switch to fanout writers.")`。改写同时去掉了原先为绕开受检异常 `NoSuchTableException` 而写的 try/catch 包装（AssertJ 的 `ThrowingCallable` 不抛受检异常），并把期望消息更新为 Spark 3.5 实际抛出的"records violate the writer assumption"文案，用 `hasMessageStartingWith` 而非完整匹配。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

**修改目的**：把该文件多处 `AssertHelpers.assertThrows` 改写为 AssertJ 链式断言。

**工作逻辑**：替换 import。涉及多处 TAG DDL 异常用例：非法 `ALTER TABLE ... CREATE TAG ... RETAIN` 语法（`IcebergParseException` + "mismatched input"）、未知快照版本（`ValidationException`，消息精确化为 `hasMessage("Cannot set " + tagName + " to unknown snapshot: -1")`）、重复创建已存在 tag（`IllegalArgumentException` + "already exists"）、非法 tag 名 `123`（`IcebergParseException` + "mismatched input '123'"）、对 branch 执行 replace tag / drop tag（`IllegalArgumentException` + "Ref ... is a branch not a tag"）、对不存在 tag 执行 replace / drop（`IllegalArgumentException` + "Tag does not exist"）。全部由 `AssertHelpers.assertThrows(msg, ExClass, containedMsg, () -> sql(...))` 改写为 `Assertions.assertThatThrownBy(() -> sql(...)).isInstanceOf(ExClass.class).hasMessageContaining(containedMsg)`，语义不变。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：该文件是本提交改动量最大的文件（1036 行变动），把所有 `AssertHelpers.assertThrows` / `assertThrowsCause` 调用统一改写为 AssertJ 链式断言。

**工作逻辑**：替换 import。涉及 MERGE 语句的多种异常场景：多行匹配单行（`SparkRuntimeException`，通过 `.cause()` 断言根因，期望消息从 `a single row from the target table with multiple rows of the source table` 精确化为完整 `MERGE statement matched a single row from the target table with multiple rows of the source table.`）、非确定性条件（`AnalysisException`）、子查询条件（`AnalysisException` + "Subqueries are not allowed"）、无法解析列（`AnalysisException` + "cannot be resolved"）、非 Iceberg 目标表（`UnsupportedOperationException`，用 `hasMessage` 精确匹配 `MERGE INTO TABLE is not supported temporarily.`）等。所有改写遵循前述两条规则，`assertThrowsCause` 对应 `.cause().isInstanceOf(...).hasMessageContaining(...)`。该文件因样板缩进减少而显著瘦身。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java`

**修改目的**：把 UPDATE 语句相关异常断言从 `AssertHelpers` 改写为 AssertJ 链式断言。

**工作逻辑**：替换 import。涉及 UPDATE 的多种异常场景：更新数组/Map 嵌套列（`AnalysisException` + "Updating nested fields is only supported for StructType"）、冲突的多重赋值（`AnalysisException` + "Multiple assignments" / "Conflicting assignments"）、ANSI/strict 模式下写空值/缺失字段/类型不兼容（`SparkException` 或 `AnalysisException` + 对应消息）、非确定性条件、非 Iceberg 表 UPDATE（`UnsupportedOperationException`，精确匹配 `UPDATE TABLE is not supported temporarily.`）。全部按规则改写，并把多处 `hasMessageContaining` 用于部分匹配、少数用 `hasMessage` 精确匹配。

## 小结

通过把 Spark 3.5 五个扩展测试文件中对已废弃 `AssertHelpers` 的全部调用改写为 AssertJ 原生 `assertThatThrownBy` 链式断言，本提交完成了该模块测试代码的去废弃化清理，使异常断言统一到社区推荐写法，为最终移除 `AssertHelpers` 类铺路。
