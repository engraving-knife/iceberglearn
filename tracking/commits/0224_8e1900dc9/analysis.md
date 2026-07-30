# 提交 0224：Style: Replace Arrays.asList with Collections.singletonList (#9213)

## 提交信息

- **序号**：0224 / 4088
- **哈希**：8e1900dc9ab917cd97a41e7145166a8c8a4bdfc5
- **短哈希**：8e1900dc9
- **日期**：2023-12-05 15:48:30 -0600
- **作者**：Junhao Liu
- **提交说明**：Style: Replace Arrays.asList with Collections.singletonList (#9213)
- **PR/Issue**：#9213

## 总体目的

这是一个代码风格优化提交，将项目中那些"仅含单个元素且使用 `Arrays.asList(...)` 构造列表"的位置替换为 `Collections.singletonList(...)`。

`Arrays.asList(x)` 与 `Collections.singletonList(x)` 都能产生一个包含单个元素的列表，但语义和实现不同：
- `Arrays.asList(x)` 内部基于一个定长数组，列表大小固定但元素可变（`set` 可修改元素），且语义上更适合"由数组转列表"或"可能扩展为多元素"的场景；
- `Collections.singletonList(x)` 返回一个不可变的、明确语义为"仅一个元素"的列表，实现上更轻量，且通过类型明确表达"这就是一个单元素列表"的意图。

在那些确实只需要一个元素、且不需要后续扩展或修改的位置使用 `Collections.singletonList`，可以让代码意图更清晰、避免误用 `set` 等可变操作，也符合常见的 Java 风格最佳实践。本提交属于纯风格调整，不改变任何运行时行为。

## 如何达成设计目的

改动方式是机械式替换：扫描各模块中所有 `Arrays.asList(单参数)` 的调用点，替换为 `Collections.singletonList(单参数)`，并相应调整 import（移除未再使用的 `java.util.Arrays`，新增 `java.util.Collections`）。涉及 4 个模块（aws、core、flink 1.15/1.16/1.17、hive-metastore）共 10 个文件。值得注意的细节：当 `Arrays.asList(...)` 仍包含多元素调用时，本提交保留原样（如 `TestS3SignRequestParser` 中的 `Arrays.asList("attempt=1", "max=4")`），仅替换单元素场景，体现了精确的范围控制。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3SignRequestParser.java`

**修改目的**：将测试中 `Arrays.asList("191")`、`Arrays.asList("application/json")` 等单元素列表替换为 `Collections.singletonList(...)`，新增 `import java.util.Collections`。注意 `Arrays.asList("aws-sdk-java/2.20.18", "Linux/5.4.0-126")` 等多元素列表保持不变。

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3SignResponseParser.java`

**修改目的**：同上，将 `Content-Length`、`Content-Type` 等单元素 header 值列表替换为 `Collections.singletonList(...)`，`amz-sdk-request` 和 `User-Agent` 等多元素列表保持 `Arrays.asList` 不变。新增 `import java.util.Collections`。

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`

**修改目的**：这是本次唯一的产品代码改动。在读取清单时将 `.select(Arrays.asList("*"))` 替换为 `.select(Collections.singletonList("*"))`，明确表达"只选择一个通配列"的意图。import 由 `java.util.Arrays` 调整为 `java.util.Collections`。

**工作逻辑**：`BaseRewriteManifests` 在重写清单流程中读取每个被重写的清单，使用 `ManifestFiles.read(...).select(...)` 选择要读取的列。原代码用 `Arrays.asList("*")` 传入单个通配列 `"*"` 表示"选择所有列"，本质是单元素列表。改为 `Collections.singletonList("*")` 后语义更清晰，且不可变列表也防止了后续误修改。

### `core/src/test/java/org/apache/iceberg/ScanTestBase.java`

**修改目的**：将多个扫描测试中 `select(Arrays.asList("id"))`、`select(Arrays.asList("ID"))` 等单列选择替换为 `Collections.singletonList(...)`，import 由 `java.util.Arrays` 调整为 `java.util.Collections`。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java`

**修改目的**：将 `Arrays.asList(128L)` 替换为 `Collections.singletonList(128L)`。新增 `import java.util.Collections`，但保留 `import java.util.Arrays`（文件其它位置仍在使用）。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousIcebergEnumerator.java`

**修改目的**：将 `splitPlanner.addSplits(Arrays.asList(...))` 与 `new SplitRequestEvent(Arrays.asList(...))` 等单元素调用替换为 `Collections.singletonList(...)`，移除已不再使用的 `import java.util.Arrays`。部分长行因参数换行而重新格式化。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestIcebergSourceReader.java` 与 `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestIcebergSourceReader.java`

**修改目的**：将 `reader.addSplits(Arrays.asList(split))` 替换为 `Collections.singletonList(split)`。两个版本文件内容相同、改动相同。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveSchemaUtil.java`

**修改目的**：将 `Arrays.asList("customer comment")` 单元素列表替换为 `Collections.singletonList(...)`。注意另一处 `Lists.newArrayList(Arrays.asList(notSupportedField))` 中外层是单元素 `Arrays.asList`，但语义是"包装成可变 List"，替换为 `Lists.newArrayList(Collections.singletonList(notSupportedField))` 以保持单元素语义。保留 `import java.util.Arrays`（文件其它位置仍在使用），新增 `import java.util.Collections`。

## 小结

一次跨多模块的代码风格统一，将所有单元素 `Arrays.asList` 调用替换为语义更精确、不可变的 `Collections.singletonList`，仅有一处产品代码（`BaseRewriteManifests`）涉及，其余为测试代码，不改变任何运行时行为。
