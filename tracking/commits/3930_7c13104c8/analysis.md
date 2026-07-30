# 提交 3930：Core: Introduce builder for TrackedFile (#16769)

## 提交信息

- **序号**：3930 / 4088
- **哈希**：7c13104c8c20323c895aeb33fe5ca1f3b127889f
- **短哈希**：7c13104c8
- **日期**：2026-06-22 15:17:15 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Introduce builder for TrackedFile (#16769)
- **PR/Issue**：#16769

## 总体目的

这次提交为 Iceberg 核心引入了 `TrackedFileBuilder`，这是一种用于构建 `TrackedFile` 对象的流式构建器模式。`TrackedFile` 是 Iceberg 内部用于跟踪数据文件、删除文件和 manifest 文件生命周期的数据结构，包含文件内容类型、位置、格式、分区数据、记录数、文件大小、tracking 状态（ADDED/DELETED/REPLACED）等大量字段。

在引入构建器之前，`TrackedFileStruct` 的构造需要通过直接构造函数或 `DataManager`/`AppendFiles` 等 API 进行，字段校验逻辑分散且容易出错。构建器的引入有几个月的：
1. 提供类型安全的流式 API 来构造 TrackedFile，每个 setter 方法都包含参数校验（如非空、非负、类型约束）。
2. 区分不同文件内容类型（DATA、EQUALITY_DELETES、DATA_MANIFEST、DELETE_MANIFEST）的构造路径，通过静态工厂方法 `data()`、`equalityDelete()`、`dataManifest()`、`deleteManifest()` 引导正确使用。
3. 支持"从已有 TrackedFile 派生"的场景（`from()`）和终态转换（`deleted()`、`replaced()`），封装 tracking 状态的转换逻辑。
4. 在构造时强制执行字段约束（如 manifest 必须有 manifestInfo、equality deletes 必须有 equalityIds、deletionVector 只能加到 DATA 条目等），将运行时错误提前到构建期。

此外，提交还为 `DeletionVectorStruct` 添加了 `equals`/`hashCode` 方法，以支持 builder 中对 deletionVector 重复添加的检测。

## 如何达成设计目的

设计采用经典 Builder 模式：
- **静态工厂方法**：`data(snapshotId)`、`equalityDelete(snapshotId)`、`dataManifest(snapshotId)`、`deleteManifest(snapshotId)` 创建新文件的 builder；`from(source, snapshotId)` 从已有 TrackedFile 派生；`deleted(source, snapshotId)` 和 `replaced(source, snapshotId)` 直接生成终态 TrackedFile。
- **必填字段**：writerFormatVersion、location、fileFormat、recordCount、fileSizeInBytes、partitionData 在 `build()` 时校验非空。
- **类型约束**：每个 setter 方法通过 `Preconditions.checkArgument` 校验，例如 sortOrderId 不能加到 manifest、deletionVector 只能加到 DATA、equalityIds 只能加到 EQUALITY_DELETES、splitOffsets 不能加到 manifest 等。
- **tracking 状态**：build 时通过 `TrackingBuilder` 根据 sourceTracking 是否存在决定是 ADDED 还是从已有 tracking 派生，并支持 dvUpdated、deletedPositions、replacedPositions 等增量信息。

同时重构了 `TrackedFileStruct` 的构造函数，新增一个接收全部字段的构造函数（包含 specId、contentStats、sortOrderId、deletionVector、manifestInfo、keyMetadata、splitOffsets、equalityIds），供 builder 使用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeletionVectorStruct.java` (+21/-0 lines)

**修改目的**：为 DeletionVectorStruct 添加 equals/hashCode。

**工作逻辑**：
新增 `equals` 和 `hashCode` 方法，基于 location、offset、sizeInBytes、cardinality 四个字段判断相等性。这使得 TrackedFileBuilder 在 `deletionVector()` setter 中能够检测重复添加相同的 DV（`!this.deletionVector.equals(newDeletionVector)`）。

### `core/src/main/java/org/apache/iceberg/TrackedFileBuilder.java` (+364 lines, 新文件)

**修改目的**：新增 TrackedFileBuilder 构建器类。

**工作逻辑**：
- 静态工厂方法：`data()`、`equalityDelete()`、`dataManifest()`、`deleteManifest()`、`from()`、`deleted()`、`replaced()`。
- 字段校验 setter：每个方法接收参数并校验后返回 this 以支持链式调用。
- `terminal()` 私有方法：用于 deleted/replaced 场景，直接从 source 复制所有字段并应用新的 tracking。
- `build()` 方法：校验所有必填字段，通过 `TrackingBuilder` 构造 tracking 状态，最终创建 `TrackedFileStruct`。

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+12/-12 lines)

**修改目的**：扩展构造函数以接收全部字段，调整字段声明顺序。

**工作逻辑**：
- 将 `tracking` 字段移到 `writerFormatVersion` 之后（字段顺序调整，不影响行为）。
- 将 `specId` 从必填字段区移到可选字段区（因其有默认值 null）。
- 新增接收全部字段的构造函数，将 ByteBuffer 转为 byte[]、List 转为 array，与现有字段存储方式一致。
- 移除了原构造函数上的 `/** Constructor that accepts required fields. */` 注释。

### `core/src/test/java/org/apache/iceberg/TestDeletionVectorStruct.java` (+58/-0 lines)

**修改目的**：测试 DeletionVectorStruct 的 equals/hashCode。

**工作逻辑**：新增 `testDvEquality` 测试，验证相同字段值的两个 DV 相等、不同字段值不相等。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileAdapters.java` (+44/-112 lines)

**修改目的**：将现有测试适配为使用 TrackedFileBuilder。

**工作逻辑**：将测试中直接构造 TrackedFileStruct 的代码替换为使用 builder API，简化测试代码。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileBuilder.java` (+863 lines, 新文件)

**修改目的**：为 TrackedFileBuilder 提供全面的单元测试。

**工作逻辑**：覆盖各种构建场景，包括必填字段缺失校验、类型约束校验、from/deleted/replaced 派生、tracking 状态转换等。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+27/-34 lines)

**修改目的**：适配新的构造函数签名。

**工作逻辑**：将测试中对 TrackedFileStruct 构造的调用更新为使用新的全字段构造函数或通过 builder 创建。

## 总结

这次提交通过引入 TrackedFileBuilder，为 Iceberg 核心的文件生命周期管理提供了类型安全、校验完备的构建 API。构建器封装了字段约束和 tracking 状态转换逻辑，将错误检测提前到构建期，并通过工厂方法引导不同文件类型的正确构造路径。这是 TrackedFile 抽象演进的铺垫性工作，为后续可能的字段重命名（如 #16952 中的 writer_format_version → format_version）和更多文件类型支持打下基础。
