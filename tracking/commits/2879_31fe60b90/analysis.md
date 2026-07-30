# 提交 2879：Spark: Custom snapshot property from session configuration (#14545)

## 提交信息

- **序号**：2879 / 4088
- **哈希**：31fe60b9036718bb805d9ca86906cbd2bc6a4a19
- **短哈希**：31fe60b90
- **日期**：2025-11-16 22:42:52 -0800
- **作者**：Owen Zhang
- **提交说明**：Spark: Custom snapshot property from session configuration (#14545)
- **PR/Issue**：#14545

## 总体目的

Iceberg 的快照（snapshot）支持在提交时附加自定义元数据属性（snapshot properties），这些属性会记录在快照的 summary 中，用于审计、追踪和元数据管理。此前，Spark 用户只能通过 DataFrameWriter 的 write options（即 `tblproperties` 中的 `snapshot-property.*` 前缀选项）来设置快照属性，这要求每次写操作都显式指定。

此提交新增了从 Spark 会话配置（session configuration）中自动提取快照属性的功能。用户可以通过设置 `spark.sql.iceberg.snapshot-property.<key>=<value>` 在 Spark 会话级别配置快照属性，这些属性会自动应用到所有 Iceberg 写操作的快照中。这简化了批量设置快照属性的工作流，避免每次写操作都重复指定。

同时，write options 仍然可以覆盖会话配置中的同名属性，提供了灵活的优先级控制。

## 如何达成设计目的

1. 在 `SparkSQLProperties` 中定义快照属性前缀常量 `SNAPSHOT_PROPERTY_PREFIX = "spark.sql.iceberg.snapshot-property."`。
2. 在 `SparkWriteConf.extraSnapshotMetadata()` 方法中，使用 `PropertyUtil.propertiesWithPrefix()` 从 Spark 会话配置中提取所有以该前缀开头的配置项，去掉前缀后加入额外快照元数据 Map。
3. 之后原有的 write options 处理逻辑保持不变，由于 write options 在会话配置之后处理，所以 write options 中的同名属性会覆盖会话配置的值。
4. 新增三个测试验证：会话配置属性被提取、write options 覆盖会话配置、属性最终持久化到快照 summary 中。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+3/-0 lines)

**修改目的**：定义快照属性前缀常量。

**工作逻辑**：新增 `public static final String SNAPSHOT_PROPERTY_PREFIX = "spark.sql.iceberg.snapshot-property.";`，作为会话配置中快照属性的前缀标识。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+9/-0 lines)

**修改目的**：从会话配置中提取快照属性。

**工作逻辑**：在 `extraSnapshotMetadata()` 方法开头，调用 `PropertyUtil.propertiesWithPrefix(JavaConverters.mapAsJavaMap(sessionConf.getAll()), SparkSQLProperties.SNAPSHOT_PROPERTY_PREFIX)`，将 Spark 会话配置转换为 Java Map 后，提取所有以 `spark.sql.iceberg.snapshot-property.` 开头的键，去掉前缀后作为 key-value 对放入 `extraSnapshotMetadata`。随后原有的 write options 遍历逻辑继续执行，如果 write options 中有以 `SnapshotSummary.EXTRA_METADATA_PREFIX`（即 `snapshot-property.`）开头的同名键，则会覆盖会话配置的值。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java` (+48/-0 lines)

**修改目的**：验证会话配置快照属性功能。

**工作逻辑**：新增三个测试：
- `testExtraSnapshotMetadataReflectsSessionConfig`：设置 `spark.sql.iceberg.snapshot-property.test-key=session-value`，验证 `extraSnapshotMetadata()` 返回 `{"test-key": "session-value"}`。
- `testExtraSnapshotMetadataWriteOptionsOverrideSessionConfig`：同时设置会话配置和 write option，验证 write option 的值覆盖会话配置。
- `testExtraSnapshotMetadataPersistedOnWrite`：设置会话配置后执行 INSERT 操作，验证快照 summary 中包含该属性。

## 总结

该提交为 Spark 集成新增了从会话级别配置快照属性的能力。用户可以通过 `spark.sql.iceberg.snapshot-property.*` 在 Spark 会话中统一配置快照属性，避免每次写操作重复指定。write options 优先级高于会话配置，提供了灵活的覆盖机制。此功能简化了需要在快照中记录审计/追踪信息的 Spark 工作流。注意此修改仅应用于 Spark v4.0 分支。
