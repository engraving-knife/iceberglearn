# 提交 0299：Spark: Fix AddFilesProcedure error message when no partitions are found (#9357)

## 提交信息

- **序号**：0299 / 4088
- **哈希**：a077a2be318b6b21c849ba7754987604c728715b
- **短哈希**：a077a2be3
- **日期**：2023-12-22 14:35:59 +0100
- **作者**：panbingkun
- **提交说明**：Spark: Fix AddFilesProcedure error message when no partitions are found (#9357)
- **PR/Issue**：#9357

## 总体目的

Iceberg 的 Spark `AddFilesProcedure` 过程用于将外部数据文件（如 Parquet/ORC/Avro 文件）导入到 Iceberg 表中。在导入分区表时，过程会先扫描源路径下匹配的分区，若未找到任何匹配分区，则通过 `Preconditions.checkArgument` 抛出参数校验异常。然而该异常的错误消息存在一个明显的逻辑缺陷：

```java
Preconditions.checkArgument(
    !partitions.isEmpty(), "Cannot find any matching partitions in table %s", partitions);
```

这里的 `%s` 占位符传入的是 `partitions`（一个空的分区集合），而非表的标识/名称。当 `partitions` 为空时（即触发异常的条件），错误消息会变成类似 `"Cannot find any matching partitions in table []"`，这对用户毫无帮助——用户看到的是空的分区列表，而无法得知到底是哪张表出了问题。正确的做法应当是显示表的名称，让用户能立即定位是哪张表没有匹配到分区。

这一缺陷会导致用户在排查"为什么文件导入失败"时缺乏关键上下文信息，增加支持成本。本次提交的目的就是修正该错误消息，将 `%s` 的参数从 `partitions` 改为 `table.name()`，使错误消息正确显示表名，提升排错效率。

由于 Iceberg 同时维护 Spark 3.3、3.4、3.5 三个版本分支，三个版本中该过程源文件存在同样的缺陷，因此本提交同时修复三个版本的 `AddFilesProcedure.java`，保证一致性。

## 如何达成设计目的

作者将三个 Spark 版本（v3.3、v3.4、v3.5）下 `AddFilesProcedure.java` 中同一处 `Preconditions.checkArgument` 调用的最后一个参数从 `partitions` 改为 `table.name()`。修改极其局部（每处仅 1 行），但精准修复了错误消息的语义错误，使 `%s` 占位符填充表名而非空分区集合。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java`

**修改目的**：修正 Spark 3.3 版本中 `AddFilesProcedure` 未找到分区时的错误消息参数。

**工作逻辑**：
在 `AddFilesProcedure` 内部导入分区的逻辑中，当 `partitions` 集合非空时走单分区或批量导入路径，否则进入 `else` 分支校验分区不为空。原代码：

```java
} else {
  Preconditions.checkArgument(
      !partitions.isEmpty(), "Cannot find any matching partitions in table %s", partitions);
  importPartitions(table, partitions, checkDuplicateFiles);
}
```

修改为：

```java
} else {
  Preconditions.checkArgument(
      !partitions.isEmpty(), "Cannot find any matching partitions in table %s", table.name());
  importPartitions(table, partitions, checkDuplicateFiles);
}
```

将格式化参数 `partitions`（此时为空集合）替换为 `table.name()`（表的标识名），使异常消息正确呈现为 `"Cannot find any matching partitions in table <表名>"`，让用户能立即知道是哪张表未匹配到分区。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java`

**修改目的**：同上，修正 Spark 3.4 版本的同一处错误消息参数。

**工作逻辑**：与 v3.3 完全相同的修改——`partitions` → `table.name()`。三个版本的该文件此处代码完全一致，故修改内容一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java`

**修改目的**：同上，修正 Spark 3.5 版本的同一处错误消息参数。

**工作逻辑**：与 v3.3、v3.4 完全相同的修改——`partitions` → `table.name()`。

## 小结

这是一个精准的错误消息修复提交，将 `AddFilesProcedure` 在"未找到匹配分区"异常中错误使用的 `partitions`（空集合）参数改为 `table.name()`（表名），使错误消息能正确告知用户是哪张表未匹配到分区，显著提升排错体验。修复同时覆盖 Spark 3.3/3.4/3.5 三个版本，保证跨版本一致性，改动极小（每版本 1 行）但实用价值明显。
