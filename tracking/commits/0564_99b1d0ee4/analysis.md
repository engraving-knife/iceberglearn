# 提交 0564：Spark 3.5——重写数据文件时支持指定 output-spec-id

## 提交信息

- **序号**：0564 / 4088
- **哈希**：99b1d0ee4c8fb67d457c4d7a03065e4cb63af435
- **短哈希**：99b1d0ee4
- **日期**：2024-03-06（AuthorDate 2024-03-06 21:58:54 -0800）
- **作者**：Himadri Pal <mehimu@gmail.com>
- **提交说明**：Spark 3.5: Add Support for Providing output-spec-id During Rewrite Datafiles
- **PR/Issue**：本提交未在 message 中列出 PR 号，但属于 Spark 3.5 `RewriteDataFiles` action 的功能增强。

## 总体目的

本提交要为 Spark 3.5 的 `RewriteDataFiles` action 增加一项能力：**让用户在重写数据文件时，可以通过 `output-spec-id` 选项指定重写后文件所使用的分区规范（partition spec）ID**，而不必总是使用表的当前（最新）spec。

**背景动机**：

- Iceberg 表的分区规范会随时间演进——每次 `updateSpec` 都会产生一个新的 spec（带递增 specId），旧 spec 仍保留在 `table.specs()` 中。表中的数据文件各自记录了它所属的 `specId`（`DataFile.specId()`），因此一张表可能同时存在分属不同 spec 的文件。
- 在本提交之前，`RewriteDataFiles`（无论 BINPACK、SORT 还是 ZORDER 策略）重写出的文件**总是使用表的当前 spec**（`table().spec()`）。这意味着任何重写都会把数据重新组织到最新分区布局上，即使用户并不希望如此。
- 用户可能希望反过来：把数据重写回某个**旧 spec**（例如回退一次分区演进），或显式指定重写产物落到某个特定 spec。此前没有途径做到这一点——Spark 写路径（`SparkWriteConf.outputSpecId()`、`SparkWriteOptions.OUTPUT_SPEC_ID`）其实已具备按指定 spec 写文件的基础设施，但 `RewriteDataFiles` action 从未把它暴露出来，也从未在重写的分布/排序逻辑中考虑非当前 spec 的输出。
- 本提交补齐这一缺口：在 action API 层新增 `OUTPUT_SPEC_ID` 选项常量，在 rewriter 链路中解析、校验、透传该选项，并修正重写时的分布模式判定与排序顺序构建，使其面向"输出 spec"而非"表当前 spec"。

## 如何达成设计目的

整体设计分为四层，自下而上打通"选项声明 → 解析校验 → 写入透传 → 分布与排序适配"：

1. **API 层声明选项**：在 `RewriteDataFiles` 接口中新增常量 `OUTPUT_SPEC_ID = "output-spec-id"`，并附 Javadoc 说明其语义（重写文件使用的分区 spec ID，默认为表当前 spec）。

2. **核心 rewriter 层解析与校验**：在 `SizeBasedFileRewriter`（所有基于大小的 rewriter 的抽象基类，BINPACK/SORT/ZORDER 均继承自它）中：
   - 新增字段 `outputSpecId`，在 `init(options)` 中通过 `outputSpecId(options)` 解析。
   - `outputSpecId(options)` 用 `PropertyUtil.propertyAsInt` 从 options 读取 `OUTPUT_SPEC_ID`，缺省回退到 `table.spec().specId()`，并用 `Preconditions.checkArgument(table.specs().containsKey(specId), ...)` 校验该 spec 在表中确实存在（防止用户传一个不存在的 specId）。
   - 暴露 `outputSpec()`（返回 `table.specs().get(outputSpecId)` 对应的 `PartitionSpec`）与 `outputSpecId()`（返回 int）两个 protected 方法供子类使用。

3. **Spark action 层注册选项**：在 `RewriteDataFilesSparkAction` 的 `VALID_OPTIONS` 集合中加入 `OUTPUT_SPEC_ID`，使该选项通过 action 的选项校验（action 会拒绝不在白名单内的选项）。

4. **各 Spark rewriter 子类适配写入与分布/排序逻辑**：
   - **写入透传**：`SparkBinPackDataRewriter` 与 `SparkShufflingDataRewriter` 在 `.write()` 链上加 `.option(SparkWriteOptions.OUTPUT_SPEC_ID, outputSpecId())`，把目标 specId 传给 Spark 写路径。Spark 写路径的 `SparkWriteConf.outputSpecId()` 本就支持读取此 option 并按该 spec 写文件（含同样的 `table.specs().containsKey` 校验），因此 rewriter 只需透传即可。
   - **分布模式判定**（BINPACK）：`SparkBinPackDataRewriter.distributionMode` 原本比较 `group.get(0).spec()` 与 `table().spec()` 决定是否需要 repartition；改为比较 `group.get(0).spec()` 与 `outputSpec()`。即判断"输入文件组的 spec 是否与**目标输出 spec**一致"，若不一致则需 `DistributionMode.RANGE` 重新分布，否则 `NONE`。
   - **排序顺序构建**（SORT/ZORDER 的基类 `SparkShufflingDataRewriter`）：`outputSortOrder(group)` 原本比较 `group.get(0).spec()` 与 `table().spec()`，一致则直接用用户 `sortOrder()`，不一致则用 `SortOrderUtil.buildSortOrder(table(), sortOrder())` 把分区列前置进排序顺序；改为比较 `group.get(0).spec()` 与 `outputSpec()`，且构建时改用三参重载 `SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder())`——其中 `spec` 是输出 spec（而非表的当前 spec），`sortSchema()` 是新增的可覆盖钩子。
   - **可覆盖的 sortSchema 钩子**：新增 `protected Schema sortSchema()`，默认返回 `table().schema()`，供 `buildSortOrder` 解析排序顺序中的列引用。`SparkZOrderDataRewriter` 覆盖该方法，把表 schema 列与 Z-order 计算列 `ICEZVALUE`（来自 `Z_SCHEMA`）合并，因为 Z-order 排序顺序引用了 `ICEZVALUE` 这个不在表 schema 中的运行时计算列，`buildSortOrder` 需要在一个能解析该列的 schema 上工作。

**关键设计点：为什么需要 `sortSchema` 钩子**

`SortOrderUtil.buildSortOrder(schema, spec, sortOrder)` 会把 `spec` 的分区列（引用表中的真实列）与用户 `sortOrder`（可能引用计算列）合并成一个综合排序顺序，所有列引用都要在传入的 `schema` 中能找到。普通 SORT 重写只引用表列，`table().schema()` 足够；但 ZORDER 重写的 `sortOrder()` 是 `Z_SORT_ORDER`（只按 `ICEZVALUE` 排序），而 `ICEZVALUE` 是运行时由 `zValue(df)` 计算出来的二进制列，不在表 schema 中。若仍用 `table().schema()` 调 `buildSortOrder`，分区列能解析但 `ICEZVALUE` 解析不了会出错。因此引入 `sortSchema()` 钩子，ZORDER 子类覆盖为"表列 + Z_SCHEMA 列"的并集，使综合排序顺序的所有列引用都能被解析。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java`

**修改目的**：在 action 接口层声明 `output-spec-id` 选项常量，作为用户配置入口。

**工作逻辑**：新增常量与 Javadoc：

```java
/**
 * The partition specification ID to be used for rewritten files
 *
 * <p>output-spec-id ID is used by the file rewriter during the rewrite operation to identify the
 * specific output partition spec. Data will be reorganized during the rewrite to align with the
 * output partitioning. Defaults to the current table specification.
 */
String OUTPUT_SPEC_ID = "output-spec-id";
```

该常量与 Spark 写路径已有的 `SparkWriteOptions.OUTPUT_SPEC_ID`（同值 `"output-spec-id"`）字符串一致，保证 action option 到 Spark write option 的透传无需转译。默认行为（不设置时）由 rewriter 层回退到 `table.spec().specId()` 实现，保持向后兼容。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java`

**修改目的**：在所有大小类 rewriter 的公共基类中解析、校验 `output-spec-id`，并向子类暴露 `outputSpec()` / `outputSpecId()`。

**工作逻辑**：

- 新增 import `org.apache.iceberg.PartitionSpec`。
- 新增实例字段 `private int outputSpecId;`。
- 在 `init(Map<String,String> options)` 末尾追加 `this.outputSpecId = outputSpecId(options);`，与 `minInputFiles`/`rewriteAll`/`maxGroupSize` 等同样从 options 解析。
- 新增三个方法：

  ```java
  protected PartitionSpec outputSpec() {
    return table.specs().get(outputSpecId);
  }

  protected int outputSpecId() {
    return outputSpecId;
  }

  private int outputSpecId(Map<String, String> options) {
    int specId =
        PropertyUtil.propertyAsInt(options, RewriteDataFiles.OUTPUT_SPEC_ID, table.spec().specId());
    Preconditions.checkArgument(
        table.specs().containsKey(specId),
        "Cannot use output spec id %s because the table does not contain a reference to this spec-id.",
        specId);
    return specId;
  }
  ```

  - `outputSpecId(options)`：核心解析逻辑。`PropertyUtil.propertyAsInt` 三参版接受默认值，缺省时回退到 `table.spec().specId()`（即不指定则用当前 spec，行为与改造前等价）。随后 `Preconditions.checkArgument` 校验该 specId 在 `table.specs()` 中存在——Iceberg 保留所有历史 spec，但用户可能传入一个从未存在过的 id（如测试中的 1234），此时需给出明确错误而非在后续 `table.specs().get(specId)` 返回 null 时才 NPE。
  - `outputSpec()`：从 `table.specs()` 按 id 取 `PartitionSpec`，供子类做分布/排序判定。
  - `outputSpecId()`：getter，供子类透传给 Spark write option。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`

**修改目的**：把 `OUTPUT_SPEC_ID` 加入 action 的合法选项白名单，否则用户设置该 option 会被 action 的选项校验拒绝。

**工作逻辑**：在 `VALID_OPTIONS` 这个 `ImmutableSet` 的构建中追加 `OUTPUT_SPEC_ID`：

```java
private static final Set<String> VALID_OPTIONS =
    ImmutableSet.of(
        MAX_CONCURRENT_FILE_GROUP_REWRITES,
        MAX_FILE_GROUP_SIZE_BYTES,
        PARTIAL_PROGRESS_ENABLED,
        PARTIAL_PROGRESS_MAX_COMMITS,
        TARGET_FILE_SIZE_BYTES,
        USE_STARTING_SEQUENCE_NUMBER,
        REWRITE_JOB_ORDER,
        OUTPUT_SPEC_ID);
```

注意该白名单只控制 action 层接受的选项；rewriter 自身的 `validOptions()`（如 shuffling rewriter 的 `COMPRESSION_FACTOR`、`SHUFFLE_PARTITIONS_PER_FILE`）由 rewriter 单独维护，`OUTPUT_SPEC_ID` 由基类 `SizeBasedFileRewriter` 处理而非作为 rewriter 选项，故无需在各 rewriter 的 `validOptions()` 中重复声明。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkBinPackDataRewriter.java`

**修改目的**：BINPACK 策略透传 output spec id 到写路径，并按输出 spec 判定是否需要 repartition。

**工作逻辑**：

- 在 `doRewrite` 的 `.write()` 链追加 `.option(SparkWriteOptions.OUTPUT_SPEC_ID, outputSpecId())`，使 Spark 写路径按指定 spec 写文件。
- `distributionMode(List<FileScanTask> group)` 中把判定条件从 `!group.get(0).spec().equals(table().spec())` 改为 `!group.get(0).spec().equals(outputSpec())`：

  ```java
  private DistributionMode distributionMode(List<FileScanTask> group) {
    boolean requiresRepartition = !group.get(0).spec().equals(outputSpec());
    return requiresRepartition ? DistributionMode.RANGE : DistributionMode.NONE;
  }
  ```

  语义：输入文件组的 spec（`group.get(0).spec()`，同组文件必属同一 spec）若与**目标输出 spec** 不同，则需要 `RANGE` 分布模式触发按分区列的 repartition，使输出文件落到输出 spec 的分区布局；相同则 `NONE`，无需重排。改造前是和"表当前 spec"比，改造后是和"用户指定的输出 spec"比，这是本提交的核心行为变化。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingDataRewriter.java`

**修改目的**：SORT/ZORDER 的公共基类透传 output spec id、新增 `sortSchema` 钩子、按输出 spec 构建排序顺序。

**工作逻辑**：

- 新增 import `PartitionSpec`、`Schema`。
- **新增 `sortSchema()` 钩子**：

  ```java
  /**
   * Retrieves and returns the schema for the rewrite using the current table schema.
   *
   * <p>The schema with all columns required for correctly sorting the table. This may include
   * additional computed columns which are not written to the table but are used for sorting.
   */
  protected Schema sortSchema() {
    return table().schema();
  }
  ```

  默认返回表 schema。Javadoc 明确指出该 schema 需包含"正确排序所需的所有列，可能包括不写入表但用于排序的额外计算列"——这正是为 ZORDER 的 `ICEZVALUE` 预留的扩展点。

- 在 `doRewrite` 的 `.write()` 链追加 `.option(SparkWriteOptions.OUTPUT_SPEC_ID, outputSpecId())`（与 BINPACK 一致）。

- **改造 `outputSortOrder(List<FileScanTask> group)`**：

  改造前：

  ```java
  private org.apache.iceberg.SortOrder outputSortOrder(List<FileScanTask> group) {
    boolean includePartitionColumns = !group.get(0).spec().equals(table().spec());
    if (includePartitionColumns) {
      return SortOrderUtil.buildSortOrder(table(), sortOrder());
    } else {
      return sortOrder();
    }
  }
  ```

  改造后：

  ```java
  private org.apache.iceberg.SortOrder outputSortOrder(List<FileScanTask> group) {
    PartitionSpec spec = outputSpec();
    boolean requiresRepartitioning = !group.get(0).spec().equals(spec);
    if (requiresRepartitioning) {
      return SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder());
    } else {
      return sortOrder();
    }
  }
  ```

  两处关键变化：
  1. 比较对象从 `table().spec()` 改为 `outputSpec()`——判定"输入组 spec 是否与目标输出 spec 一致"。
  2. `buildSortOrder` 从双参重载 `buildSortOrder(table(), sortOrder())`（内部用 `table.schema()` + `table.spec()`）改为三参重载 `buildSortOrder(sortSchema(), spec, sortOrder())`——显式传入输出 spec（而非表当前 spec）与可定制的 sortSchema。三参版会把 `spec` 的分区列按分区顺序前置到 `sortOrder` 之前，使 Spark 按该综合顺序 shuffle 后，数据自然按输出 spec 的分区布局聚簇，写出的文件即落在正确的输出分区。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderDataRewriter.java`

**修改目的**：覆盖 `sortSchema()`，使 ZORDER 在按输出 spec repartition 时，综合排序顺序中的 `ICEZVALUE` 计算列能被解析。

**工作逻辑**：

- 新增 import `ImmutableList`。
- 覆盖 `sortSchema()`：

  ```java
  /**
   * Overrides the sortSchema method to include columns from Z_SCHEMA.
   *
   * <p>This method generates a new Schema object which consists of columns from the original table
   * schema and Z_SCHEMA.
   */
  @Override
  protected Schema sortSchema() {
    return new Schema(
        new ImmutableList.Builder<Types.NestedField>()
            .addAll(table().schema().columns())
            .addAll(Z_SCHEMA.columns())
            .build());
  }
  ```

  `Z_SCHEMA` 是该类已有的静态字段 `new Schema(Types.NestedField.required(0, Z_COLUMN, Types.BinaryType.get()))`，即只含一个名为 `ICEZVALUE` 的二进制计算列（由 `zValue(df)` 在运行时从 Z-order 列计算而来，不写入表）。覆盖后的 `sortSchema()` 返回"表全部列 + ICEZVALUE 列"的并集 schema。这样 `SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder())` 在合并"输出 spec 的分区列（引用表真实列）+ Z_SORT_ORDER（引用 ICEZVALUE）"时，所有列引用都能在这个并集 schema 中被解析到，避免找不到列的异常。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：覆盖 BINPACK/SORT/ZORDER 三种策略下指定 output-spec-id 的行为，以及非法 specId 的校验。

**工作逻辑**：新增 5 个测试方法 + 2 个辅助方法。

1. **`testBinPackRewriterWithSpecificUnparitionedOutputSpec`**：建表（10 文件，初始未分区 specId=0），更新 spec 加 `truncate(c2,2)`（specId=1），然后用 `output-spec-id=0` + `binPack()` + `REWRITE_ALL=true` 重写。断言重写后所有文件 `specId==0` 且分区类型与 spec 0 一致（即未分区）。验证"从分区 spec 回退到未分区 spec"。

2. **`testBinPackRewriterWithSpecificOutputSpec`**：建表后连续两次更新 spec（specId=1 `truncate(c2,2)`、specId=2 `bucket(c3,2)`），用 `output-spec-id=1` + binPack 重写。断言重写后文件 `specId==1` 且分区类型匹配 spec 1。验证"指定中间某个历史 spec"。

3. **`testBinpackRewriteWithInvalidOutputSpecId`**：用不存在的 `output-spec-id=1234` 触发重写，断言抛 `IllegalArgumentException` 且消息为 `"Cannot use output spec id 1234 because the table does not contain a reference to this spec-id."`。验证 `SizeBasedFileRewriter.outputSpecId(options)` 的校验逻辑。

4. **`testSortRewriterWithSpecificOutputSpecId`**：与 2 同样的 spec 演进（输出到 specId=1），但用 `sort(SortOrder.asc("c2").asc("c3"))` 策略。验证 SORT rewriter 的 `outputSortOrder` + `buildSortOrder` 路径。

5. **`testZOrderRewriteWithSpecificOutputSpecId`**：与 2 同样的 spec 演进，用 `zOrder("c2","c3")` 策略。验证 ZORDER rewriter 的 `sortSchema()` 覆盖路径——这是本提交最关键的测试，确保 `ICEZVALUE` 计算列与输出 spec 分区列合并时能正确解析。

6. **辅助方法 `shouldRewriteDataFilesWithPartitionSpec(Table, int)`**：扫描表当前所有数据文件，断言每个文件 `specId == outputSpecId`，且 `file.partition().getPartitionType()` 等于 `table.specs().get(outputSpecId).partitionType()`（验证分区数据结构与目标 spec 一致）。

7. **辅助方法 `currentDataFiles(Table)`**：通过 `table.newScan().planFiles()` 收集当前所有 `DataFile`，供上述断言使用。

所有测试均断言 `result.rewrittenBytesCount() == dataSizeBefore`（重写不丢数据）与 `currentData().size() == count`（行数不变），确保重写到不同 spec 不影响数据完整性。

## 小结

本提交为 Spark 3.5 的 `RewriteDataFiles` action 补齐了"指定输出分区 spec"的能力。改动跨 API/core/Spark 三层、7 个文件、+194/-5 行，核心成效：

1. **新增 `output-spec-id` 选项**（API 层常量 + action 白名单 + rewriter 解析校验），默认回退到表当前 spec，向后兼容。
2. **透传到 Spark 写路径**（BINPACK 与 Shuffling rewriter 均加 `SparkWriteOptions.OUTPUT_SPEC_ID`），复用已有的 `SparkWriteConf.outputSpecId()` 基础设施。
3. **修正分布与排序判定**：从"与表当前 spec 比较"改为"与输出 spec 比较"，并在需要 repartition 时用三参 `buildSortOrder(sortSchema(), outputSpec(), sortOrder())` 构建面向输出 spec 的综合排序顺序。
4. **引入 `sortSchema()` 钩子**并由 ZORDER 子类覆盖以纳入 `ICEZVALUE` 计算列，解决 Z-order 在跨 spec repartition 时的列解析问题。

**影响范围**：仅影响 `RewriteDataFiles` action 的 Spark 3.5 实现。不影响其他版本（v3.3/v3.4 未改动）或其他 action。不改变默认行为（不指定 `output-spec-id` 时与改造前完全一致）。

**回迁到 1.4.x 的注意事项**：

- 本提交本身就是针对 Spark 3.5（1.4.x 维护的 Spark 版本之一）的功能增强，回迁目标即 v3.5 模块。
- 依赖 Spark 写路径已存在 `SparkWriteConf.outputSpecId()` / `SparkWriteOptions.OUTPUT_SPEC_ID`——回迁前需确认 1.4.x 的 Spark 3.5 写路径已具备此基础设施（本提交未新增这些，只是消费它们）。
- `SortOrderUtil.buildSortOrder(Schema, PartitionSpec, SortOrder)` 三参重载需已存在于 core 模块（本提交未新增该方法，只是改用既有重载）。
- 测试依赖 `assertj` 的 `Assertions.assertThatThrownBy`，需确认测试依赖中已引入 assertj（1.4.x 已有）。
- 若 1.4.x 同时维护 Spark 3.3/3.4 且希望该能力一致，需另行为 v3.3/v3.4 做对应回迁（本提交未覆盖这两个版本）。
