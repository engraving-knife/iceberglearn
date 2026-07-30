# 提交 1548 c0d6d42a5 分析

## 提交信息
- 哈希：c0d6d42a55a7d33d7d38f73d00e46e86b57d934e
- 日期：2025-01-03（Fri Jan 3 09:40:04 2025 -0700）
- 作者：Amogh Jahagirdar <amoghj@apache.org>
- 消息：Spark: Change delete file granularity to file in Spark 3.5 (#11478)

## 总体目的

将 Spark 3.5 模块中 delete file（删除文件）的默认粒度从 PARTITION（分区级）改为 FILE（文件级），并重构配置解析代码使用 `enumConf` 替代手动的 `stringConf` + `fromString` 模式。

Iceberg 的 Merge-on-Read（MoR）表在执行 DELETE/UPDATE/MERGE 操作时会产生位置删除文件（position delete files），用于标记被删除行的位置。删除文件的粒度决定了删除文件的组织方式：
- **PARTITION 粒度**：每个分区一个删除文件，包含该分区内所有被删除行的位置。删除文件数量少但单个文件较大。
- **FILE 粒度**：每个数据文件一个删除文件，仅包含对应数据文件中被删除行的位置。删除文件数量多但单个文件较小。

将默认值改为 FILE 粒度有以下优势：在读取时，每个数据文件只需加载对应的删除文件，减少了不必要的数据扫描；对于点查和少量删除的场景，FILE 粒度更高效；同时也减少了删除文件对查询计划的影响。PARTITION 粒度在大量删除的场景下可能产生更少的文件，但默认采用 FILE 粒度对大多数工作负载更合理。

此外，代码重构使用 `enumConf(DeleteGranularity::fromString)` 替代 `stringConf()` + `DeleteGranularity.fromString()`，利用配置解析器内置的枚举支持，提供更好的类型安全和错误提示。

## 如何达成设计目的

修改 `SparkWriteConf.deleteGranularity()` 方法的实现，将默认值从 `TableProperties.DELETE_GRANULARITY_DEFAULT`（PARTITION）改为 `DeleteGranularity.FILE`，并改用 `enumConf` 解析。同时更新所有相关测试，在需要 PARTITION 粒度的测试中显式设置该属性，并调整默认行为的断言。

### 修改详情

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java`

**修改目的**：将 delete granularity 默认值改为 FILE，并重构配置解析。

**工作逻辑**：原实现：
```java
String valueAsString = confParser.stringConf()
    .option(SparkWriteOptions.DELETE_GRANULARITY)
    .tableProperty(TableProperties.DELETE_GRANULARITY)
    .defaultValue(TableProperties.DELETE_GRANULARITY_DEFAULT)
    .parse();
return DeleteGranularity.fromString(valueAsString);
```
新实现：
```java
return confParser.enumConf(DeleteGranularity::fromString)
    .option(SparkWriteOptions.DELETE_GRANULARITY)
    .tableProperty(TableProperties.DELETE_GRANULARITY)
    .defaultValue(DeleteGranularity.FILE)
    .parse();
```

两处关键改动：
1. **默认值**：从 `TableProperties.DELETE_GRANULARITY_DEFAULT`（字符串 "partition"）改为 `DeleteGranularity.FILE` 枚举值。当 Spark session 配置和表属性均未设置时，默认使用 FILE 粒度。
2. **解析方式**：从 `stringConf()` + 手动 `fromString` 改为 `enumConf(DeleteGranularity::fromString)`，利用解析器内置的枚举验证，对非法值提供更清晰的错误信息。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：在需要 PARTITION 粒度的测试中显式设置属性，简化断言逻辑。

**工作逻辑**：
- 在 `testScanWithMultipleShufflesAndFilterPushDown` 相关的表属性中新增 `DELETE_GRANULARITY=PARTITION`，因为该测试依赖分区级删除文件来验证特定的数据布局。
- 简化 `validateMergeOnRead` 的断言：移除 `formatVersion >= 3` 与 `< 3` 的分支区分，统一为 `validateMergeOnRead(currentSnapshot, "3", "4", null)`。因为默认粒度变为 FILE 后，不再需要按 formatVersion 区分删除文件数量。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：解除 formatVersion < 3 的限制，显式设置 PARTITION 粒度，新增 MoR v3 的断言。

**工作逻辑**：
- 移除 `testCoalesceMerge` 中的 `assumeThat(formatVersion).isLessThan(3)` 限制，使测试在 formatVersion 3 上也能运行。
- 在表属性中新增 `DELETE_GRANULARITY=PARTITION`，确保合并测试使用分区级删除。
- 新增 MoR formatVersion >= 3 场景的断言：验证 `ADDED_DELETE_FILES_PROP` 为 "4"、`ADDED_DVS_PROP` 为 "4"。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java`

**修改目的**：在建表时显式设置 PARTITION 粒度。

**工作逻辑**：在 `CREATE TABLE` 的 TBLPROPERTIES 中新增 `'write.delete.granularity'='partition'`，确保位置删除文件重写过程测试使用分区级粒度。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java`

**修改目的**：与 TestDelete 类似，显式设置 PARTITION 粒度并简化断言。

**工作逻辑**：在表属性中新增 `DELETE_GRANULARITY=PARTITION`；简化 `validateMergeOnRead` 断言，移除 formatVersion 分支区分，统一为 `validateMergeOnRead(currentSnapshot, "2", "3", "2")`。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`

**修改目的**：适配默认值变更。

**工作逻辑**：两个测试用例的期望值对调：
- 默认场景（不设置属性）：期望从 `PARTITION` 改为 `FILE`。
- 显式设置场景：从设置 `FILE` 改为设置 `PARTITION`，期望相应从 `FILE` 改为 `PARTITION`。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`

**修改目的**：在建表属性中显式设置 PARTITION 粒度。

**工作逻辑**：在 `createTable` 方法的表属性中新增 `DELETE_GRANULARITY=PARTITION`，确保重写位置删除文件的行为测试使用分区级粒度。

## 小结

- **成效**：将 Spark 3.5 的删除文件默认粒度从 PARTITION 改为 FILE，使默认行为更适合大多数工作负载（减少读取时扫描的删除文件数据量）；同时通过 `enumConf` 重构提升了配置解析的类型安全和错误提示。
- **影响范围**：涉及 Spark 3.5 模块的 7 个文件（1 个源码 + 6 个测试），核心改动在 `SparkWriteConf`，其余为测试适配。这是一个行为变更——已有表如果未显式设置 `write.delete.granularity`，升级后删除文件的默认粒度会从 PARTITION 变为 FILE。
- **回迁到 1.4.x 的注意事项**：这是默认行为变更，属于功能调整而非纯 bug 修复。1.4.x 作为维护分支通常不引入默认行为变更，**一般不回迁**。如果 1.4.x 的 Spark 3.5 模块有特殊需求（如用户反馈 PARTITION 粒度的性能问题），可考虑回迁，但需评估对现有用户的影响。
