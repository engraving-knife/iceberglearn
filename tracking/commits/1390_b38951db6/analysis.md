# 提交 1390：Data, Flink, MR, Spark: Test deletes with format-version=3 (#11538)

## 提交信息

- **序号**：1390 / 4088
- **哈希**：b38951db6a7061a595605229c21c1a1912a3a4c1
- **短哈希**：b38951db6
- **日期**：2024-11-17（Sun Nov 17 17:20:20 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Data, Flink, MR, Spark: Test deletes with format-version=3
- **PR/Issue**：#11538

## 总体目的

Iceberg 表格式 v3 引入了 DV（Deletion Vector，删除向量）作为位置删除的新载体：DV 以 Puffin 文件格式存储 Roaring 位图，按数据文件粒度（1:1）绑定，相比传统 v2 的 Parquet 位置删除文件更紧凑、读取开销更小。此前 DV 的写入与读取基础设施已在 Core 层落地（如 `BaseDVFileWriter`、`BaseDeleteLoader` 支持 Puffin、`DeleteFileIndex` 支持 DV 索引等），测试辅助工具也已就绪（`FileHelpers.writeDeleteFile` 在 `formatVersion >= 3` 时写 DV、`FileGenerationUtil.generateDV` 生成 DV 元数据、`TestBase.newDeletes` 按版本选择 DV 或位置删除）。

但跨引擎的删除读取测试（`DeleteReadTests` 抽象基类及其在 Data/Flink/MR/Spark 各模块的子类）此前仅在 `format-version=2` 下运行，未覆盖 v3 的 DV 读取路径。本提交的目标是**将这些既有删除读取测试扩展到 `format-version=3`**，验证各引擎读取 DV 删除的数据时行为正确，确保 DV 功能端到端可用。

这与此前提交 1373（`Core, Flink, Spark: Test DVs with format-version=3`）形成互补：1373 主要覆盖 Core 层的 DV 测试与 Flink/Spark 部分场景，本提交则系统性地将 `DeleteReadTests` 共享测试基类的全部子类（Generic reader、Flink 1.18/1.19/1.20、MR InputFormat、Spark 3.3/3.4/3.5）统一扩展到 v3。

## 如何达成设计目的

核心思路是**在 `DeleteReadTests` 抽象基类中引入 `formatVersion` 参数**，让所有继承它的子类自动获得 v3 测试覆盖，具体步骤：

1. **参数化 `formatVersion`**：在 `DeleteReadTests` 中新增 `@Parameter(index = 1) protected int formatVersion`，并在 `@Parameters` 中增加 `{FileFormat.PARQUET, 3}` 组合（v3 仅与 Parquet 搭配，因为 DV 基础设施面向 Parquet 数据文件）。

2. **透传 `formatVersion` 到删除文件写入**：所有 `FileHelpers.writeDeleteFile` 调用追加 `formatVersion` 参数。该重载方法在 `formatVersion >= 3` 时使用 `BaseDVFileWriter`（Puffin 格式）写入 DV，否则使用传统 `PositionDeleteWriter`（Parquet 格式）写入位置删除。

3. **透传 `formatVersion` 到建表逻辑**：各子类的 `createTable` 方法将硬编码的 `upgradeToFormatVersion(2)` 改为 `upgradeToFormatVersion(formatVersion)`，并在 Spark 中额外设置 `TableProperties.FORMAT_VERSION` 属性，确保表以 v3 格式创建。

4. **跳 v3 不兼容的测试**：`testMultiplePosDeleteFiles` 在 v3 下不适用（DV 与数据文件 1:1 绑定，不能为同一数据文件写多个删除文件），用 `assumeThat(formatVersion).isEqualTo(2)` 跳过。

5. **统一元数据表测试的删除文件生成**：`TestMetadataTableFilters` 中原先有本地 `posDelete` 方法按版本选择 DV 或位置删除，现替换为继承自 `TestBase` 的 `newDeletes` 方法（逻辑相同），消除重复代码。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/DeleteReadTests.java`（修改，核心改动）

**修改目的**：在共享删除读取测试基类中引入 `formatVersion` 参数，使所有子类自动获得 v3 测试覆盖。

**工作逻辑**：

1. **新增参数字段与参数集**：
   ```java
   @Parameter(index = 1)
   protected int formatVersion;

   @Parameters(name = "fileFormat = {0}")
   public static Object[][] parameters() {
     return new Object[][] {
       new Object[] {FileFormat.PARQUET, 2},
       new Object[] {FileFormat.AVRO, 2},
       new Object[] {FileFormat.ORC, 2},
       new Object[] {FileFormat.PARQUET, 3},
     };
   }
   ```
   注意 v3 仅与 PARQUET 搭配——AVRO/ORC 的 v3 组合未加入，因为 DV 读取测试主要面向 Parquet 数据文件。

2. **透传 `formatVersion` 到 `FileHelpers.writeDeleteFile`**：所有原本 4 参数的 `writeDeleteFile(table, out, partition, deletes)` 调用改为 5 参数 `writeDeleteFile(table, out, partition, deletes, formatVersion)`。该重载在 v3 时写 DV（Puffin），v2 时写位置删除（Parquet）。

3. **跳过 v3 不兼容的 `testMultiplePosDeleteFiles`**：
   ```java
   @TestTemplate
   public void testMultiplePosDeleteFiles() throws IOException {
     assumeThat(formatVersion)
         .as("Can't write multiple delete files with formatVersion >= 3")
         .isEqualTo(2);
     // ... 原有测试逻辑
   }
   ```
   原因：v3 的 DV 与数据文件是 1:1 绑定关系，一个数据文件只能有一个 DV，不支持为同一数据文件写多个删除文件。该测试验证的是"多个位置删除文件合并读取"的行为，在 v3 下语义不成立。

### `data/src/test/java/org/apache/iceberg/data/TestGenericReaderDeletes.java`（修改，1 行）

**修改目的**：Generic reader 子类使用参数化的 `formatVersion` 建表。

**工作逻辑**：
```java
-    return TestTables.create(tableDir, name, schema, spec, 2);
+    return TestTables.create(tableDir, name, schema, spec, formatVersion);
```
将硬编码的 `2` 替换为继承自 `DeleteReadTests` 的 `formatVersion` 字段。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkReaderDeletesBase.java`（修改，1 行）

**修改目的**：Flink 1.18 子类使用参数化的 `formatVersion` 升级表格式。

**工作逻辑**：
```java
-    ops.commit(meta, meta.upgradeToFormatVersion(2));
+    ops.commit(meta, meta.upgradeToFormatVersion(formatVersion));
```

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkReaderDeletesBase.java`（修改，1 行）

**修改目的**：Flink 1.19 子类，与 v1.18 相同改动。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkReaderDeletesBase.java`（修改，1 行）

**修改目的**：Flink 1.20 子类，与 v1.18 相同改动。

### `mr/src/test/java/org/apache/iceberg/mr/TestInputFormatReaderDeletes.java`（修改）

**修改目的**：MR InputFormat 子类引入 `formatVersion` 参数并增加 v3 测试用例。

**工作逻辑**：

1. **参数索引调整与新增 v3 用例**：
   ```java
   -  @Parameter(index = 1)
   +  @Parameter(index = 2)
     private String inputFormat;

   -  @Parameters(name = "fileFormat = {0}, inputFormat = {1}")
   +  @Parameters(name = "fileFormat = {0}, formatVersion = {1}, inputFormat = {2}")
     public static Object[][] parameters() {
       return new Object[][] {
         {FileFormat.PARQUET, 2, "IcebergInputFormat"},
         {FileFormat.AVRO, 2, "IcebergInputFormat"},
         {FileFormat.ORC, 2, "IcebergInputFormat"},
         {FileFormat.PARQUET, 2, "MapredIcebergInputFormat"},
         {FileFormat.AVRO, 2, "MapredIcebergInputFormat"},
         {FileFormat.ORC, 2, "MapredIcebergInputFormat"},
         {FileFormat.PARQUET, 3, "IcebergInputFormat"},
         {FileFormat.PARQUET, 3, "MapredIcebergInputFormat"},
       };
     }
   ```
   `inputFormat` 参数从 `index = 1` 移到 `index = 2`，为 `formatVersion`（继承自 `DeleteReadTests` 的 `index = 1`）让位。v3 仅与 PARQUET + 两种 InputFormat 搭配。

2. **建表使用 `formatVersion`**：
   ```java
   -    ops.commit(meta, meta.upgradeToFormatVersion(2));
   +    ops.commit(meta, meta.upgradeToFormatVersion(formatVersion));
   ```

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（修改）

**修改目的**：Spark 3.3 子类引入 `formatVersion` 和 `planningMode` 参数并增加 v3 测试用例。

**工作逻辑**：

1. **新增 `PlanningMode` import 与参数**：
   ```java
   +  @Parameter(index = 3)
   +  private PlanningMode planningMode;
   ```
   v3.3 此前没有 `planningMode` 参数（v3.4/v3.5 已有），本提交顺带补齐，使三个 Spark 版本的参数结构一致。

2. **参数集调整**：
   ```java
   -  @Parameters(name = "format = {0}, vectorized = {1}")
   +  @Parameters(name = "fileFormat = {0}, formatVersion = {1}, vectorized = {2}, planningMode = {3}")
     public static Object[][] parameters() {
       return new Object[][] {
         new Object[] {FileFormat.PARQUET, 2, false, PlanningMode.DISTRIBUTED},
         new Object[] {FileFormat.PARQUET, 2, true, PlanningMode.LOCAL},
         new Object[] {FileFormat.ORC, 2, false, PlanningMode.DISTRIBUTED},
         new Object[] {FileFormat.AVRO, 2, false, PlanningMode.LOCAL},
         new Object[] {FileFormat.PARQUET, 3, false, PlanningMode.DISTRIBUTED},
         new Object[] {FileFormat.PARQUET, 3, true, PlanningMode.LOCAL},
       };
     }
   ```
   v3 测试用例覆盖非向量化（`false`）+ 分布式规划、向量化（`true`）+ 本地规划两种组合。

3. **建表设置 `planningMode` 与 `FORMAT_VERSION` 属性**：
   ```java
   -    table.updateProperties().set(TableProperties.DEFAULT_FILE_FORMAT, format.name()).commit();
   +    table
   +        .updateProperties()
   +        .set(TableProperties.DEFAULT_FILE_FORMAT, format.name())
   +        .set(TableProperties.DATA_PLANNING_MODE, planningMode.modeName())
   +        .set(TableProperties.DELETE_PLANNING_MODE, planningMode.modeName())
   +        .set(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion))
   +        .commit();
   ```
   注意 v3.3 保留了硬编码的 `ops.commit(meta, meta.upgradeToFormatVersion(2))`，但通过 `updateProperties` 设置 `FORMAT_VERSION` 属性也会触发格式版本升级，二者配合生效。

4. **透传 `formatVersion` 到 `FileHelpers.writeDeleteFile`**：与 `DeleteReadTests` 中的改动一致，所有 `writeDeleteFile` 调用追加 `formatVersion` 参数。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（修改）

**修改目的**：Spark 3.4 子类引入 `formatVersion` 参数并增加 v3 测试用例。

**工作逻辑**：

1. **参数索引调整**：`vectorized` 从 `index = 1` 移到 `index = 2`，`planningMode` 从 `index = 2` 移到 `index = 3`，为 `formatVersion`（`index = 1`）让位。

2. **参数集增加 v3 用例**：与 v3.3 相同的 `{PARQUET, 3, false, DISTRIBUTED}` 和 `{PARQUET, 3, true, LOCAL}`。

3. **建表增加 `FORMAT_VERSION` 属性**：
   ```java
   +        .set(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion))
   ```

4. **透传 `formatVersion` 到 `FileHelpers.writeDeleteFile`**。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（修改）

**修改目的**：Spark 3.5 子类引入 `formatVersion` 参数并增加 v3 测试用例。

**工作逻辑**：

1. **参数索引调整**：与 v3.4 相同。

2. **参数集增加 v3 用例**：与 v3.4 相同。

3. **建表使用 `formatVersion` 升级格式版本**（与 v3.3/v3.4 略有不同）：
   ```java
   -    ops.commit(meta, meta.upgradeToFormatVersion(2));
   +    ops.commit(meta, meta.upgradeToFormatVersion(formatVersion));
   ```
   v3.5 直接将 `upgradeToFormatVersion` 参数化为 `formatVersion`，而 v3.3/v3.4 保留硬编码 `2` 再通过属性设置。两种方式效果一致，但 v3.5 的写法更直接。

4. **建表增加 `FORMAT_VERSION` 属性**：与 v3.4 相同。

5. **透传 `formatVersion` 到 `FileHelpers.writeDeleteFile`**。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java`（修改）

**修改目的**：消除本地 `posDelete` 辅助方法，改用继承自 `TestBase` 的 `newDeletes`。

**工作逻辑**：

1. **删除本地 `posDelete` 方法**：
   ```java
   -  private DeleteFile posDelete(Table table, DataFile dataFile) {
   -    return formatVersion >= 3
   -        ? FileGenerationUtil.generateDV(table, dataFile)
   -        : FileGenerationUtil.generatePositionDeleteFile(table, dataFile);
   -  }
   ```
   该方法逻辑与 `TestBase.newDeletes(DataFile)` 完全相同（按 `formatVersion` 选择生成 DV 或位置删除文件），属于重复代码。

2. **替换调用点**：两处 `posDelete(table, data10)` / `posDelete(table, data11)` 改为 `newDeletes(data10)` / `newDeletes(data11)`。`newDeletes` 继承自 `TestBase`（commit 1367 引入），签名 `protected DeleteFile newDeletes(DataFile dataFile)`，无需传 `table`（因为 `TestBase` 持有 `table` 字段）。

## 小结

- **成效**：将跨 4 个引擎模块（Data/Generic、Flink 1.18-1.20、MR、Spark 3.3-3.5）的删除读取测试统一扩展到 `format-version=3`，验证各引擎在读取 DV 删除的数据时行为正确。测试参数集从"3 种文件格式 × v2"扩展为"3 种文件格式 × v2 + Parquet × v3"，覆盖了 DV 与传统位置删除两条路径。同时消除了 `TestMetadataTableFilters` 中与 `TestBase` 重复的 `posDelete` 方法，并为 Spark 3.3 补齐了 `planningMode` 参数使其与 v3.4/v3.5 对齐。

- **影响范围**：仅测试代码变更（10 个文件），不涉及任何生产代码。依赖此前已落地的 DV 基础设施：`FileHelpers.writeDeleteFile` 的 `formatVersion` 重载（commit 1373 引入）、`TestBase.newDeletes`（commit 1367 引入）、`FileGenerationUtil.generateDV`、`BaseDVFileWriter`、`BaseDeleteLoader` 的 DV 读取支持、`DeleteFileIndex` 的 DV 索引支持等。

- **回迁到 1.4.x 的注意事项**：
  1. **前置依赖**：需确保 1.4.x 分支已合入以下 DV 基础设施：
     - `FileHelpers.writeDeleteFile(Table, OutputFile, StructLike, List, int formatVersion)` 重载（commit 1373）；
     - `TestBase.newDeletes(DataFile)` 方法（commit 1367）；
     - `FileGenerationUtil.generateDV` 方法；
     - DV 写入与读取链路（`BaseDVFileWriter`、`BaseDeleteLoader` 支持 Puffin、`DeleteFileIndex` 支持 DV）。
  2. **v3 仅测 Parquet**：v3 测试用例只与 `FileFormat.PARQUET` 搭配，回迁时不要误加 AVRO/ORC 的 v3 组合，否则可能因 DV 基础设施不完整而失败。
  3. **`testMultiplePosDeleteFiles` 跳过逻辑**：v3 下该测试被 `assumeThat` 跳过，回迁时需确保 AssertJ 的 `Assumptions.assumeThat` 可用（注意 import 是 `org.assertj.core.api.Assumptions.assumeThat`，非 JUnit 的 `Assume.assumeTrue`）。
  4. **Spark 3.3 的 `planningMode`**：本提交为 v3.3 新增了 `planningMode` 参数，回迁时需确认 1.4.x 的 v3.3 模块是否已有 `PlanningMode` 枚举与 `TableProperties.DATA_PLANNING_MODE` / `DELETE_PLANNING_MODE` 属性支持。
  5. **v3.3/v3.4 与 v3.5 的建表差异**：v3.3/v3.4 保留 `upgradeToFormatVersion(2)` 再通过 `FORMAT_VERSION` 属性升级，v3.5 直接 `upgradeToFormatVersion(formatVersion)`。回迁时保持与原提交一致即可，两种方式功能等价。
