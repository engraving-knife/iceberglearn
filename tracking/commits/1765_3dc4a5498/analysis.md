# 提交 1765：Core: Remove deprecated Util.blockLocations method and StructCopy class (#12320)

## 提交信息

- **序号**：1765 / 4088
- **哈希**：3dc4a5498ebbcf9bb1606752330d1de415db5cb0
- **短哈希**：3dc4a5498
- **日期**：2025-02-20 11:00:19 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Remove deprecated Util.blockLocations method and StructCopy class (#12320)
- **PR/Issue**：#12320

## 总体目的

本提交旨在移除 Core 模块中在 1.8.0 版本标记为废弃（deprecated）的 API，为 1.9.0 版本做清理准备。与提交 1762 类似，这是 Iceberg 项目遵循的废弃 API 在下一个主要版本中移除的实践。

具体移除的废弃 API 包括：

1. **`Util.blockLocations(CombinedScanTask, Configuration)`**：废弃的 `blockLocations` 方法，接受 `CombinedScanTask` 参数。该方法在 1.8.0 中被标记为废弃，建议使用接受 `ScanTaskGroup<FileScanTask>` 参数的新版本。`CombinedScanTask` 是旧接口，已被 `ScanTaskGroup` 替代。

2. **`StructCopy` 类**：废弃的工具类，实现了 `StructLike` 接口，用于复制 `StructLike` 对象的值。在 1.8.0 中被标记为废弃，建议使用 `StructLikeUtil.copy(StructLike)` 替代。

## 如何达成设计目的

提交通过以下步骤完成移除：

1. **删除 `Util.blockLocations(CombinedScanTask, Configuration)` 方法**：从 `Util.java` 中移除该废弃方法及其 Javadoc 和 `@Deprecated` 注解，同时移除不再需要的 `CombinedScanTask` import。保留新的 `blockLocations(ScanTaskGroup<FileScanTask>, Configuration)` 方法。

2. **删除 `StructCopy` 类**：完全删除 `StructCopy.java` 文件。该类是包私有（package-private）的，仅被标记为废弃，其功能已被 `StructLikeUtil.copy()` 替代。

3. **更新 RevAPI 配置**：在 `.palantir/revapi.yml` 中记录 `Util.blockLocations` 方法被移除这一破坏性 API 变更。

## 修改详情

### `.palantir/revapi.yml`（修改, +5/-0 lines）

**修改目的**：记录 1.8.0 版本中 `iceberg-core` 的 API 破坏性变更。

**工作逻辑**：在 `acceptedBreaks` 的 `1.8.0` 版本下新增 `iceberg-core` 条目，记录 `Util.blockLocations(CombinedScanTask, Configuration)` 方法被移除（`java.method.removed`），理由为"Removing deprecated code"。

### `core/src/main/java/org/apache/iceberg/hadoop/Util.java`（修改, +0/-10 lines）

**修改目的**：移除废弃的 `blockLocations(CombinedScanTask, Configuration)` 方法。

**工作逻辑**：
- 移除 `import org.apache.iceberg.CombinedScanTask;` 导入语句
- 移除带有 `@Deprecated` 注解和 Javadoc 的 `blockLocations(CombinedScanTask task, Configuration conf)` 方法。该方法仅是将 `CombinedScanTask` 强制转换为 `ScanTaskGroup<FileScanTask>` 并调用新版本的 `blockLocations` 方法。
- 保留 `blockLocations(ScanTaskGroup<FileScanTask> taskGroup, Configuration conf)` 方法不变。

### `core/src/main/java/org/apache/iceberg/io/StructCopy.java`（删除, -65 lines）

**修改目的**：完全删除废弃的 `StructCopy` 类。

**工作逻辑**：`StructCopy` 是一个实现了 `StructLike` 接口的包私有类，用于深度复制 `StructLike` 对象的值。它通过递归方式复制嵌套的 `StructLike` 值，但不处理 list 或 map 类型的值。该类在 1.8.0 中被标记为废弃，建议使用 `StructLikeUtil.copy(StructLike)` 替代。整个文件被删除。

`StructCopy` 类包含：
- 静态工厂方法 `copy(StructLike)`：创建 `StructLike` 的副本
- 私有构造器：递归复制所有字段值
- `size()`、`get()`、`set()` 方法实现 `StructLike` 接口
- `set()` 方法抛出 `UnsupportedOperationException`，因为副本不可修改

## 小结

- **成效**：移除了 Core 模块中两个在 1.8.0 标记废弃的 API（`Util.blockLocations(CombinedScanTask, Configuration)` 方法和 `StructCopy` 类），为 1.9.0 版本清理了废弃代码。
- **影响范围**：修改 Core 模块的 `Util` 类和删除 `StructCopy` 类。`StructCopy` 是包私有的，删除不影响外部 API；`Util.blockLocations` 是 public 方法，移除是破坏性 API 变更，但已有替代方法。
- **回迁到 1.4.x 的注意事项**：不建议回迁到 1.4.x 分支。与提交 1762 类似，这些 API 是在 1.8.0 中才标记为废弃的，1.4.x 分支中可能尚未标记废弃。直接移除会跳过废弃过渡期，对 API 使用者造成意外。仅在 1.4.x 分支已完成废弃周期的情况下才考虑回迁。
