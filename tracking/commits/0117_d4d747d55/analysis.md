# 提交 0117：Flink 1.16: Remove usage of AssertHelpers (#8946)

## 提交信息

- **序号**：0117 / 4088
- **哈希**：d4d747d55f767410b71a1baf6dba742ecc194c07
- **短哈希**：d4d747d55
- **日期**：2023-10-31
- **作者**：Ashok
- **提交说明**：Flink 1.16: Remove usage of AssertHelpers (#8946)
- **PR/Issue**：#8946

## 总体目的

本提交是与 #8945（提交 0116）同一天、同一作者推进的姊妹提交，针对 `flink/v1.16/flink` 模块完成相同的"去 AssertHelpers"迁移工作：把测试代码中对自研 `org.apache.iceberg.AssertHelpers`（`assertThrows` / `assertThrowsRootCause` / `assertThrowsCause`）的调用，统一替换为 AssertJ 的 `Assertions.assertThatThrownBy(...)` 链式断言，并相应调整 import。这是 Iceberg 在各 Flink 版本模块上分批淘汰自研断言工具、统一到社区标准 AssertJ 的工作的一部分。

由于 Flink 1.16 与 1.15 的测试代码结构高度相似，本提交的改动内容与 #8945 几乎逐文件对应，差异主要来源于两个版本模块本身的代码基线不同。最显著的差异是 `TestFlinkTableSource.java`：在 v1.16 中该类只包含 `testLimitPushDown` 一个用到 `AssertHelpers` 的用例，没有 v1.15 中那 6 处针对 NaN SQL 解析的 `testSqlParseError` 用例（v1.16 基线已不含该测试），因此本提交对该文件的改动比 #8945 少 6 处。整体规模为 +183 / -248 行，比 #8945 的 +200 / -277 略小，原因即在于此。

与 #8945 一样，本提交在迁移 API 的同时也收紧了异常文案断言（改用 `hasMessage` / `hasMessageStartingWith` / `hasMessageContaining` 精确或前缀/包含匹配当前实际抛出的消息），并简化了若干 lambda（去掉多余的 `return null`、改用方法引用）。

## 如何达成设计目的

对 `flink/v1.16/flink/src/test/java` 下的 15 个测试类逐一执行机械迁移：`assertThrows` → `assertThatThrownBy(...).isInstanceOf(...).hasMessage*(...)`，`assertThrowsRootCause` → `.rootCause().isInstanceOf(...)`，`assertThrowsCause` → `.cause().isInstanceOf(...)`；移除 `import org.apache.iceberg.AssertHelpers;`，新增 `import org.assertj.core.api.Assertions;`；对原本只做子串匹配的断言，根据当前实际异常消息选择精确/前缀/包含匹配，使断言既准确又紧密贴合被测代码当前行为。

## 修改详情

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogFactory.java`

**修改目的**：迁移 `testLoadCatalogBothCatalogTypeAndImpl` 与 `testLoadCatalogUnknown` 两处断言。

**工作逻辑**：分别改为 `hasMessageStartingWith("Cannot create catalog customCatalog, both catalog-type and catalog-impl are set")` 与 `hasMessageStartingWith("Unknown catalog-type: fooType")`，把完整异常文案纳入断言。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`

**修改目的**：迁移 3 处异常断言，含 1 处 `assertThrowsRootCause`。

**工作逻辑**：v2 表降级 v1 的用例改用 `assertThatThrownBy(...).rootCause().isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot downgrade v2 table to v1")`；另两处（重命名后取原表抛 `ValidationException`、DROP 后访问抛 `NoSuchTableException`）改用 `hasMessage` 精确匹配。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`

**修改目的**：迁移未分区表列举分区应抛 `TableNotPartitionedException` 的断言。

**工作逻辑**：改用 `hasMessage("Table " + objectPath + " in catalog " + catalogName + " is not partitioned.")` 精确匹配。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkSchemaUtil.java`

**修改目的**：迁移 Flink schema 主键不支持嵌套列的异常断言。

**工作逻辑**：改为 `hasMessageStartingWith("Could not create a PRIMARY KEY")` 配合 `hasMessageContaining("Column 'struct.inner' does not exist")` 的双段断言。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`

**修改目的**：迁移两处异常断言，含 1 处 `assertThrowsCause`。

**工作逻辑**：重复建表抛 `TableException` 改用 `hasMessageStartingWith("Could not execute CreateTable in path")`；在 iceberg catalog 中用 `connector='iceberg'` 建表抛 `IllegalArgumentException`（cause），改用 `.cause().isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot create the table with 'connector'='iceberg' table property in an iceberg catalog, ...")` 把完整引导文案纳入断言。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：迁移 3 处 sink 构建异常断言并简化 lambda。

**工作逻辑**：RANGE 分布模式、无效分布模式 `UNRECOGNIZED`、无效文件格式 `UNRECOGNIZED` 三处，分别改用 `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessage(...)`，并把原本显式 `builder.append(); env.execute(...); return null;` 的 lambda 简化为方法引用 `builder::append`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`

**修改目的**：迁移 UPSERT 模式下两处 `IllegalStateException` 断言。

**工作逻辑**：把消息补全为实际抛出的完整文案：`"OVERWRITE mode shouldn't be enable when configuring to use UPSERT data stream."` 与 `"Equality field columns shouldn't be empty when configuring to use UPSERT data stream."`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`

**修改目的**：迁移两处 hash 分布模式下 equality fields 须包含所有分区键的异常断言。

**工作逻辑**：简化 lambda（去掉 `return null`），改用 `hasMessageStartingWith("In 'hash' distribution mode with equality fields set, partition field")` 与 `hasMessageContaining("should be included in equality fields:")` 的组合断言。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScan.java`

**修改目的**：迁移两处 `START_SNAPSHOT_ID` 与 `START_TAG`/`END_TAG` 互斥配置的异常断言。

**工作逻辑**：改用 `hasMessage("START_SNAPSHOT_ID and START_TAG cannot both be set.")` 与 `hasMessage("END_SNAPSHOT_ID and END_TAG cannot both be set.")` 精确匹配。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceConfig.java`

**修改目的**：迁移流式读取下设置 `as-of-timestamp` 应抛 `IllegalArgumentException` 的断言。

**工作逻辑**：简化 lambda，改用 `hasMessage("Cannot set as-of-timestamp option for streaming reader")` 精确匹配。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkTableSource.java`

**修改目的**：迁移 `testLimitPushDown` 一处异常断言。

**工作逻辑**：原 `assertThrows("Invalid limit number: -1 ", SqlParserException.class, ...)` 改为 `assertThatThrownBy(...).as("Invalid limit number: -1 ").isInstanceOf(SqlParserException.class).hasMessageContaining("SQL parse failed. Encountered \"-\"")`，把断言收紧到具体解析错误。

**与 v1.15 的差异**：v1.16 基线的 `TestFlinkTableSource.java` 不包含 v1.15 中那 6 处针对 NaN SQL 解析的 `testSqlParseError` 用例，因此本提交对该文件只有 1 处改动，而 #8945 有 7 处。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`

**修改目的**：迁移两处流式读取配置异常断言。

**工作逻辑**：流式读取不支持 branch ref 改为 `hasMessage("Cannot scan table using ref b1 configured for streaming reader yet")`；`START_SNAPSHOT_ID` 与 `START_TAG` 互斥改为 `hasMessage("START_SNAPSHOT_ID and START_TAG cannot both be set.")`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`

**修改目的**：迁移两处 `max-planning-snapshot-count` 非法值（0 与 -10）的异常断言。

**工作逻辑**：简化 lambda 为 `() -> createFunction(scanContext1)`，统一断言 `"The max-planning-snapshot-count must be greater than zero"`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java`

**修改目的**：迁移 4 处连续切片规划器非法起始 snapshot id / timestamp 断言。

**工作逻辑**：3 处 snapshot id 断言改用 `hasMessage("Start snapshot id not found in history: ...")`；1 处 timestamp 断言改用 `hasMessage("Cannot find a snapshot after: 1")`（精确）与 `hasMessageStartingWith("Cannot find a snapshot after: ")`（前缀），分两种用例对齐实际文案。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImplStartStrategy.java`

**修改目的**：迁移 2 处起始 snapshot 非法 id / timestamp 断言，并修正注释拼写错误。

**工作逻辑**：4 处注释 `// emtpy table` 统一改为 `// empty table`；非法 snapshot id 断言改为 `hasMessage("Start snapshot id not found in history: 1")`；非法 timestamp 断言改为 `hasMessageStartingWith("Cannot find a snapshot after: ")`。

## 小结

本提交是 #8945 在 Flink 1.16 模块上的对应落地，将 v1.16 测试从 `AssertHelpers` 迁移到 AssertJ 链式断言并收紧异常文案，是 Iceberg 统一断言风格、逐步淘汰 `AssertHelpers` 工作的继续。
