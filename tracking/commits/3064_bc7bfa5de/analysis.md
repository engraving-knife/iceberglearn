# 提交 3064：Flink: Backport: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes(#14967)

## 提交信息

- **序号**：3064 / 4088
- **哈希**：bc7bfa5de4743853d9647ad095322ba71e304221
- **短哈希**：bc7bfa5de
- **日期**：2026-01-05
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes(#14967)
- **PR/Issue**：#14967（回移自 #14880）

## 总体目的

本提交是提交 3061（PR #14880）的回移（backport），将"Flink 动态 Sink 修复 schema 超过 2^16 字节序列化崩溃"的改动从 `flink/v2.1` 同步到较旧的 `flink/v1.20` 与 `flink/v2.0` 两个 Flink 版本分支。Iceberg 同时维护多个 Flink 版本的适配模块，主修复通常先在最新的 `v2.1` 上完成，再按相同逻辑回移到仍受支持的旧版本，以保证各 Flink 版本行为一致、修复同步落地。

被回移的源改动（详见 3061 分析）解决的核心问题是：`DynamicRecordInternalSerializer` 在 `writeSchemaAndSpec=true` 时用 Flink 的 `DataOutputView.writeUTF()` 写 schema JSON，而 `writeUTF` 的长度字段只有 2 字节，最大 65535 字节。当表的 schema 很大（列数多、字段名长、嵌套复杂）时，schema JSON 超过 64KB 会抛 `UTFDataFormatException`，导致序列化失败、作业无法运行或状态恢复失败。回移后，`flink/v1.20` 与 `flink/v2.0` 下的动态 Sink 同样会改用 `SerializerHelper.writeLongUTF`/`readLongUTF`（4 字节长度，突破 64KB 限制），并引入序列化器版本号与 `compatibleAfterMigration` 迁移机制，保证从旧版本（version 0）状态能平滑迁移到新版本（version 1）。

之所以需要回移，是因为使用 Flink 1.20 与 2.0 的用户同样会遇到大 schema 序列化崩溃，且状态恢复兼容性对这些已在生产运行作业同样关键——若不回移，旧版本用户要么无法使用大 schema 表，要么在升级 Iceberg 时面临状态不兼容。

## 如何达成设计目的

把 3061 对 `flink/v2.1` 的全部改动原样应用到 `flink/v1.20` 与 `flink/v2.0`：包括 `DynamicRecordInternalSerializer` 的 long UTF 编码与版本迁移、`SerializerHelper` 迁包公开、`IcebergSourceSplit` import 更新、测试基类参数化与新增测试类。两套改动的文件清单与内容完全对称，共 18 个文件。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializer.java` (+88/-15 lines)

**修改目的**：让 Flink 1.20 动态 Sink 在写大 schema 时改用 long UTF 编码并支持版本迁移。

**工作逻辑**：
与 3061 中 `flink/v2.1` 同名文件改动一致：新增 `writeLongUTF` 字段与构造器；`serialize()`/`deserialize()`/`reuseDeserialize()` 中根据 `writeLongUTF` 选择 `SerializerHelper.writeLongUTF`/`readLongUTF` 或 `writeUTF`/`readUTF`；`DynamicRecordInternalTypeSerializerSnapshot` 引入 `MOST_RECENT_VERSION=1`、`version`、`serializerCache` 字段，`resolveSchemaCompatibility()` 通过 `DynMethods` 反射注入 `initializeSerializerCache` 并返回 `compatibleAfterMigration()`，`restoreSerializer()` 按版本返回对应编码的 serializer；新增 `getSerializerCache()` 测试访问器。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java` (+1/-0 lines)

**修改目的**：适配 `SerializerHelper` 迁包后的 import。

**工作逻辑**：
新增 `import org.apache.iceberg.flink.util.SerializerHelper;`，与 v2.1 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/util/SerializerHelper.java` (+11/-4 lines)

**修改目的**：将 `SerializerHelper` 迁移到 `util` 包并公开，方法签名放宽。

**工作逻辑**：
从 `flink/source/split/SerializerHelper.java` 重命名为 `flink/util/SerializerHelper.java`，包声明改为 `org.apache.iceberg.flink.util`，类改为 `@Internal public`，`writeLongUTF`/`readLongUTF`/`writeUTFBytes` 入参放宽为 `DataOutputView`/`DataInputView`，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializerTestBase.java` (+6/-2 lines)

**修改目的**：测试基类支持 `writeLongUTF` 参数化。

**工作逻辑**：
新增 `writeLongUTF` 字段与双参构造器，`createSerializer()` 传入该参数，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializer.java` (+119/-0 lines)

**修改目的**：新增序列化器版本与迁移机制测试。

**工作逻辑**：
包含 `testCurrentTypeSerializerSnapshotVersion`、`testCurrentTypeSerializerSnapshotCompatibility`、`testRestoreFromOldVersion` 三个测试，用 `OldTypeSerializerSnapshot` 内部类模拟 version 0 并验证 `compatibleAfterMigration` 与 `restoreSerializer` 缓存非空，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchema.java` (+2/-2 lines)

**修改目的**：明确该子类用标准 UTF 编码。

**工作逻辑**：
构造器改为 `super(true, false)`，注释更新为 "standard UTF encoding"，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaId.java` (+2/-2 lines)

**修改目的**：明确该子类用标准 UTF 编码。

**工作逻辑**：
构造器改为 `super(false, false)`，注释更新，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaIdLongUTF.java` (+28/-0 lines)

**修改目的**：新增 schema id + long UTF 测试。

**工作逻辑**：
新测试类 `super(false, true)`，覆盖 schema id 模式下的 long UTF 路径，与 v2.1 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaLongUTF.java` (+28/-0 lines)

**修改目的**：新增完整 schema + long UTF 测试。

**工作逻辑**：
新测试类 `super(true, true)`，覆盖大 schema 场景，与 v2.1 一致。

### `flink/v2.0/` 下 9 个文件 (+284/-26 lines)

**修改目的**：对 Flink 2.0 做完全相同的回移。

**工作逻辑**：
`flink/v2.0` 下的 `DynamicRecordInternalSerializer.java`、`IcebergSourceSplit.java`、`SerializerHelper.java`（迁包）、`DynamicRecordInternalSerializerTestBase.java`、`TestDynamicRecordInternalSerializer.java`（新增）、`TestDynamicRecordInternalSerializerWriteSchema.java`、`TestDynamicRecordInternalSerializerWriteSchemaId.java`、`TestDynamicRecordInternalSerializerWriteSchemaIdLongUTF.java`（新增）、`TestDynamicRecordInternalSerializerWriteSchemaLongUTF.java`（新增）共 9 个文件，改动内容与上述 `flink/v1.20` 完全一致，此处不再逐文件赘述。

## 总结

本提交将 3061 的"Flink 动态 Sink 修复大 schema 序列化崩溃"改动从 `flink/v2.1` 回移到 `flink/v1.20` 与 `flink/v2.0`，使三个 Flink 版本在 long UTF 编码、序列化器版本迁移、`SerializerHelper` 迁包复用与测试覆盖上完全对齐，保证旧版本用户同样能使用大 schema 表并平滑迁移状态。
