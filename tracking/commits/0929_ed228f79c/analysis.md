# 提交 0929：Core: Fix NPE during conflict handling of NULL partitions (#10680)

## 提交信息

- **序号**：0929 / 4088
- **哈希**：ed228f79cd3e569e04af8a8ab411811803bf3a29
- **短哈希**：ed228f79c
- **日期**：2024-07-12 14:25:39 +0200
- **作者**：boroknagyz <boroknagyz@cloudera.com>
- **提交说明**：Core: Fix NPE during conflict handling of NULL partitions (#10680)
- **PR/Issue**：#10680

## 总体目的

Iceberg 在执行 `ReplacePartitions`、`ValidateConflict` 等需要冲突检测的操作时，若发现冲突文件，会构造一条 `ValidationException` 错误信息，把冲突涉及的分区值拼到消息里（例如 `Found conflicting files that can contain records matching partitions [data_bucket=0, data_bucket=1]: [...]`）。`PartitionSet` 在拼接该消息时，对每个分区字段调用 `s.get(i, Object.class).toString()` 来获取值的字符串表示。

问题在于：分区的值可以为 `null`（例如源数据中的 NULL 值会落到 `__HIVE_DEFAULT_PARTITION__`，在内存中表现为 null；又或使用 VOID transform（`alwaysNull`）时该分区字段值恒为 null）。当冲突恰好发生在含 null 值的分区上时，`s.get(i, Object.class)` 返回 `null`，再调用 `.toString()` 就会抛出 `NullPointerException`，从而把原本应该抛出的、对用户友好的 `ValidationException`（告知哪些分区有冲突）掩盖成一个栈层面的 NPE，用户无法定位真正的冲突原因。本提交的目的就是把这种 NPE 修复为正确的错误信息展示。

## 如何达成设计目的

实现方式非常精简：把 `StringBuilder.append(s.get(i, Object.class).toString())` 改为 `StringBuilder.append(s.get(i, Object.class))`。`StringBuilder.append(Object)` 内部会调用 `String.valueOf(obj)`，而 `String.valueOf(null)` 返回字符串 `"null"`，不会抛 NPE。这样 null 分区值会在错误信息中以 `field=null` 的形式展示，既修复了 NPE，又保留了字段名信息，让错误消息依然可读。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PartitionSet.java`

**修改目的**：修复拼接冲突分区错误信息时对 null 分区值调用 `.toString()` 导致的 NPE。

**工作逻辑**：在构造冲突分区字符串的循环中，原代码：

```java
partitionStringBuilder.append(s.get(i, Object.class).toString());
```

改为：

```java
partitionStringBuilder.append(s.get(i, Object.class));
```

利用 `StringBuilder.append(Object)` 对 null 的容错行为（输出 `"null"`），避免对 null 显式调用 `.toString()` 抛 NPE。其余拼接逻辑（字段名、`=`、`StringJoiner`）不变。

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java`

**修改目的**：补充回归测试，覆盖 NULL 分区值与 VOID transform 两种场景下的冲突检测。

**工作逻辑**：
- 新增 `FILE_NULL_PARTITION`（`data_bucket=__HIVE_DEFAULT_PARTITION__`，即 null 分区值）、`SPEC_VOID`（带 `alwaysNull("id")` 的 VOID transform spec）、`FILE_A_VOID_PARTITION` 和 `FILE_B_VOID_PARTITION`。
- `testValidateWithNullPartition`：先提交一个 null 分区文件，再尝试并发 `ReplacePartitions` 添加冲突文件，断言抛出 `ValidationException` 且消息为 `Found conflicting files that can contain records matching partitions [data_bucket=null, data_bucket=1]: [/path/to/data-null-partition.parquet]`（验证 null 正确展示为 `null` 而非 NPE）。
- `testValidateWithVoidTransform`：用 `SPEC_VOID` 建表，先提交 `FILE_A_VOID_PARTITION`，再并发 `ReplacePartitions` 同时加 `FILE_A_VOID_PARTITION` 与 `FILE_B_VOID_PARTITION`，断言抛出 `ValidationException` 且消息含 `[id_null=null, data_bucket=1, id_null=null, data_bucket=0]`，验证 VOID transform 产生的 null 分区也能正确进入错误信息而非 NPE。

## 小结

- **成效**：修复了 `PartitionSet` 在冲突检测错误信息拼接时对 null 分区值调用 `.toString()` 引发的 NPE，使含 NULL 分区值或 VOID transform 的冲突能正常抛出带可读分区信息的 `ValidationException`。
- **影响范围**：`core` 模块的 `PartitionSet.java`（+1/-1）与 `TestReplacePartitions.java`（+77），仅影响冲突错误信息的生成路径，不影响正常提交路径。
- **回迁到 1.4.x 的注意事项**：回迁风险极低，单行改动且行为明确（从抛 NPE 改为输出 "null"）。前提是 1.4.x 的 `PartitionSet` 在相同位置有相同的拼接逻辑。建议连同两个测试一起回迁，防止 1.4.x 上同样问题回归。注意 1.4.x 的 `TestReplacePartitions` 结构需与 main 兼容（`@TestTemplate`、`commit(table, ..., branch)` 等辅助方法存在）。
