# 提交 3820：Core: Refactor v4 struct builders to improve validation (#16408)

## 提交信息

- **序号**：3820 / 4088
- **哈希**：c6464040081ef6c578a59a08f9d668466486dd56
- **短哈希**：c6464040
- **日期**：2026-06-03 11:29:41 -0700
- **作者**：Anoop Johnson <anoop@apache.org>
- **提交说明**：Core: Refactor v4 struct builders to improve validation (#16408)
- **PR/Issue**：#16408

## 总体目的

本提交对 Iceberg v4 表格式相关的几个 struct 构建器进行重构，强化校验逻辑并改善代码组织。v4 引入了 row lineage（行谱系）相关的 `Tracking` 结构、`ManifestInfoStruct`（manifest 元信息）以及 `DeletionVectorStruct`（删除向量）。原实现存在几个问题：

1. `TrackingStruct` 同时承担"数据载体"和"构建器"两种职责，构建逻辑内嵌在 struct 类中，校验分散且难以复用，状态转换（ADDED/EXISTING/DELETED/REPLACED）与字段互斥关系（DV 适用于数据文件、deleted/replaced positions 适用于 manifest 文件）缺乏集中校验。
2. `ManifestInfoStruct` 与 `DeletionVectorStruct` 使用 `-1`（或 `-1L`）作为"未设置"哨兵值，这种做法难以区分"显式设置为 -1 的非法值"与"未设置"，且校验信息不够清晰（如 "Invalid added files count" vs "Missing required value"）。

本提交提取独立的 `TrackingBuilder` 类集中管理 `Tracking` 的构建与校验，把 struct 类瘦身为纯数据载体；同时把 `ManifestInfoStruct`、`DeletionVectorStruct` 的字段从原始类型改为包装类型（null 表示未设置），增加"缺失必填值"与"非负"两类校验，使错误消息更精确。整体目标是提升 v4 元数据构造的健壮性与可维护性。

## 如何达成设计目的

设计上采用"提取构建器 + 包装类型 + 集中校验"三步：
- 新建 `TrackingBuilder`，提供 `added`/`from`/`deleted`/`replaced` 静态工厂与 `dvUpdated`/`deletedPositions`/`replacedPositions`/`build` 方法，内部对源数据、状态转换、DV 与 positions 的互斥关系做 `Preconditions` 校验。`TrackingStruct` 移除构建逻辑，仅保留数据字段与构造函数。
- `ManifestInfoStruct` 与 `DeletionVectorStruct` 的 Builder 把原始类型字段（`int`/`long`）改为 `Integer`/`Long`（null 表示未设置），在 `build()` 时分别校验"必填非 null"与"非负"，并相应调整 setter 签名（如 `dvCardinality(Long)` → `dvCardinality(long)` 配合非负校验）。
- 构造函数可见性调整（如 `ManifestInfoStruct` 改为包级可见以便 builder 访问），`SupportsIndexProjection` 父类构造从 `super(BASE_TYPE, BASE_TYPE)` 改为 `super(BASE_TYPE.fields().size())`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackingBuilder.java` (+179/-0 lines, new file)

**修改目的**：提取独立的 `Tracking` 构建器，集中校验逻辑。

**工作逻辑**：
- 字段：`status`、`snapshotId`、`dataSequenceNumber`、`fileSequenceNumber`、`firstRowId`、`newSnapshotId`、`dvSnapshotId`、`deletedPositions`、`replacedPositions`。
- 静态工厂：
  - `added(long newSnapshotId)`：新建 ADDED 条目。
  - `from(Tracking source, long newSnapshotId)`：从源 EXISTING 条目派生。
  - `deleted`/`replaced`：返回终态（DELETED/REPLACED）条目。
- 方法：
  - `dvUpdated()`：标记 DV 已更新（设置 `dvSnapshotId = newSnapshotId`），校验 deleted/replaced positions 未设置（DV 仅适用于数据文件）。
  - `deletedPositions(ByteBuffer)`/`replacedPositions(ByteBuffer)`：设置 positions，校验 status 为 EXISTING 且 DV 未设置（positions 仅适用于 manifest 文件）。
  - `build()`：构造 `TrackingStruct`。
- 校验辅助：
  - `validateSource`：源非 null，dataSequenceNumber/fileSequenceNumber 非 null。
  - `validateStatusTransition`：源 status 非 null、非终态（DELETED/REPLACED 不可复活），目标不可为 ADDED（ADDED 只能作为起始）。

### `core/src/main/java/org/apache/iceberg/TrackingStruct.java` (+6/-91 lines)

**修改目的**：移除内嵌的构建器逻辑，瘦身为纯数据载体。

**工作逻辑**：
删除原 Builder 内部类及其所有构建方法，仅保留字段、构造函数与访问器。父类构造改为 `super(BASE_TYPE.fields().size())`。构建职责完全交给 `TrackingBuilder`。

### `core/src/main/java/org/apache/iceberg/DeletionVectorStruct.java` (+15/-9 lines)

**修改目的**：用包装类型替代哨兵值，增强校验。

**工作逻辑**：
- 字段从 `long offset = -1L` 等改为 `Long offset = null` 等。
- `build()` 时校验 `location/offset/sizeInBytes/cardinality` 非 null（"Missing required value"）且非负。
- setter 内增加非负校验。
- DV 设置改为强制非 null（`Preconditions.checkArgument(buffer != null, "Invalid DV: null")`）。

### `core/src/main/java/org/apache/iceberg/ManifestInfoStruct.java` (+80/-27 lines)

**修改目的**：同样用包装类型替代哨兵值，增加必填与非负校验。

**工作逻辑**：
- 所有 count/rows/sequenceNumber 字段从原始类型改为包装类型（null 表示未设置）。
- 各 setter 增加非负校验。
- `build()` 时校验所有必填字段非 null（"Missing required value: added files count" 等），替代原来的 `>= 0` 校验，使"未设置"与"非法负值"可区分。
- `dvCardinality` setter 签名从 `Long` 改为 `long` 并加非负校验。
- 构造函数可见性从 `private` 改为包级，供 builder 使用。
- `toString` 中 `dvCardinality` 的 null 处理简化。

### 测试文件

- `core/src/test/java/org/apache/iceberg/TestTrackingStruct.java` (+388/-77 lines)：覆盖 `TrackingBuilder` 的各种构建路径、状态转换校验、DV 与 positions 互斥校验、源校验等。
- `core/src/test/java/org/apache/iceberg/TestManifestInfoStruct.java` (+205/-12 lines)：覆盖必填值缺失校验、非负校验、正常构建。
- `core/src/test/java/org/apache/iceberg/TestDeletionVectorStruct.java` (+35/-12 lines)：覆盖 DV 结构的必填与非负校验。
- `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+14/-6 lines)：适配 TrackingStruct 构建方式的变更。

## 总结

本提交是 v4 元数据构造层的一次质量提升重构：提取独立的 `TrackingBuilder` 集中管理 row lineage 构建与状态转换校验，把 `ManifestInfoStruct`/`DeletionVectorStruct` 从哨兵值（-1）改为包装类型（null）以精确区分"未设置"与"非法值"，并补充了缺失值与非负校验。重构后代码职责更清晰、校验更严格、错误消息更友好，测试覆盖充分。这是 v4 格式实现稳健性的重要改进，为后续 v4 功能的可靠演进打下基础。
