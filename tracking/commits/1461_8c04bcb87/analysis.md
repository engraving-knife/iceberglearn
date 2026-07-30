# 提交 1461：Core: Generalize Util.blockLocations (#11053)

## 提交信息

- **序号**：1461 / 4088
- **哈希**：8c04bcb876d46e953ece5b2951c9bd2f361783df
- **短哈希**：8c04bcb87
- **日期**：2024-12-04（Thu Dec 5 00:41:10 2024 +0900）
- **作者**：Shohei Okumiya <git@okumin.com>
- **提交说明**：Core: Generalize Util.blockLocations (#11053)
- **PR/Issue**：#11053

## 总体目的

`Util.blockLocations` 是 Iceberg Hadoop 模块中用于获取扫描任务对应数据文件块位置（BlockLocation）的工具方法，主要服务于数据本地性调度（data locality）——让计算任务尽可能调度到数据所在节点，减少网络传输。该方法被 `IcebergSplit`（MR 模块）等调用。

原有方法签名为 `blockLocations(CombinedScanTask task, Configuration conf)`，仅接受 `CombinedScanTask` 类型。但 `CombinedScanTask` 只是 `ScanTaskGroup<FileScanTask>` 的一个特化子接口——随着 Iceberg 演进，出现了其他形式的 `ScanTaskGroup<FileScanTask>` 实现（如自定义的 task group、非 `CombinedScanTask` 的分组），这些场景下调用方无法直接使用此方法，只能先把 task group 强转为 `CombinedScanTask` 或自行实现块位置查询逻辑。

本提交把方法签名从 `CombinedScanTask` 泛化为 `ScanTaskGroup<FileScanTask>`，使任何文件扫描任务组都能直接使用该方法获取块位置。原 `CombinedScanTask` 重载标记为 `@Deprecated`（since 1.8.0, removed in 1.9.0），通过委托新方法保持向后兼容。

## 如何达成设计目的

通过方法重载实现平滑迁移：

1. 新增 `blockLocations(ScanTaskGroup<FileScanTask> taskGroup, Configuration conf)`，内部遍历 `taskGroup.tasks()`（而非 `task.files()`）获取文件扫描任务；
2. 原 `blockLocations(CombinedScanTask task, Configuration conf)` 标记 `@Deprecated`，方法体改为 `return blockLocations((ScanTaskGroup<FileScanTask>) task, conf);`——委托新方法；
3. 由于 `CombinedScanTask extends ScanTaskGroup<FileScanTask>`，旧调用方传入的 `CombinedScanTask` 可直接被新方法接受，行为不变。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/Util.java`（修改，+10/-1 行）

**修改目的**：泛化 `blockLocations` 方法参数类型，从 `CombinedScanTask` 扩展到 `ScanTaskGroup<FileScanTask>`。

**工作逻辑**：

修改后代码结构：

```java
/**
 * @deprecated since 1.8.0, will be removed in 1.9.0; use
 *     Util#blockLocations(ScanTaskGroup, Configuration) instead.
 */
@Deprecated
public static String[] blockLocations(CombinedScanTask task, Configuration conf) {
  return blockLocations((ScanTaskGroup<FileScanTask>) task, conf);
}

public static String[] blockLocations(ScanTaskGroup<FileScanTask> taskGroup, Configuration conf) {
  Set<String> locationSets = Sets.newHashSet();
  for (FileScanTask f : taskGroup.tasks()) {
    Path path = new Path(f.file().path().toString());
    try {
      FileSystem fs = path.getFileSystem(conf);
      for (BlockLocation b : fs.getFileBlockLocations(path, f.start(), f.length())) {
        locationSets.addAll(Arrays.asList(b.getHosts()));
      }
    } catch (IOException ioe) {
      LOG.warn("Failed to get block locations for path {}", path, ioe);
    }
  }
  return locationSets.toArray(new String[0]);
}
```

关键变化：
- **参数类型**：`CombinedScanTask` → `ScanTaskGroup<FileScanTask>`。`CombinedScanTask extends ScanTaskGroup<FileScanTask>`，因此新方法接受范围更广；
- **迭代方式**：`task.files()` → `taskGroup.tasks()`。`CombinedScanTask.files()` 返回 `Collection<FileScanTask>`，而 `ScanTaskGroup.tasks()` 也返回 `Collection<FileScanTask>`（`CombinedScanTask` 的 `tasks()` 默认实现委托给 `files()`），语义等价；
- **旧方法委托**：`CombinedScanTask` 重载直接 cast 并调用新方法，行为完全保持一致；
- **废弃标注**：旧方法标注 `@Deprecated`，Javadoc 指引使用新方法，废弃计划为 1.8.0 起、1.9.0 移除。

注意：同文件中已存在另一个更通用的重载 `blockLocations(FileIO io, ScanTaskGroup<?> taskGroup)`，它接受任意 `ScanTaskGroup<?>`（不限于 `FileScanTask`）且用 `FileIO` 而非 `Configuration`。本提交新增的方法与该重载互补——保留 `Configuration`-based 的块位置查询路径（直接用 Hadoop `FileSystem.getFileBlockLocations`），同时泛化 task 类型。

## 小结

- **成效**：`Util.blockLocations` 现在接受任意 `ScanTaskGroup<FileScanTask>`，不再限于 `CombinedScanTask`，使非 `CombinedScanTask` 的文件扫描任务组也能直接获取块位置用于数据本地性调度。旧 API 保持向后兼容，废弃窗口为 1.8.0 → 1.9.0。
- **影响范围**：仅 `core` 模块的 `Util.java`，+10/-1 行。无破坏性变更：旧调用方（如 `IcebergSplit`）继续通过 `CombinedScanTask` 重载工作，编译期会触发 `@Deprecated` 警告但不影响运行。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。本提交是纯向后兼容的方法泛化——新增重载 + 旧方法委托。需确认 1.4.x 的 `ScanTaskGroup` 接口有 `tasks()` 方法（1.4.x 已具备）。回迁后，1.4.x 上若有调用方持 `ScanTaskGroup<FileScanTask>` 但非 `CombinedScanTask` 的场景，可直接使用新方法而无需强转。若 1.4.x 的 `CombinedScanTask` 接口结构与 main 一致（`extends ScanTaskGroup<FileScanTask>` + `files()` + `tasks()` 默认委托），迁移无风险。建议后续把 MR 模块的 `IcebergSplit` 调用迁移到新方法以消除废弃警告。
