# 提交 3061：Flink: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes (#14880)

## 提交信息

- **序号**：3061 / 4088
- **哈希**：3048d772aed6572bce28abbddae661d441058f74
- **短哈希**：3048d772a
- **日期**：2026-01-05
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes (#14880)
- **PR/Issue**：#14880

## 总体目的

本提交修复了 Flink Iceberg 动态 Sink（Dynamic Sink）在 schema/partition spec 的 JSON 序列化结果超过 65535 字节（2^16，即 64KB）时崩溃的问题。动态 Sink 的 `DynamicRecordInternalSerializer` 在 `writeSchemaAndSpec=true`（即写入完整 schema 与 partition spec，而非仅写 schema id）时，会通过 Flink 的 `DataOutputView.writeUTF()` 把 `SchemaParser.toJson(schema)` 与 `PartitionSpecParser.toJson(spec)` 写入序列化流。但 Java/Flink 的 `writeUTF` 采用 modified UTF-8 编码，且长度字段只有 2 字节（无符号 16 位），最大只能表示 65535 字节。当表的 schema 很大（例如列数极多、字段名很长、含复杂嵌套类型）时，schema 的 JSON 字符串可能超过 64KB，触发 `UTFDataFormatException`，导致整个序列化失败、作业无法运行或状态恢复失败。

仓库中其实已存在一个 `SerializerHelper` 工具类（原先位于 `flink/source/split/` 包，用于 `IcebergSourceSplit` 的 split 序列化），它提供 `writeLongUTF`/`readLongUTF` 方法，用 4 字节 int 表示长度来突破 64KB 限制。但 `DynamicRecordInternalSerializer` 并未使用它，仍直接调用 `writeUTF`。本提交的核心改动就是让动态 Sink 在写 schema 时改用 `SerializerHelper.writeLongUTF`/`readLongUTF`，并对 partition spec 也保持一致（spec 通常较小，但仍保留 `writeUTF` 以兼容，schema 才走 long UTF）。

除了修复序列化本身，本提交还必须处理状态兼容性：序列化格式的改变意味着旧版本（version 0）序列化的数据与新版本（version 1）不兼容。Flink 的 `TypeSerializerSnapshot` 机制要求显式声明兼容性。原实现把 `getCurrentVersion()` 硬编码为 0、`resolveSchemaCompatibility()` 直接返回 `compatibleAsIs()`，且 `restoreSerializer()` 传入 null 的 serializerCache（注释承认这会有问题但依赖 `compatibleAsIs` 保证它不被使用）。这种实现无法支持版本迁移。本提交引入版本号 `MOST_RECENT_VERSION=1`，并在 `resolveSchemaCompatibility` 中判断：若旧版本号等于当前版本则 `compatibleAsIs`；否则通过反射调用 `initializeSerializerCache` 把当前 serializer 的 `TableSerializerCache` 注入旧 snapshot，再返回 `compatibleAfterMigration()`，让 Flink 先用旧格式读数据、再切换到新格式。`restoreSerializer()` 也相应改为根据版本号返回带 `writeLongUTF=false`（旧格式）或 `true`（新格式）的 serializer，并传入真实的 serializerCache 而非 null。

为支持上述改动，`SerializerHelper` 从 `flink/source/split/` 包迁移到 `flink/util/` 包并改为 `public`，方法签名从 `DataOutputSerializer`/`DataInputDeserializer` 放宽为 `DataOutputView`/`DataInputView`（更通用的接口），以便动态 Sink 与 source split 都能复用。`IcebergSourceSplit` 的 import 也随之更新。

## 如何达成设计目的

整体思路是"复用已有的 long UTF 工具 + 引入序列化器版本与迁移机制"。涉及 `flink/v2.1` 下三处主代码与五处测试：把 `SerializerHelper` 迁包并公开；在 `DynamicRecordInternalSerializer` 的 serialize/deserialize 路径上条件性走 `writeLongUTF`/`readLongUTF`；重写 `TypeSerializerSnapshot` 的版本管理与兼容性判定，支持从 version 0 迁移到 version 1；新增 `TestDynamicRecordInternalSerializer` 验证版本与迁移逻辑，并新增两个 long UTF 测试变体覆盖新编码路径。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializer.java` (+88/-15 lines)

**修改目的**：让动态 Sink 在写大 schema 时改用 long UTF 编码，并支持序列化器版本迁移。

**工作逻辑**：
新增字段 `private final boolean writeLongUTF` 与带该参数的构造器（原两参构造器委托给新构造器并默认 `writeLongUTF=true`），`duplicate()` 也传递该标志。在 `serialize()` 中，当 `writeSchemaAndSpec` 为真时，若 `writeLongUTF` 则用 `SerializerHelper.writeLongUTF(dataOutputView, SchemaParser.toJson(toSerialize.schema()))`，否则回退到 `dataOutputView.writeUTF(...)`；partition spec 仍用 `writeUTF`。`deserialize()` 与 `reuseDeserialize()` 两个反序列化方法对称地根据 `writeLongUTF` 选择 `SerializerHelper.readLongUTF` 或 `readUTF`。

`snapshotConfiguration()` 现在把 `serializerCache` 也传入 snapshot。`DynamicRecordInternalTypeSerializerSnapshot` 新增 `MOST_RECENT_VERSION=1` 常量、`version` 与 `serializerCache` 字段；`getCurrentVersion()` 返回 `version` 而非硬编码 0；`readSnapshot` 记录读到的版本号。`resolveSchemaCompatibility()` 中：若旧 snapshot 的 `getCurrentVersion()` 等于当前版本则 `compatibleAsIs`；否则用 `DynMethods` 反射调用旧 snapshot 的 `initializeSerializerCache(serializerCache)` 注入缓存（确保旧 serializer 能拿到 CatalogLoader），并返回 `compatibleAfterMigration()`，让 Flink 执行迁移式恢复。`restoreSerializer()` 根据版本号返回：版本 < 1 时返回 `new DynamicRecordInternalSerializer(serializerCache, writeSchemaAndSpec, false)`（用旧编码读旧数据），否则返回带 `true` 的新 serializer。新增私有方法 `initializeSerializerCache(TableSerializerCache cache)` 供反射注入，注释强调"此方法不可删除"。另外新增 `@VisibleForTesting` 的 `getSerializerCache()` 用于测试断言。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java` (+1/-0 lines)

**修改目的**：更新 import 以适配 `SerializerHelper` 迁包。

**工作逻辑**：
新增 `import org.apache.iceberg.flink.util.SerializerHelper;`，因为 `SerializerHelper` 从 `org.apache.iceberg.flink.source.split` 包迁移到了 `org.apache.iceberg.flink.util` 包。`IcebergSourceSplit` 自身使用 `SerializerHelper` 的逻辑不变。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/util/SerializerHelper.java` (+11/-4 lines)

**修改目的**：将 `SerializerHelper` 迁移到 `util` 包并公开，方法签名放宽到 `DataOutputView`/`DataInputView`。

**工作逻辑**：
文件从 `flink/source/split/SerializerHelper.java` 重命名为 `flink/util/SerializerHelper.java`，包声明由 `package org.apache.iceberg.flink.source.split;` 改为 `package org.apache.iceberg.flink.util;`。类由包级可见 `class SerializerHelper` 改为 `@Internal public class SerializerHelper`，加 `@Internal` 注解表明这是 Flink 内部 API。`writeLongUTF` 的入参由 `DataOutputSerializer` 放宽为 `DataOutputView`，`readLongUTF` 由 `DataInputDeserializer` 放宽为 `DataInputView`，私有 `writeUTFBytes` 同步改为 `DataOutputView`。这使得该工具既能被 source split（用 `DataOutputSerializer`）也能被动态 Sink（用 `DataOutputView`）复用。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializerTestBase.java` (+6/-2 lines)

**修改目的**：让测试基类支持 `writeLongUTF` 参数化。

**工作逻辑**：
基类新增 `private final boolean writeLongUTF` 字段，构造器由 `DynamicRecordInternalSerializerTestBase(boolean writeFullSchemaAndSpec)` 改为带 `writeLongUTF` 的双参版本。`createSerializer()` 在构造 `DynamicRecordInternalSerializer` 时传入该参数，从而让不同子类可以分别覆盖"标准 UTF"与"long UTF"两种编码路径。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializer.java` (+119/-0 lines)

**修改目的**：新增针对序列化器版本与迁移机制的专项测试。

**工作逻辑**：
新增三个测试。`testCurrentTypeSerializerSnapshotVersion` 断言当前 serializer 的 snapshot 版本为 1。`testCurrentTypeSerializerSnapshotCompatibility` 断言当前版本自身 `isCompatibleAsIs()`。`testRestoreFromOldVersion` 是核心：构造一个 `OldTypeSerializerSnapshot`（内部类，重写 `getCurrentVersion()` 返回 0）模拟旧版本，先序列化其 snapshot 再用当前 snapshot 的 `readSnapshot(0, ...)` 读取，断言读到版本为 0 且对旧 snapshot `isCompatibleAsIs()`；再断言当前版本（1）的 snapshot 对旧版本 `isCompatibleAfterMigration()`，并验证 `restoreSerializer()` 返回的 serializer 的 `getSerializerCache()` 非空。该测试完整覆盖了从 version 0 到 version 1 的迁移路径。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchema.java` (+2/-2 lines)

**修改目的**：明确该子类使用标准 UTF 编码（保留旧路径覆盖）。

**工作逻辑**：
构造器由 `super(true)` 改为 `super(true /* writeFullSchemaAndSpec */, false /* writeLongUTF */)`，并更新类注释为"Test writing DynamicRecord with the full schema and standard UTF encoding"，明确此子类覆盖的是不走 long UTF 的旧编码路径。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaId.java` (+2/-2 lines)

**修改目的**：明确该子类使用标准 UTF 编码。

**工作逻辑**：
构造器由 `super(false)` 改为 `super(false /* writeFullSchemaAndSpec */, false /* writeLongUTF */)`，注释同步更新为"standard UTF encoding"，与 `WriteSchema` 子类保持一致地覆盖旧编码路径。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaIdLongUTF.java` (+28/-0 lines)

**修改目的**：新增覆盖 schema id + long UTF 编码路径的测试。

**工作逻辑**：
新测试类继承 `DynamicRecordInternalSerializerTestBase`，构造器调用 `super(false /* writeFullSchemaAndSpec */, true /* writeLongUTF */)`，覆盖"仅写 schema id 且使用 long UTF"的组合，确保新编码在 schema id 模式下也正确工作。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaLongUTF.java` (+28/-0 lines)

**修改目的**：新增覆盖完整 schema + long UTF 编码路径的测试。

**工作逻辑**：
新测试类继承 `DynamicRecordInternalSerializerTestBase`，构造器调用 `super(true /* writeFullSchemaAndSpec */, true /* writeLongUTF */)`，覆盖"写完整 schema 与 spec 且使用 long UTF"的组合，这正是本次修复要解决的大 schema 场景，确保超过 64KB 的 schema JSON 能正确序列化与反序列化。

## 总结

本提交通过让 Flink 动态 Sink 的 `DynamicRecordInternalSerializer` 在写 schema 时改用 `SerializerHelper.writeLongUTF`/`readLongUTF`（4 字节长度，突破 64KB 限制），修复了超大 schema 序列化崩溃的问题；同时引入序列化器版本号与 `compatibleAfterMigration` 迁移机制，保证旧版本（version 0）状态能平滑迁移到新版本（version 1）；并将 `SerializerHelper` 迁包公开以供 source 与 sink 复用。配套测试完整覆盖了新编码路径与版本迁移逻辑，使动态 Sink 在大 schema 与状态恢复场景下均稳定可用。
