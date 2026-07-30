# 提交 0627：Core, Spark: Fix handling of null binary values when sorting with zorder

## 提交信息

- **序号**：0627 / 4088
- **哈希**：602186bedc7f5be270f9a0cd5c2c15da7bddcdb9
- **短哈希**：602186bed
- **日期**：2024-03-25 15:03:33 -0700
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Core, Spark: Fix handling of null binary values when sorting with zorder (#10026)
- **PR/Issue**：#10026

## 总体目的

本提交修复一个 Z-Order 排序场景下的空指针异常（NPE）Bug：当参与 Z-Order 排序的列中包含 `binary`（二进制）类型，且该列存在 `null` 值时，对数据文件执行 `rewrite_data_files`（sort 策略 + zorder 排序）会抛出 `NullPointerException`，导致重写失败。

**背景**：Z-Order（Z 曲线）排序是 Iceberg 提供的多维数据聚类能力，通过对多个排序列的字节表示按位交错（interleave）生成单一排序键，使数据在多维度上同时具备局部性，从而提升范围查询的跳过率。Iceberg 的 `ZOrderByteUtils` 负责将各类型值转换为定长、字典序可比较的字节表示。

**Bug 根因**：`ZOrderByteUtils.byteTruncateOrFill(byte[] val, int length, ByteBuffer reuse)` 方法用于将 `binary` 类型的字节数组截断或补零到定长。但当 `binary` 列值为 `null` 时，上层传入的 `val` 为 `null`，而原方法体第一行就执行 `val.length`，直接触发 NPE。对比之下，同文件中的 `stringToOrderedBytes` 方法已经通过 `if (val != null)` 判空处理了 null 字符串（输出全 0），但 `byteTruncateOrFill` 遗漏了相同的判空逻辑。

## 如何达成设计目的

修复思路非常直接且与既有设计保持一致：在 `byteTruncateOrFill` 方法入口处增加 null 检查，若 `val == null` 则将整个输出缓冲区填充为 `0x00` 并返回。

**为什么用全 0 表示 null**：这与 Z-Order 的字节序语义一致。Z-Order 要求所有列的转换结果都按无符号字典序可比较，全 0 字节序列在字典序中最小，意味着 null 值被排在所有非 null 值之前。这与 `stringToOrderedBytes` 对 null 字符串的处理方式完全一致，保证了不同类型列在 null 处理上的语义统一性。在 `interleaveBits` 按位交错时，全 0 的列贡献也自然是全 0 比特，不会破坏交错结果的可比较性。

**修复范围**：核心修复仅 5 行（含空行），位于 `core` 模块的 `ZOrderByteUtils`。同时在 `core` 单元测试和 Spark 3.3/3.4/3.5 三个版本的 `TestRewriteDataFilesProcedure` 中新增了端到端回归测试，验证含 null binary 列的 Z-Order 重写能正常完成。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ZOrderByteUtils.java`

**修改目的**：在 `byteTruncateOrFill` 方法中处理 `val` 为 null 的情况，避免 NPE。

**工作逻辑**：在方法体开头（`ByteBuffers.reuse(reuse, length)` 之后）插入 null 检查：

```java
if (val == null) {
  Arrays.fill(bytes.array(), 0, length, (byte) 0x00);
  return bytes;
}
```

完整方法的执行流程如下：
1. 通过 `ByteBuffers.reuse(reuse, length)` 获取（或复用）一个长度为 `length` 的 `ByteBuffer`，其底层 `byte[]` 数组内容未初始化。
2. **新增**：若 `val` 为 null，用 `0x00` 填充整个 `[0, length)` 区间后直接返回。这保证 null binary 值在 Z-Order 中映射为全零字节序列，与 null string 的处理一致。
3. 若 `val` 非空且 `val.length < length`：写入 `val` 全部内容，剩余区间 `[val.length, length)` 填充 `0x00`（短数组右侧补零）。
4. 若 `val` 非空且 `val.length >= length`：只写入 `val` 的前 `length` 字节（长数组截断）。

此修改与同文件 `stringToOrderedBytes` 的 null 处理模式完全对齐，是修复遗漏的判空逻辑而非引入新设计。

### `core/src/test/java/org/apache/iceberg/util/TestZOrderByteUtil.java`

**修改目的**：为 `byteTruncateOrFill` 的 null 处理新增单元测试。

**工作逻辑**：新增 `testByteTruncatedOrFillNullIsZeroArray` 测试方法。分配一个 128 字节的 `ByteBuffer`，调用 `ZOrderByteUtils.byteTruncateOrFill(null, 128, buffer)`，断言返回的字节数组与一个全 0 的 128 字节数组相等。这直接验证了 null 输入产出全零输出的契约。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`

**修改目的**：在 Spark 3.3 上新增端到端回归测试，覆盖含 null binary 列的 Z-Order 数据重写。

**工作逻辑**：新增 `testRewriteDataFilesWithZOrderNullBinaryColumn` 测试方法，步骤如下：
1. 创建含 `c1 int, c2 string, c3 binary` 三列的 iceberg 表。
2. 循环 5 次插入 `(1, 'foo', null)` 和 `(2, 'bar', null)`，共 10 行、10 个数据文件（每次 INSERT 产生一个文件）。
3. 调用 `system.rewrite_data_files` 存储过程，策略为 `sort`，排序规则为 `zorder(c2, c3)`——其中 `c3` 是含 null 的 binary 列，正是触发原 Bug 的场景。
4. 断言重写结果：删除 10 个文件、新增 1 个文件（`row(10, 1)`），且输出数组长度为 3（Spark 3.3 版本结果列数）。
5. 断言快照摘要中 `REMOVED_FILE_SIZE_PROP` 与输出中删除文件总大小一致。
6. 断言重写后数据按 Z-Order 排序：5 行 `(2, 'bar', null)` 在前、5 行 `(1, 'foo', null)` 在后（因为 'bar' > 'foo' 字典序，c3 全 null 映射为全 0 不影响 c2 的排序主导）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`

**修改目的**：与 Spark 3.3 相同的回归测试，针对 Spark 3.4 版本。

**工作逻辑**：测试逻辑与 3.3 版本几乎完全相同，唯一差异是断言输出数组长度为 4（`assertThat(output.get(0)).hasSize(4)`），这是因为 Spark 3.4 的 `rewrite_data_files` 存储过程返回结果列比 3.3 多一列（包含 `ADDED_FILE_SIZE_PROP` 等）。其余步骤完全一致。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`

**修改目的**：与 Spark 3.4 相同的回归测试，针对 Spark 3.5 版本。

**工作逻辑**：测试逻辑与 3.4 版本完全相同，差异仅在于使用 `@TestTemplate` 注解（Spark 3.5 测试基类使用 JUnit 5 的 `@TestTemplate` 配合参数化测试，而非 3.3/3.4 的 `@Test`）。断言输出数组长度同为 4。

## 小结

**成效**：本提交以极小的核心改动（5 行）修复了一个会导致 Z-Order 重写直接失败的生产 Bug。修复方式与既有 `stringToOrderedBytes` 的 null 处理模式保持一致，语义正确且无副作用。三个 Spark 版本的端到端测试确保了修复在所有维护的 Spark 版本上均有效。

**影响范围**：
- 核心修复位于 `core` 模块的 `ZOrderByteUtils`，影响所有使用 Z-Order 排序且涉及 `binary` 类型列的场景（不仅限于 Spark，Flink、Trino 等引擎调用同一核心工具类）。
- 此前，任何含 null binary 值的 Z-Order 重写都会失败；修复后可正常完成。
- 对非 null 的 binary 值处理无任何影响（新增的 null 分支在 val 非空时不进入）。

**回迁到 1.4.x 的注意事项**：
- 核心修复（`ZOrderByteUtils.java` + `TestZOrderByteUtil.java`）可无损回迁，1.4.x 分支上的方法签名和实现与 main 一致。
- Spark 测试需要根据 1.4.x 实际维护的 Spark 版本选择性回迁：1.4.x 通常维护 Spark 3.3/3.4/3.5，需确认各版本测试文件中 `rewrite_data_files` 结果列数（3.3 为 3 列、3.4/3.5 为 4 列）与 1.4.x 实际一致，否则断言 `hasSize` 需调整。
- 测试中使用的 `@Test` vs `@TestTemplate` 注解需与 1.4.x 各 Spark 版本测试基类的注解风格一致。
- 该修复是纯 Bug 修复，无行为变更风险，建议优先回迁。
