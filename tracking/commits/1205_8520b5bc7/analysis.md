# 提交 1205：Core: Deprecate legacy ways for loading position deletes (#11242)

## 提交信息

- **序号**：1205 / 4088
- **哈希**：8520b5bc761910132c72c200f6ca5766cac6b958
- **短哈希**：8520b5bc7
- **日期**：2024-10-01（Tue Oct 1 10:44:19 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnochyi@apache.org>
- **提交说明**：Core: Deprecate legacy ways for loading position deletes (#11242)
- **PR/Issue**：#11242

## 总体目的

Iceberg 在读取侧应用位置删除（position deletes）时，长期以来通过 `Deletes` 工具类的静态方法 `toPositionIndex(...)` 把位置删除文件物化为 `PositionDeleteIndex`。这套 API 存在以下问题：

1. **API 分散、职责不清**：`Deletes` 是个"大杂烩"工具类，同时承担相等删除物化、位置删除物化、流式归并过滤/标记等多种职责，调用方需要自己组装"读取删除文件 → 过滤目标数据文件 → 提取 pos → 合并 → 构建索引"的链路。
2. **缺少统一抽象**：随着 deletion vector（DV）支持的引入，位置删除既可能来自传统的位置删除文件，也可能来自 Puffin 文件中的 DV。`Deletes.toPositionIndex` 只处理传统位置删除文件，无法适配 DV 场景。
3. **难以缓存与并行加载**：新架构希望在 executor 上并行加载删除并在 executor 侧缓存，旧的静态方法不支持这种模式。

为此，Iceberg 在 `org.apache.iceberg.data` 包下引入了新的 `DeleteLoader` 接口（由更早的提交 #8755 引入），统一"加载删除文件内容到内存数据结构"的契约：

- `loadEqualityDeletes(Iterable<DeleteFile>, Schema)`：加载相等删除为 `StructLikeSet`；
- `loadPositionDeletes(Iterable<DeleteFile>, CharSequence filePath)`：加载位置删除（含 DV）为 `PositionDeleteIndex`，按数据文件路径过滤。

本提交是这一迁移计划的"宣告"步骤：把 `Deletes` 中两个遗留的 `toPositionIndex` 静态重载标记为 `@Deprecated`（since 1.7.0, will be removed in 1.8.0），引导调用方迁移到 `DeleteLoader` API，为 1.8.0 正式移除做铺垫。

## 如何达成设计目的

通过给两个遗留方法加 `@Deprecated` 注解和 javadoc 说明来实现：

1. 在 `Deletes.toPositionIndex(CharSequence, List<CloseableIterable<T>>)` 和 `Deletes.toPositionIndex(CharSequence, List<CloseableIterable<T>>, ExecutorService)` 上各加 `@Deprecated` 和 `@deprecated since 1.7.0, will be removed in 1.8.0; use delete loaders.` 的 javadoc。
2. 不修改方法体，保持向后兼容，让现有调用方有迁移窗口。
3. 调用方应改为通过 `DeleteLoader`（如 `BaseDeleteLoader`）来加载位置删除，由 `DeleteLoader` 实现内部决定是读位置删除文件还是读 DV。

注：本提交只做"标记废弃"，不实际修改任何调用方。调用方的迁移会在后续提交中分批进行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`（修改，+8 行）

**修改目的**：把两个遗留的位置删除加载方法标记为 `@Deprecated`。

**工作逻辑**：

对以下两个静态方法各添加 `@Deprecated` 注解和 javadoc：

```java
/**
 * @deprecated since 1.7.0, will be removed in 1.8.0; use delete loaders.
 */
@Deprecated
public static <T extends StructLike> PositionDeleteIndex toPositionIndex(
    CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles) {
  return toPositionIndex(dataLocation, deleteFiles, ThreadPools.getDeleteWorkerPool());
}
```

和带 `ExecutorService` 参数的重载：

```java
/**
 * @deprecated since 1.7.0, will be removed in 1.8.0; use delete loaders.
 */
@Deprecated
public static <T extends StructLike> PositionDeleteIndex toPositionIndex(
    CharSequence dataLocation,
    List<CloseableIterable<T>> deleteFiles,
    ExecutorService executor) {
  ...
}
```

两个方法的语义：

- 无 `ExecutorService` 重载：委托给带 `ExecutorService` 重载，使用全局 `ThreadPools.getDeleteWorkerPool()`。
- 带 `ExecutorService` 重载：对每个删除文件用 `DataFileFilter` 按 `dataLocation` 过滤，提取 `pos` 列后并行合并（`ParallelIterable`），最后调用 `toPositionIndex(CloseableIterable<Long>)` 构建位图索引。

被废弃的原因：

1. 这两个方法把"读取删除文件 → 过滤 → 合并 → 构建索引"耦合在一起，调用方无法插入缓存、无法适配 DV；
2. 新的 `DeleteLoader` 接口把"加载"这一步抽象出来，由实现决定具体策略（含 DV），并可在 executor 侧缓存。

## 小结

- **成效**：正式宣告 `Deletes.toPositionIndex` 两个重载将在 1.8.0 移除，引导调用方迁移到 `org.apache.iceberg.data.DeleteLoader` 接口。这是 Iceberg 删除加载路径重构的废弃通告步骤，本身不改变任何运行时行为。
- **影响范围**：仅修改 `core` 模块的 `Deletes.java` 一个文件、两个方法的注解和 javadoc，无逻辑改动。所有现有调用方仍可编译运行，但会收到 deprecation 警告。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯标记性提交，回迁风险极低。但需确认 1.4.x 分支上是否已合入 `DeleteLoader` 接口（`data/src/main/java/org/apache/iceberg/data/DeleteLoader.java` 及 `BaseDeleteLoader`）。若 1.4.x 尚未引入 `DeleteLoader`，则此 deprecation 会指向一个不存在的 API，应先回迁 `DeleteLoader` 相关提交，或调整 deprecation javadoc。
  2. 若 1.4.x 上 `Deletes.toPositionIndex` 仍被大量调用且短期内不会迁移到 `DeleteLoader`，回迁此 deprecation 主要是"信息性"的，提醒 1.4.x 用户该 API 未来会移除。
  3. 注意 deprecation 版本号：javadoc 写的是 "since 1.7.0, will be removed in 1.8.0"，这是 main 分支的版本节奏，回迁到 1.4.x 时版本号可能需要调整以匹配 1.4.x 的版本规划。
