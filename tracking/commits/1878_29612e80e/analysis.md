# 提交 1878：Spark: Improve assertions for better debuggability (#12569)

## 提交信息

- **序号**：1878 / 4088
- **哈希**：29612e80eae162e4b3716f0f0eb5364257316a04
- **短哈希**：29612e80e
- **日期**：2025-03-19 10:48:56 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Improve assertions for better debuggability (#12569)
- **PR/Issue**：#12569

## 总体目的

本提交改进 `TestRewriteTablePathsAction` 测试中的 AssertJ 断言，使其在失败时提供更好的可调试性。

背景：原测试中大量使用 `assertThat(collection.size()).isEqualTo(n)` 或 `assertThat(stream.filter(...).count()).isEqualTo(n)` 的模式。这种模式在断言失败时只显示数字不匹配，无法看到实际的集合内容，调试困难。此外，`withFailMessage()` 已不推荐使用，应替换为 `as()`。还有部分断言使用 `.count()` 触发 Spark action 后断言数字，改为 `collectAsList()` 后断言列表大小，能在失败时打印实际行内容。

本提交将这些断言统一改为 AssertJ 的集合断言 API（`hasSize`、`isEmpty`），并在需要时用 `collectAsList()` 替代 `count()` 以便失败时输出实际数据。

## 如何达成设计目的

1. **集合大小断言**：将 `assertThat(list.size()).isEqualTo(n)` 改为 `assertThat(list).hasSize(n)`，失败时会打印实际集合内容。

2. **空集合断言**：将 `assertThat(...count()).isEqualTo(0)` 改为 `assertThat(...).isEmpty()`。

3. **Stream 过滤断言**：将 `assertThat(stream.filter(...).count()).isEqualTo(n)` 改为 `assertThat(stream.filter(...)).hasSize(n)`（对过滤后的流收集为 List 后断言）。

4. **失败消息**：将 `withFailMessage(msg)` 替换为 `as(msg)`。

5. **Spark action 优化**：将 `assertThat(spark.read()...count()).isEqualTo(n)` 改为 `assertThat(spark.read()...collectAsList()).hasSize(n)`，失败时可查看实际行。

6. **去除不必要的 throws**：`toAbsolute` 方法移除 `throws IOException`（不再需要）。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (修改, +39/-34 lines)

**修改目的**：改进断言可调试性。

**工作逻辑**：全文约 15 处断言改写，典型模式：
- `assertThat(validDataFiles.size()).isEqualTo(2)` → `assertThat(validDataFiles).hasSize(2)`
- `assertThat(paths.stream().filter(...).count()).withFailMessage(...).isEqualTo(1)` → `assertThat(paths.stream().filter(...)).as(...).hasSize(1)`
- `assertThat(paths.stream().filter(...).count()).withFailMessage(...).isEqualTo(0)` → `assertThat(paths.stream().filter(...)).as(...).isEmpty()`
- `assertThat(spark.read()...count()).isEqualTo(1)` → `assertThat(spark.read()...collectAsList()).hasSize(1)`
- `assertThat(spark.read()...count()).isEqualTo(0)` → `assertThat(spark.read()...collectAsList()).isEmpty()`
- `assertThat(sourceTable.statisticsFiles().size()).isEqualTo(iterations)` → `assertThat(sourceTable.statisticsFiles()).hasSize(iterations)`
- `assertThat(filesToMove.size()).as("Wrong total file count").isEqualTo(totalCount)` → `assertThat(filesToMove).as("Wrong total file count").hasSize(totalCount)`
- `toAbsolute(Path relative) throws IOException` → `toAbsolute(Path relative)`（移除异常声明）

## 总结

本提交是测试代码质量改进，将 `TestRewriteTablePathsAction` 中的断言从"断言 size/count 数字"改为"断言集合本身大小/空"，并将 `withFailMessage` 替换为 `as`、`count()` 替换为 `collectAsList()`，使断言失败时能输出实际集合内容，提升调试效率。无功能变更。
