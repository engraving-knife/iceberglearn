# 提交 2039：Core: Ensure reactivated view version uses correct timestamp

## 提交信息

- **序号**：2039 / 4088
- **哈希**：01fe380d455949abb49ebfecd9509afce8764fae
- **短哈希**：01fe380d4
- **日期**：2025-04-25 09:39:42 +0200
- **作者**：Leon Lin
- **提交说明**：Core: Ensure reactivated view version uses correct timestamp (#12821)
- **PR/Issue**：#12821

## 总体目的

Iceberg 的 View（视图）支持版本管理，每次视图定义变更会创建一个新的 ViewVersion，并在历史记录（history）中添加一条 ViewHistoryEntry 记录"哪个版本在什么时间成为当前版本"。当用户将某个版本设为当前版本时（`setCurrentVersionId`），会在历史中添加一条记录。

问题在于：当重新激活（reactivate）一个过去曾经是当前版本的旧版本时（例如回滚操作），`ViewMetadata` 构建器会使用该 ViewVersion 自身的 `timestampMillis`（即该版本最初创建时的时间戳）作为历史记录的时间戳，而不是当前系统时间。这导致历史记录中的时间戳不正确——它记录的是版本创建时间而非重新激活时间，破坏了历史时间线的正确性。

本提交修复此问题：当设置当前版本时，如果该版本是在本次构建变更中新添加的，则使用版本自身的时间戳；如果该版本是已存在的旧版本（即重新激活），则使用当前系统时间 `System.currentTimeMillis()`。

## 如何达成设计目的

修复位于 `ViewMetadata` 构建器的 `setCurrentVersionId` 逻辑中：

1. 在添加历史记录前，检查目标版本是否在当前这批变更中通过 `AddViewVersion` 新添加的
2. 如果是新添加的版本：使用版本的 `timestampMillis()`（这是版本创建时的时间戳，合理）
3. 如果是已存在的旧版本（重新激活场景）：使用 `System.currentTimeMillis()`（记录重新激活的时间）

关键判断逻辑：
```java
boolean versionAddedInThisChange =
    changes(MetadataUpdate.AddViewVersion.class)
        .anyMatch(added -> added.viewVersion().versionId() == newVersionId);
```
通过检查当前的变更列表中是否包含与目标版本 ID 匹配的 `AddViewVersion` 更新来判断版本是否是本次新添加的。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java` (修改, +8/-1 lines)

**修改目的**：修复重新激活旧视图版本时历史记录时间戳不正确的问题。

**工作逻辑**：
在 `ViewMetadata.Builder` 的 `setCurrentVersionId` 逻辑中（构建 historyEntry 时），新增判断：
- 通过 `changes(MetadataUpdate.AddViewVersion.class).anyMatch(...)` 检查目标版本是否在本次构建变更中新添加
- 如果是（`versionAddedInThisChange == true`）：使用 `version.timestampMillis()` 作为历史记录时间戳
- 如果否（重新激活旧版本）：使用 `System.currentTimeMillis()` 作为历史记录时间戳

这确保了历史记录中的时间戳始终反映"版本成为当前版本的实际时间"，而非版本创建时间。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java` (修改, +66/-2 lines)

**修改目的**：验证重新激活场景下历史时间戳的正确性。

**工作逻辑**：
新增测试方法 `versionHistoryEntryMaintainCorrectTimeline`，使用固定时间戳（1000、2000、3000）创建三个视图版本，验证三种场景：
1. **切换到已存在的版本**：将当前版本从 v1 切换到 v2（v2 已存在），验证历史记录中 v2 的时间戳为当前系统时间（大于 3000），而非 v2 的创建时间 2000
2. **添加新版本并设为当前**：添加 v3 并设为当前，验证历史记录中 v3 的时间戳为版本自身的时间戳 3000
3. **回滚到旧版本**：将当前版本从 v3 回滚到 v1，验证历史记录中 v1 的时间戳为当前系统时间（大于 3000），而非 v1 的创建时间 1000

同时重构 `newViewVersion` 方法，新增支持指定 `timestampMillis` 参数的重载版本，以便测试中使用固定时间戳。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java` (修改, +2/-2 lines)

**修改目的**：适配测试中视图版本的添加顺序。

**工作逻辑**：
将 `addVersion(version2)` 从内层 builder 移到外层 builder（在 `setCurrentVersionId(2)` 之前），使 version2 的添加和设为当前版本分属不同的构建变更，以匹配修复后的时间戳逻辑。

## 总结

本提交修复了视图版本重新激活时历史记录时间戳不正确的 Bug。此前重新激活旧版本时，历史记录使用版本创建时间而非重新激活时间。修复方案是区分"新添加版本"和"重新激活已有版本"两种场景，分别使用版本时间戳和当前系统时间。新增了完整的测试覆盖三种场景（切换、新增、回滚），并调整了相关测试的构建顺序以匹配新逻辑。
