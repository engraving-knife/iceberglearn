# 提交 0116：Flink 1.15: Remove usage of AssertHelpers (#8945)

## 提交信息

- **序号**：0116 / 4088
- **哈希**：eea5547462507727156678c153675e8595a38526
- **短哈希**：eea554746
- **日期**：2023-10-31
- **作者**：Ashok
- **提交说明**：Flink 1.15: Remove usage of AssertHelpers (#8945)
- **PR/Issue**：#8945

## 总体目的

Iceberg 在测试代码中长期使用自研的工具类 `org.apache.iceberg.AssertHelpers`（提供 `assertThrows` / `assertThrowsRootCause` / `assertThrowsCause` 等方法）来断言被测代码会抛出特定异常。这种自定义断言 API 在功能上与社区主流的 AssertJ（`Assertions.assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)`）完全重叠，但又缺少 AssertJ 丰富的链式断言能力（如 `hasMessageContaining`、`hasMessageStartingWith`、`cause()`、`rootCause()` 等），同时维护两套断言风格会增加阅读与维护成本。

本提交针对 `flink/v1.15/flink` 模块下的所有测试类，将 `AssertHelpers` 的调用统一替换为 AssertJ 的 `Assertions.assertThatThrownBy(...)` 链式断言，并相应移除 `import org.apache.iceberg.AssertHelpers;`、新增 `import org.assertj.core.api.Assertions;`。这是 Iceberg 在多个 Flink 版本模块上分批推进的"去 AssertHelpers"工作的一部分（与 #8946 等并行），目标是逐步淘汰自研断言工具，统一到 AssertJ，为后续彻底删除 `AssertHelpers` 类铺路。

值得注意的是，本次替换不仅仅是机械地改写 API，还顺带修正了一些断言文案，使其与被测代码当前实际抛出的异常消息精确对齐（例如把模糊的 `"both catalog-type and catalog-impl are set"` 改为完整的 `"Cannot create catalog customCatalog, both catalog-type and catalog-impl are set"`，把 `"OVERWRITE mode shouldn't be enable"` 改为 `"OVERWRITE mode shouldn't be enable when configuring to use UPSERT data stream."` 等），并修正了若干注释中的拼写错误（`emtpy table` → `empty table`）。

## 如何达成设计目的

整体思路是机械迁移 + 文案校正：对 `flink/v1.15/flink/src/test/java` 下的 15 个测试类逐一替换 `AssertHelpers.assertThrows*` 调用为 `Assertions.assertThatThrownBy(...).isInstanceOf(...).hasMessage*(...)`，对应地把 `assertThrowsRootCause` 映射为 `.rootCause().isInstanceOf(...)`，`assertThrowsCause` 映射为 `.cause().isInstanceOf(...)`。对于原先用 `"子串"` 匹配的断言，根据当前实际异常消息选择 `hasMessage`（精确）、`hasMessageStartingWith`（前缀）或 `hasMessageContaining`（包含），从而既保留了断言强度又提升了准确性。改动总规模为 +200 / -277 行，呈净减少，说明新写法更紧凑。

## 修改详情

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogFactory.java`

**修改目的**：移除 `AssertHelpers` import，将两处 `assertThrows` 改为 `Assertions.assertThatThrownBy`，并修正断言文案。

**工作逻辑**：
- `testLoadCatalogBothCatalogTypeAndImpl`：原断言只匹配 `"both catalog-type and catalog-impl are set"`，新写法改为 `hasMessageStartingWith("Cannot create catalog customCatalog, both catalog-type and catalog-impl are set")`，完整对齐实际异常消息。
- `testLoadCatalogUnknown`：原断言匹配 `"Unknown catalog-type"`，新写法改为 `hasMessageStartingWith("Unknown catalog-type: fooType")`，把具体未知类型也纳入断言。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`

**修改目的**：将 3 处异常断言迁移到 AssertJ，其中一处涉及 `assertThrowsRootCause`。

**工作逻辑**：
- 重命名表后取原表应抛 `ValidationException`，消息 `"Table \`tl\` was not found."` 改用 `hasMessage` 精确匹配。
- DROP 后访问表应抛 `NoSuchTableException`，消息精确匹配。
- 测试 v2 表不能降级到 v1 时，原 `assertThrowsRootCause(... IllegalArgumentException.class ...)` 改写为 `assertThatThrownBy(...).rootCause().isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot downgrade v2 table to v1")`，利用 AssertJ 的 `rootCause()` 链式方法保留对根因异常的断言能力。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`

**修改目的**：迁移对未分区表列举分区应抛 `TableNotPartitionedException` 的断言。

**工作逻辑**：新写法使用 `hasMessage("Table " + objectPath + " in catalog " + catalogName + " is not partitioned.")` 精确匹配异常消息。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestFlinkSchemaUtil.java`

**修改目的**：迁移对 Flink schema 主键不支持嵌套列的异常断言。

**工作逻辑**：原断言只匹配 `"Column 'struct.inner' does not exist"`，新写法改用 `.hasMessageStartingWith("Could not create a PRIMARY KEY")` 再 `.hasMessageContaining("Column 'struct.inner' does not exist")`，对异常消息做了更全面的双段断言。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`

**修改目的**：迁移两处异常断言，其中一处涉及 `assertThrowsCause`。

**工作逻辑**：
- `testCreateConnectorTable` 中重复建表抛 `TableException`，改用 `hasMessageStartingWith("Could not execute CreateTable in path")`。
- 在 iceberg catalog 中用 `connector='iceberg'` 建表应抛 `IllegalArgumentException`（作为 cause），原 `assertThrowsCause` 改写为 `assertThatThrownBy(...).cause().isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot create the table with 'connector'='iceberg' table property in an iceberg catalog, Please create table with 'connector'='iceberg' property in a non-iceberg catalog or create table without 'connector'='iceberg' related properties in an iceberg table.")`，把完整的引导提示文案纳入断言。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：迁移 3 处 sink 构建异常断言，并简化 lambda 写法。

**工作逻辑**：
- RANGE 分布模式不支持：原 lambda 包裹 `testWriteRow(null, DistributionMode.RANGE)` 并 `return null`，新写法直接 `assertThatThrownBy(() -> testWriteRow(null, DistributionMode.RANGE))`，并精确断言 `"Flink does not support 'range' write distribution mode now."`。
- 无效分布模式 `UNRECOGNIZED`：原 lambda 显式调用 `builder.append(); env.execute(...)` 并 `return null`，新写法简化为方法引用 `builder::append`，断言消息精确为 `"Invalid distribution mode: UNRECOGNIZED"`。
- 无效文件格式 `UNRECOGNIZED`：同上简化为 `builder::append`，断言 `"Invalid file format: UNRECOGNIZED"`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`

**修改目的**：迁移 UPSERT 模式下的两处 `IllegalStateException` 断言。

**工作逻辑**：把两条 `assertThrows` 改写为 `assertThatThrownBy`，并把过短的消息补全为实际抛出的完整消息：`"OVERWRITE mode shouldn't be enable when configuring to use UPSERT data stream."` 与 `"Equality field columns shouldn't be empty when configuring to use UPSERT data stream."`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`

**修改目的**：迁移两处"hash 分布模式下 equality fields 必须包含所有分区键"的异常断言。

**工作逻辑**：把原先用 `return null` 包裹的 lambda 简化为直接调用 `testChangeLogs(...)`，并改用 `hasMessageStartingWith("In 'hash' distribution mode with equality fields set, partition field")` 与 `hasMessageContaining("should be included in equality fields:")` 的组合断言，覆盖更完整的异常文案。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScan.java`

**修改目的**：迁移两处 `START_SNAPSHOT_ID` 与 `START_TAG`/`END_TAG` 互斥配置的异常断言。

**工作逻辑**：把 `assertThrows(... Exception.class ...)` 改为 `assertThatThrownBy(...).isInstanceOf(Exception.class).hasMessage(...)`，分别精确断言 `"START_SNAPSHOT_ID and START_TAG cannot both be set."` 与 `"END_SNAPSHOT_ID and END_TAG cannot both be set."`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceConfig.java`

**修改目的**：迁移流式读取下设置 `as-of-timestamp` 应抛 `IllegalArgumentException` 的断言。

**工作逻辑**：简化 lambda（去掉 `return null`），断言消息精确为 `"Cannot set as-of-timestamp option for streaming reader"`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkTableSource.java`

**修改目的**：迁移 1 处 LIMIT 解析异常与 6 处 NaN SQL 解析异常断言。

**工作逻辑**：
- `testLimitPushDown`：原 `assertThrows(... SqlParserException.class ...)` 无消息断言，新写法补充 `.as("Invalid limit number: -1 ")` 描述并 `.hasMessageContaining("SQL parse failed. Encountered \"-\"")`，把断言收紧到具体解析错误。
- `testSqlParseError`：6 处 `=`/`<>`/`>`/`<`/`>=`/`<=` 对 `NaN` 的 SQL 比较均应抛 `NumberFormatException`，统一改为 `hasMessageContaining("Infinite or NaN")`，比原先无消息断言更严格。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`

**修改目的**：迁移两处流式读取配置异常断言。

**工作逻辑**：
- 流式读取不支持 branch ref：原匹配 `"Cannot scan table using ref"`，改为 `hasMessage("Cannot scan table using ref b1 configured for streaming reader yet")`，把具体 branch 名纳入断言。
- `START_SNAPSHOT_ID` 与 `START_TAG` 互斥：改为 `hasMessage("START_SNAPSHOT_ID and START_TAG cannot both be set.")` 精确匹配。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`

**修改目的**：迁移两处 `max-planning-snapshot-count` 非法值的异常断言。

**工作逻辑**：原 lambda 显式 `createFunction(scanContext1); return null;`，简化为 `() -> createFunction(scanContext1)`，并对 0 和 -10 两种非法值统一断言 `"The max-planning-snapshot-count must be greater than zero"`。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java`

**修改目的**：迁移 4 处连续切片规划器的非法起始 snapshot id / timestamp 断言。

**工作逻辑**：
- 3 处 `assertThrows` 改为 `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessage("Start snapshot id not found in history: ...")`，分别匹配 1 与 `invalidSnapshotId`。
- 1 处 timestamp 断言原匹配 `"Cannot find a snapshot after: "` 子串，新写法改为 `hasMessage("Cannot find a snapshot after: 1")`（精确）与 `hasMessageStartingWith("Cannot find a snapshot after: ")`（前缀），分两种用例对齐实际文案。

### `flink/v1.15/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImplStartStrategy.java`

**修改目的**：迁移 2 处起始 snapshot 非法 id / timestamp 断言，并修正注释拼写错误。

**工作逻辑**：
- 4 处注释 `// emtpy table` 统一改为 `// empty table`。
- 非法 snapshot id 断言改为 `hasMessage("Start snapshot id not found in history: 1")`。
- 非法 timestamp 断言改为 `hasMessageStartingWith("Cannot find a snapshot after: ")`。

## 小结

本提交将 Flink 1.15 模块全部测试从自研 `AssertHelpers` 迁移到社区标准的 AssertJ 链式断言，并顺带收紧异常文案断言、修正注释拼写，是 Iceberg 统一断言风格、逐步淘汰 `AssertHelpers` 工作在 Flink 1.15 上的落地。
